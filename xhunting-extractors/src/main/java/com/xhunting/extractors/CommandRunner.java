package com.xhunting.extractors;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Port của {@code golang_common.RunCmd} (utils.go) và các chỗ dùng {@code exec.Command(...).CombinedOutput()}.
 *
 * <pre>{@code
 * func RunCmd(name string, args ...string) error {
 *     cmd := exec.Command(name, args...)
 *     var stderr bytes.Buffer
 *     cmd.Stderr = &stderr
 *     err := cmd.Run()
 *     if err != nil {
 *         return fmt.Errorf("%s %v failed: %s", name, args, stderr.String())
 *     }
 *     return nil
 * }
 * }</pre>
 *
 * Khác biệt Go -> Java khi chạy process ngoài:
 * <ul>
 *   <li>{@code exec.Command} -> {@link ProcessBuilder}. {@code cmd.Run()} -> {@code start()} + {@code waitFor()}.</li>
 *   <li>Go: exit code khác 0 tự thành {@code *exec.ExitError}. Java: KHÔNG ném lỗi, phải tự đọc
 *       {@code waitFor()} rồi so với 0.</li>
 *   <li>Go: nếu không gán {@code cmd.Stdout}, output bị bỏ vào /dev/null. Java: output đi vào một
 *       pipe; nếu không đọc thì buffer pipe đầy và process con TREO. Vì vậy ta luôn
 *       {@code redirectOutput(DISCARD)} hoặc đọc hết stream.</li>
 *   <li>{@code CombinedOutput()} -> {@code redirectErrorStream(true)} rồi đọc {@code getInputStream()}.</li>
 *   <li>Varargs: Go {@code args ...string} ~ Java {@code String... args} (bên trong đều là mảng/slice).</li>
 * </ul>
 */
final class CommandRunner {

    private CommandRunner() {
        // Lớp tiện ích chỉ có static method: chặn tạo instance (Go không cần vì có hàm cấp package).
    }

    /** Tương đương {@code RunCmd}: chạy lệnh, ném lỗi kèm stderr nếu exit code != 0. */
    static void run(String name, String... args) throws ExtractException {
        List<String> command = toCommand(name, args);
        ProcessBuilder pb = new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD); // bỏ stdout, giống Go khi không gán cmd.Stdout
        try {
            Process process = pb.start();
            String stderr = readAll(process.getErrorStream());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new ExtractException("%s %s failed: %s".formatted(name, List.of(args), stderr));
            }
        } catch (IOException e) {
            // Go: exec.Command trả lỗi "executable file not found" ở cmd.Run(). Java ném IOException ở start().
            throw new ExtractException("%s %s failed: %s".formatted(name, List.of(args), e.getMessage()), e);
        } catch (InterruptedException e) {
            // Quy ước Java: khi bắt InterruptedException phải khôi phục cờ interrupt cho thread.
            // Go không có khái niệm này; thay vào đó dùng context.Context để hủy.
            Thread.currentThread().interrupt();
            throw new ExtractException("interrupted while running " + name, e);
        }
    }

    /**
     * Tương đương {@code out, _ := cmd.CombinedOutput()}: lấy cả stdout + stderr, bỏ qua lỗi.
     * Go cho phép bỏ lỗi bằng {@code _}; Java phải catch rõ ràng rồi mới bỏ qua được.
     */
    static String combinedOutput(String name, String... args) {
        ProcessBuilder pb = new ProcessBuilder(toCommand(name, args))
                .redirectErrorStream(true);
        try {
            Process process = pb.start();
            // 7z hỏi mật khẩu qua stdin. Đóng stdin để nó nhận EOF và thoát thay vì chờ mãi.
            process.getOutputStream().close();
            String out = readAll(process.getInputStream());
            process.waitFor();
            return out;
        } catch (IOException e) {
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    private static List<String> toCommand(String name, String... args) {
        List<String> command = new ArrayList<>(args.length + 1);
        command.add(name);
        command.addAll(List.of(args));
        return command;
    }

    private static String readAll(InputStream in) throws IOException {
        // try-with-resources ~ defer in.Close()
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
