package com.xhunting.coreapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Điểm vào ~ main.go. So sánh với {@code Main.buildApp} của bản Java core:
 * ở đó ta tự {@code new} từng service rồi truyền vào nhau (16 tham số như {@code NewCaseServiceHandler} của Go).
 * Ở đây {@code @SpringBootApplication} quét package {@code com.xhunting.coreapi}, tìm mọi class có
 * {@code @Service/@Component/@RestController/...}, tự gọi constructor và tiêm dependency (DI container).
 * MongoClient, ObjectMapper, Tomcat... được auto-configuration tạo sẵn từ application.yml.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CoreApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreApiApplication.class, args);
    }
}
