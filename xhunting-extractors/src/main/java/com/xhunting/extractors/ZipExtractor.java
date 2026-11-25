package com.xhunting.extractors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Port của {@code zip_extractor.go}.
 *
 * Trong Go, {@code ExtractZip} là method của {@code *DefaultExtractorService} nhưng nằm ở file
 * riêng: Go cho phép rải method của một type ra nhiều file cùng package. Java KHÔNG cho phép:
 * một class phải nằm trọn trong một file. Để giữ bố cục "mỗi định dạng một file" cho dễ so sánh,
 * mỗi định dạng thành một class nhỏ, package-private, và {@link DefaultExtractorService} gọi nó.
 */
final class ZipExtractor {

    /**
     * {@code checkNeedPasswordZip}: chạy thử 7z không mật khẩu và đọc output.
     * (Bản Go thực sự giải nén một lần chỉ để kiểm tra; giữ nguyên hành vi.)
     */
    boolean checkNeedPassword(String command, Path file, Path dest) {
        String out = CommandRunner.combinedOutput(command, "x", file.toString(), "-o" + dest, "-y");
        return out.contains("password") || out.contains("encrypted");
    }

    void extract(String command, Path file, Path dest, List<String> passwords) throws ExtractException {
        Path originalDest = dest;
        // Go: dest = filepath.Join(dest, FileNameWithoutExt(file)). Java: tham số không nên gán lại,
        // nên tạo biến mới cho rõ nghĩa.
        Path target = dest.resolve(FileUtils.fileNameWithoutExt(file));

        try {
            Files.createDirectories(target); // os.MkdirAll(dest, 0755)
        } catch (IOException e) {
            throw new ExtractException("cannot create " + target, e);
        }

        if (!checkNeedPassword(command, file, target)) {
            CommandRunner.run(command, "x", file.toString(), "-o" + target, "-y");
        } else {
            boolean hasValidPassword = false;
            for (String pw : passwords) { // for _, pw := range passwords
                try {
                    CommandRunner.run(command, "x", file.toString(), "-p" + pw, "-o" + target, "-y");
                    hasValidPassword = true;
                    break;
                } catch (ExtractException wrongPassword) {
                    // Go: if err == nil {...} -> bỏ qua lỗi, thử mật khẩu tiếp theo.
                }
            }
            if (!hasValidPassword) {
                throw new PasswordRequiredException();
            }
        }

        normalize(target, originalDest);
    }

    static void normalize(Path target, Path originalDest) throws ExtractException {
        try {
            FileUtils.normalizeExtractedFolder(target, originalDest);
        } catch (IOException e) {
            throw new ExtractException("normalize extracted folder failed: " + e.getMessage(), e);
        }
    }
}
