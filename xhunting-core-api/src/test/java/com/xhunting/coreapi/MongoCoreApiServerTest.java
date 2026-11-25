package com.xhunting.coreapi;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.mongodb.MongoClientSettings;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClients;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

/**
 * Chạy toàn bộ CoreApiServerTest trên MongoDB thật.
 * Mongo lấy từ biến MONGO_TEST_URI (mặc định mongodb://localhost:27017). Không kết nối được thì BỎ QUA
 * (giống {@code t.Skip("mongo not available")} trong Go), nên {@code mvn test} vẫn xanh khi chưa bật Mongo.
 * Mỗi test dùng một database riêng tên ngẫu nhiên và xóa nó khi xong.
 */
class MongoCoreApiServerTest extends CoreApiServerTest {

    private static final String URI = System.getenv().getOrDefault("MONGO_TEST_URI", "mongodb://localhost:27017");
    private final String database = "xhunting_test_" + UUID.randomUUID().toString().replace("-", "");

    @BeforeAll
    static void requireMongo() {
        var settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(URI))
                .applyToClusterSettings(b -> b.serverSelectionTimeout(1500, TimeUnit.MILLISECONDS))
                .build();
        boolean ok;
        try (var client = MongoClients.create(settings)) { // try-with-resources đóng client
            client.getDatabase("admin").runCommand(new Document("ping", 1));
            ok = true;
        } catch (RuntimeException e) {
            ok = false;
        }
        assumeTrue(ok, "MongoDB not available at " + URI);
    }

    @Override
    protected AppConfig config() {
        return new AppConfig(0, AppConfig.DEV_ENCRYPT_KEY, true, URI, database);
    }

    @AfterEach
    void dropDatabase() {
        try (var client = MongoClients.create(URI)) {
            client.getDatabase(database).drop();
        }
    }
}
