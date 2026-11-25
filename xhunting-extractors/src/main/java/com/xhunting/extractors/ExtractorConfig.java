package com.xhunting.extractors;

/**
 * Port của:
 * <pre>{@code
 * type ExtractorConfig struct {
 *     Logger loggers.Logger `mapstructure:"logger"`
 * }
 * }</pre>
 *
 * Dùng {@link System.Logger} có sẵn trong JDK (Java 9+) thay cho logger của framework nội bộ.
 * Khi lên Spring sẽ dùng SLF4J ({@code LoggerFactory.getLogger(...)}).
 */
public record ExtractorConfig(System.Logger logger) {

    public static ExtractorConfig defaults() {
        return new ExtractorConfig(System.getLogger("xhunting.extractors"));
    }
}
