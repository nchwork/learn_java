package com.xhunting.extractors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Port các hàm trong {@code golang-common/utils.go} mà package extractors dùng:
 * {@code FileNameWithoutExt}, {@code RemoveExt}, {@code RemovePathExt},
 * {@code NormalizeExtractedFolder}, {@code firstSubDir}.
 *
 * Khác biệt Go -> Java:
 * <ul>
 *   <li>Go đặt hàm ở cấp package ({@code golang_common.RemoveExt(...)}). Java bắt buộc mọi
 *       hàm nằm trong class, nên gom thành {@code static} method của một lớp tiện ích.</li>
 *   <li>{@code path/filepath} + {@code os} -> {@code java.nio.file.Path} + {@code Files}.</li>
 *   <li>{@code os.RemoveAll} không có sẵn trong JDK: {@code Files.delete} chỉ xóa file hoặc thư mục
 *       rỗng, nên ta tự viết {@link #deleteRecursively(Path)}.</li>
 *   <li>Go chữ hoa = public, chữ thường = private trong package. Java dùng từ khóa:
 *       không ghi gì = package-private (gần nhất với chữ thường của Go).</li>
 * </ul>
 */
final class FileUtils {

    private FileUtils() {
    }

    /**
     * Go {@code filepath.Ext}: phần từ dấu chấm cuối cùng của tên file (".gz" với "a.tar.gz"),
     * chuỗi rỗng nếu không có. JDK không có sẵn hàm này, nên tự viết.
     */
    static String ext(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot);
    }

    /** {@code FileNameWithoutExt}: "/a/b/logs.zip" -> "logs". */
    static String fileNameWithoutExt(Path path) {
        String name = path.getFileName().toString();
        return name.substring(0, name.length() - ext(path).length());
    }

    /** {@code RemoveExt}: "/a/b/logs.zip" -> "/a/b/logs". */
    static Path removeExt(Path path) {
        String s = path.toString();
        return Path.of(s.substring(0, s.length() - ext(path).length()));
    }

    /** {@code RemovePathExt}: giống removeExt nhưng xử lý riêng ".tar.gz" -> bỏ cả hai đuôi. */
    static Path removePathExt(Path path) {
        String s = path.toString();
        if (path.getFileName().toString().endsWith(".tar.gz")) {
            return Path.of(s.substring(0, s.length() - ".tar.gz".length()));
        }
        return removeExt(path);
    }

    /**
     * Port của {@code NormalizeExtractedFolder(dest, originalDest)}.
     *
     * Ý nghĩa: archive thường được nén kèm một thư mục gốc, ví dụ logs.zip chứa {@code logs_host01/...}.
     * Sau khi giải nén vào {@code dest/logs/} ta có {@code dest/logs/logs_host01/...}. Hàm này
     * "nâng" thư mục con đầu tiên lên thay cho {@code dest/logs}, để còn {@code dest/logs/...}.
     */
    static void normalizeExtractedFolder(Path dest, Path originalDest) throws IOException {
        Path subDir = firstSubDir(dest);

        Path tmpPath = originalDest.resolve("__tmp__");
        deleteRecursively(tmpPath); // _ = os.RemoveAll(tmpPath)
        Files.createDirectories(tmpPath); // os.MkdirAll(tmpPath, 0755)

        Path moved = tmpPath.resolve(subDir.getFileName());
        deleteRecursively(moved);

        Files.move(subDir, moved); // os.Rename
        deleteRecursively(dest);
        Files.move(moved, dest);
        deleteRecursively(tmpPath);
    }

    /**
     * Port của {@code firstSubDir}. Go trả ("", error) khi không có; Java ném IOException.
     *
     * Lưu ý: {@code os.ReadDir} của Go trả kết quả ĐÃ SẮP XẾP theo tên. {@code Files.list} thì
     * không đảm bảo thứ tự, nên phải {@code sorted()} để giữ đúng hành vi.
     */
    static Path firstSubDir(Path dir) throws IOException {
        try (Stream<Path> entries = Files.list(dir)) { // Stream giữ file handle -> phải đóng
            return entries
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .filter(Files::isDirectory)
                    .findFirst()
                    .orElseThrow(() -> new IOException("no sub directory found in " + dir));
        }
    }

    /** Tương đương {@code os.RemoveAll}: xóa đệ quy, không lỗi nếu path không tồn tại. */
    static void deleteRecursively(Path path) throws IOException {
        if (Files.notExists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            // Xóa con trước, cha sau -> sắp xếp ngược.
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }
}
