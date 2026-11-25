package com.xhunting.extractors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Port của {@code rar_extractor.go}.
 *
 * Khác zip/7z: lệnh cấu hình có thể là {@code 7z.exe} hoặc {@code unrar}, và hai công cụ nhận
 * tham số khác nhau. Bản Go lặp lại {@code if strings.Contains(command, "7z.exe")} ở 3 chỗ;
 * bản Java gom vào một method {@link #buildArgs} để khỏi lặp.
 */
final class RarExtractor {

    boolean checkNeedPassword(String command, Path file, Path dest) {
        String out = CommandRunner.combinedOutput(command, buildArgs(command, file, dest, null));
        return out.contains("password") || out.contains("encrypted");
    }

    void extract(String command, Path file, Path dest, List<String> passwords) throws ExtractException {
        Path target = dest.resolve(FileUtils.fileNameWithoutExt(file));
        try {
            Files.createDirectories(target);
        } catch (IOException e) {
            throw new ExtractException("cannot create " + target, e);
        }

        if (!checkNeedPassword(command, file, target)) {
            CommandRunner.run(command, buildArgs(command, file, target, null));
        } else {
            boolean hasValidPassword = false;
            for (String pw : passwords) {
                try {
                    CommandRunner.run(command, buildArgs(command, file, target, pw));
                    hasValidPassword = true;
                    break;
                } catch (ExtractException ignored) {
                    // thử mật khẩu tiếp theo
                }
            }
            if (!hasValidPassword) {
                throw new PasswordRequiredException();
            }
        }

        ZipExtractor.normalize(target, dest);
    }

    /**
     * 7z:    7z x file [-pPW] -oDEST -y
     * unrar: unrar x file [-pPW] -o+ -y DEST
     * {@code password == null} nghĩa là không truyền mật khẩu (Go dùng nhánh code riêng).
     */
    private static String[] buildArgs(String command, Path file, Path dest, String password) {
        var args = new java.util.ArrayList<String>();
        args.add("x");
        args.add(file.toString());
        if (password != null) {
            args.add("-p" + password);
        }
        if (command.contains("7z.exe")) {
            args.add("-o" + dest);
            args.add("-y");
        } else {
            args.add("-o+");
            args.add("-y");
            args.add(dest.toString());
        }
        return args.toArray(String[]::new);
    }
}
