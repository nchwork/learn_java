package com.xhunting.extractors;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Port của hàm {@code detectArchiveExtByContent} trong {@code default_service.go}.
 *
 * Bản Go làm 2 bước: (1) {@code http.DetectContentType(head)} để đoán MIME, (2) kiểm tra
 * magic bytes, (3) không đoán được thì lấy đuôi file.
 *
 * Khác biệt Go -> Java:
 * <ul>
 *   <li>JDK không có {@code http.DetectContentType}. {@code Files.probeContentType} có tồn tại
 *       nhưng phụ thuộc hệ điều hành và thường chỉ nhìn đuôi file. Thực tế với các định dạng này,
 *       DetectContentType của Go cũng chỉ so magic bytes (zip "PK\x03\x04", rar "Rar!\x1A\x07",
 *       gzip "\x1F\x8B"), nên bản Java chỉ giữ bước (2) và (3), kết quả tương đương.</li>
 *   <li>{@code bytes.HasPrefix} -> tự viết {@link #startsWith}; {@code bytes.Equal(head[257:262], ...)}
 *       -> {@code Arrays.equals(a, from, to, b, from, to)}. Java không có slice "view" như Go,
 *       mảng con phải chỉ định bằng chỉ số.</li>
 *   <li>Byte trong Java là có dấu (-128..127), Go {@code byte} là uint8. Nên {@code 0x8B} phải
 *       ép kiểu {@code (byte) 0x8B} khi so sánh.</li>
 * </ul>
 */
final class ArchiveDetector {

    private static final byte[] RAR_MAGIC = {0x52, 0x61, 0x72, 0x21}; // "Rar!"
    private static final byte[] SEVEN_ZIP_MAGIC = {0x37, 0x7A, (byte) 0xBC, (byte) 0xAF};
    private static final byte[] USTAR = "ustar".getBytes(StandardCharsets.US_ASCII);

    private ArchiveDetector() {
    }

    /**
     * Trả về đuôi file (".zip", ".7z", ...) giống bản Go, để {@link DefaultExtractorService}
     * tra sang {@link ArchiveFormat}.
     */
    static String detectArchiveExtByContent(Path path) {
        byte[] head;
        // try-with-resources thay cho: f, err := os.Open(path); defer f.Close()
        try (InputStream in = Files.newInputStream(path)) {
            head = in.readNBytes(512); // đọc tối đa 512 byte như bản Go
        } catch (IOException e) {
            return fallbackExt(path); // Go: if err != nil { return fallbackExt() }
        }

        if (head.length >= 4 && head[0] == 0x50 && head[1] == 0x4B) { // "PK" -> zip
            return ".zip";
        }
        if (startsWith(head, RAR_MAGIC)) {
            return ".rar";
        }
        if (startsWith(head, SEVEN_ZIP_MAGIC)) {
            return ".7z";
        }
        if (head.length >= 2 && head[0] == 0x1F && head[1] == (byte) 0x8B) { // gzip
            // Giống bản Go: luôn trả ".gz", không cố phân biệt ".tar.gz".
            return ".gz";
        }
        if (head.length >= 262 && Arrays.equals(head, 257, 262, USTAR, 0, USTAR.length)) {
            return ".tar";
        }
        return fallbackExt(path);
    }

    /** Go dùng closure {@code fallbackExt := func() string {...}}; Java tách thành private method. */
    private static String fallbackExt(Path path) {
        return FileUtils.ext(path).toLowerCase();
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length
                && Arrays.equals(data, 0, prefix.length, prefix, 0, prefix.length);
    }
}
