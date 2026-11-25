package com.xhunting.extractors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Port của {@code sevenzip_extractor.go}. Logic giống ZipExtractor, chỉ khác cách nhận biết cần mật khẩu. */
final class SevenZipExtractor {

    /** {@code checkNeedPasswordSevenZip}: 7z in "Enter password" khi archive có mật khẩu. */
    boolean checkNeedPassword(String command, Path file, Path dest) {
        String out = CommandRunner.combinedOutput(command, "x", file.toString(), "-o" + dest, "-y");
        return out.contains("Enter password");
    }

    void extract(String command, Path file, Path dest, List<String> passwords) throws ExtractException {
        Path target = dest.resolve(FileUtils.fileNameWithoutExt(file));
        try {
            Files.createDirectories(target);
        } catch (IOException e) {
            throw new ExtractException("cannot create " + target, e);
        }

        if (!checkNeedPassword(command, file, target)) {
            CommandRunner.run(command, "x", file.toString(), "-o" + target, "-y");
        } else {
            // Viết kiểu Java 8+: stream + anyMatch thay cho vòng for + cờ hasValidPassword.
            // anyMatch dừng ở phần tử đầu tiên trả true, tương đương "break".
            boolean hasValidPassword = passwords.stream()
                    .anyMatch(pw -> tryRun(command, "x", file.toString(), "-p" + pw, "-o" + target, "-y"));
            if (!hasValidPassword) {
                throw new PasswordRequiredException();
            }
        }

        ZipExtractor.normalize(target, dest);
    }

    /**
     * Lambda trong Java không được ném checked exception (Predicate.test không khai báo throws),
     * nên phải bọc lại thành boolean. Đây là điểm khó chịu quen thuộc khi trộn stream và checked exception.
     */
    private static boolean tryRun(String command, String... args) {
        try {
            CommandRunner.run(command, args);
            return true;
        } catch (ExtractException e) {
            return false;
        }
    }
}
