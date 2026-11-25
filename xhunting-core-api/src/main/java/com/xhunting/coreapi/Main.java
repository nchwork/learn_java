package com.xhunting.coreapi;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.xhunting.coreapi.auth.AuthGuard;
import com.xhunting.coreapi.auth.PermissionGuard;
import com.xhunting.coreapi.cases.Case;
import com.xhunting.coreapi.cases.CaseRepository;
import com.xhunting.coreapi.cases.InMemoryCaseRepository;
import com.xhunting.coreapi.cases.MongoCaseRepository;
import com.xhunting.coreapi.eventlogs.EventLogService;
import com.xhunting.coreapi.handler.CaseServiceHandler;
import com.xhunting.coreapi.http.CoreApiServer;
import com.xhunting.coreapi.tenants.InMemoryTenantService;
import com.xhunting.coreapi.users.InMemoryUserService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.bson.Document;

/**
 * Điểm vào chương trình ~ {@code main.go} của core-api.
 *
 * Bản Go: {@code main()} đọc config, dựng từng service bằng {@code NewXxx(...)} rồi truyền vào nhau,
 * cuối cùng {@code app.Start()} và chờ tín hiệu tắt. Ở đây làm y hệt bằng tay ("manual DI").
 * Khi lên Spring Boot, toàn bộ phần {@link #buildApp} biến mất: các class được đánh dấu
 * {@code @Service/@Repository/@Component} và Spring tự gọi constructor + nối dây.
 */
public class Main {

    private static final System.Logger LOG = System.getLogger("main");

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.fromEnv(System.getenv());
        App app = buildApp(config);
        app.start(config.port());

        // Chờ Ctrl+C. Go: signal.Notify(c, os.Interrupt); <-c
        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("shutting down...");
            app.stop();
            stopped.countDown();
        }));
        stopped.await();
    }

    /** Nối dây các dependency. Tách riêng để test dựng app giống hệt production. */
    public static App buildApp(AppConfig config) {
        ObjectMapper json = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        List<AutoCloseable> resources = new ArrayList<>();

        CaseRepository caseRepository;
        if (config.useMongo()) {
            // Go: client, err := mongo.Connect(ctx, options.Client().ApplyURI(uri))
            MongoClient client = MongoClients.create(config.mongoUri());
            resources.add(client); // MongoClient là AutoCloseable ~ defer client.Disconnect(ctx)
            var db = client.getDatabase(config.mongoDatabase());
            db.runCommand(new Document("ping", 1)); // fail sớm nếu không kết nối được Mongo
            var mongoRepo = new MongoCaseRepository(db);
            if (config.seedDemo() && mongoRepo.isEmpty()) {
                seed(mongoRepo);
            }
            caseRepository = mongoRepo;
            LOG.log(System.Logger.Level.INFO, "storage: MongoDB {0}/{1}", redact(config.mongoUri()), config.mongoDatabase());
        } else {
            caseRepository = new InMemoryCaseRepository();
            if (config.seedDemo()) {
                seed(caseRepository);
            }
            LOG.log(System.Logger.Level.INFO, "storage: in-memory (set MONGO_URI to use MongoDB)");
        }

        var userService = InMemoryUserService.withDemoUsers();
        var handler = new CaseServiceHandler(
                new AuthGuard(userService),
                new PermissionGuard(),
                caseRepository,
                userService,
                new InMemoryTenantService(),
                new EventLogService(),
                json,
                config.encryptKey());
        return new App(new CoreApiServer(handler, json), resources);
    }

    /** Ẩn mật khẩu trong URI khi ghi log: mongodb://user:pass@host -> mongodb://user:***@host */
    private static String redact(String uri) {
        return uri.replaceAll("(//[^:/@]+):[^@]+@", "$1:***@");
    }

    private static void seed(CaseRepository repo) {
        long now = Instant.now().toEpochMilli();
        Object[][] rows = {
                // id, tenant, name, priority, status, creator, assignees
                {"65f000000000000000000001", "tenant-a", "Ransomware host-01", "critical", "open", "u-hunter1", List.of("u-hunter2")},
                {"65f000000000000000000002", "tenant-a", "Phishing campaign Q4", "high", "in_progress", "u-hunter2", List.of()},
                {"65f000000000000000000003", "tenant-b", "Webshell DMZ", "medium", "verified", "u-admin", List.of()},
        };
        for (int i = 0; i < rows.length; i++) {
            Object[] r = rows[i];
            Case c = new Case();
            c.setId((String) r[0]);
            c.setTenantId((String) r[1]);
            c.setName((String) r[2]);
            c.setPriority((String) r[3]);
            c.setStatus((String) r[4]);
            c.setCreatorId((String) r[5]);
            @SuppressWarnings("unchecked") // ép kiểu Object -> List<String>: generics bị xóa lúc runtime (type erasure)
            List<String> assignees = (List<String>) r[6];
            c.setAssigneeIds(assignees);
            c.setFilenames(List.of("logs_" + (i + 1) + ".zip"));
            c.setNumberOfFiles(1);
            c.setFolderPath("/" + r[1] + "/" + r[0]);
            c.setCreatedTime(now - (rows.length - i) * 60_000L);
            c.setUpdatedTime(c.getCreatedTime());
            repo.create(c);
        }
    }
}
