package com.xhunting.extractors;

import java.util.Arrays;
import java.util.Optional;

/**
 * Thay cho các hằng số trong {@code constants/constants.go}:
 *
 * <pre>{@code
 * ExtensionZip      = ".zip"
 * ExtensionSevenZip = ".7z"
 * ExtensionRar      = ".rar"
 * ExtensionTarGz    = ".tar.gz"
 * ExtensionGz       = ".gz"
 * ExtensionTar      = ".tar"
 * }</pre>
 *
 * Khác biệt Go -> Java: Go không có enum thật, chỉ có const string/iota. Java {@code enum}
 * là một class đầy đủ: có field, method, và quan trọng nhất là {@code switch} trên enum
 * được compiler kiểm tra đủ case (exhaustive) khi dùng switch expression. Bản Go dùng
 * {@code switch ext} trên string nên gõ sai ".zipp" vẫn compile.
 */
public enum ArchiveFormat {
    ZIP(".zip"),
    SEVEN_ZIP(".7z"),
    RAR(".rar"),
    TAR_GZ(".tar.gz"),
    GZ(".gz"),
    TAR(".tar");

    private final String extension;

    ArchiveFormat(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }

    /**
     * Tra enum từ đuôi file (".zip" -> ZIP). Go sẽ trả về (value, ok bool);
     * Java dùng {@link Optional} để nói "có thể không có kết quả" thay vì trả null.
     */
    public static Optional<ArchiveFormat> fromExtension(String ext) {
        return Arrays.stream(values())
                .filter(f -> f.extension.equalsIgnoreCase(ext))
                .findFirst();
    }
}
