package com.xhunting.coreapi;

import java.util.Map;

/**
 * Cấu hình đọc từ biến môi trường (bản Go đọc file yaml qua {@code configs.ConfigContainer} + mapstructure).
 *
 * @param port          PORT, mặc định 8080
 * @param encryptKey    ENCRYPT_KEY, khóa AES 16/24/32 byte để mã hóa upload token (Go: config.EncryptKey)
 * @param seedDemo      SEED_DEMO_DATA=true thì tạo vài case mẫu khi khởi động (chỉ khi DB đang trống)
 * @param mongoUri      MONGO_URI, ví dụ mongodb://localhost:27017. Để trống = lưu trong RAM
 * @param mongoDatabase MONGO_DATABASE, mặc định "xhunting"
 */
public record AppConfig(int port, String encryptKey, boolean seedDemo, String mongoUri, String mongoDatabase) {

    /** Khóa CHỈ dùng cho local. Môi trường thật phải đặt ENCRYPT_KEY riêng. */
    static final String DEV_ENCRYPT_KEY = "local-dev-key-32-bytes-long!!!!!";

    public static AppConfig fromEnv(Map<String, String> env) {
        return new AppConfig(
                Integer.parseInt(env.getOrDefault("PORT", "8080")),
                env.getOrDefault("ENCRYPT_KEY", DEV_ENCRYPT_KEY),
                Boolean.parseBoolean(env.getOrDefault("SEED_DEMO_DATA", "true")),
                env.getOrDefault("MONGO_URI", ""),
                env.getOrDefault("MONGO_DATABASE", "xhunting"));
    }

    public boolean useMongo() {
        return mongoUri != null && !mongoUri.isBlank();
    }
}
