package com.xhunting.coreapi;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MongoDBContainer;

/**
 * Bật MongoDB 7 trong Docker cho test. {@code @ServiceConnection}: Spring Boot tự lấy địa chỉ container
 * và đặt vào {@code spring.data.mongodb.uri}, không cần cấu hình gì thêm.
 * Go tương đương: testcontainers-go, hoặc tự {@code docker run} trong TestMain.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfig {

    @Bean
    @ServiceConnection
    MongoDBContainer mongoDbContainer() {
        return new MongoDBContainer("mongo:7");
    }
}
