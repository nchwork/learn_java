package com.xhunting.coreapi.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cấu hình riêng của app, đọc từ khối {@code xhunting:} trong application.yml.
 * Thay cho {@code AppConfig.fromEnv} của bản core và struct + tag {@code mapstructure:"encrypt_key"} của Go.
 * Spring map "encrypt-key" / "ENCRYPT_KEY" / "encryptKey" về cùng một field (relaxed binding).
 * {@code @Validated}: sai cấu hình thì app không khởi động được (fail fast).
 */
@Validated
@ConfigurationProperties(prefix = "xhunting")
public record AppProperties(
        @NotBlank String encryptKey,
        boolean seedDemoData) {
}
