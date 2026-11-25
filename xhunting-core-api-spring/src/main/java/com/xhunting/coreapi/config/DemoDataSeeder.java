package com.xhunting.coreapi.config;

import com.xhunting.coreapi.cases.Case;
import com.xhunting.coreapi.cases.CaseRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Tạo 3 case mẫu khi khởi động nếu collection trống (giống {@code Main.seed} bản core).
 * {@code CommandLineRunner}: Spring gọi {@link #run} một lần sau khi app khởi động xong.
 * {@code @ConditionalOnProperty}: bean chỉ được tạo khi {@code xhunting.seed-demo-data=true}.
 */
@Component
@ConditionalOnProperty(prefix = "xhunting", name = "seed-demo-data", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private final CaseRepository repository;

    public DemoDataSeeder(CaseRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }
        long now = Instant.now().toEpochMilli();
        repository.insert(List.of(
                demo("65f000000000000000000001", "tenant-a", "Ransomware host-01", "critical", "open",
                        "u-hunter1", List.of("u-hunter2"), "logs_1.zip", now - 180_000),
                demo("65f000000000000000000002", "tenant-a", "Phishing campaign Q4", "high", "in_progress",
                        "u-hunter2", List.of(), "logs_2.zip", now - 120_000),
                demo("65f000000000000000000003", "tenant-b", "Webshell DMZ", "medium", "verified",
                        "u-admin", List.of(), "logs_3.zip", now - 60_000)));
        log.info("seeded 3 demo cases");
    }

    private static Case demo(String id, String tenant, String name, String priority, String status,
                             String creator, List<String> assignees, String file, long time) {
        Case c = new Case();
        c.setId(id);
        c.setTenantId(tenant);
        c.setName(name);
        c.setPriority(priority);
        c.setStatus(status);
        c.setCreatorId(creator);
        c.setAssigneeIds(assignees);
        c.setFilenames(List.of(file));
        c.setNumberOfFiles(1);
        c.setFolderPath("/" + tenant + "/" + id);
        c.setCreatedTime(time);
        c.setUpdatedTime(time);
        return c;
    }
}
