package com.xhunting.extractors;

/**
 * Port của {@code extractors/model.go}.
 *
 * <pre>{@code
 * type CommandArchive struct {
 *     CommandZip      string `mapstructure:"command_zip"`
 *     CommandSevenZip string `mapstructure:"command_seven_zip"`
 *     ...
 * }
 * }</pre>
 *
 * Khác biệt Go -> Java:
 * <ul>
 *   <li>Struct chỉ chứa dữ liệu -> {@code record} (Java 16+). Record tự sinh constructor,
 *       accessor {@code commandZip()}, {@code equals/hashCode/toString} và mọi field là final
 *       (bất biến), trong khi struct Go thì sửa field thoải mái.</li>
 *   <li>Struct tag {@code mapstructure:"..."} không có trong Java core. Ở Spring, việc này
 *       do {@code @ConfigurationProperties} lo (map command-zip / command_zip -> commandZip).</li>
 *   <li>Go có "zero value" ({@code ""}). Java String mặc định là {@code null}, nên ta tự
 *       kiểm tra trong compact constructor.</li>
 * </ul>
 */
public record CommandArchive(
        String commandZip,
        String commandSevenZip,
        String commandRar,
        String commandTar,
        String commandGz) {

    /** Compact constructor: chạy trước khi gán field, dùng để validate. */
    public CommandArchive {
        requireNonBlank(commandZip, "commandZip");
        requireNonBlank(commandSevenZip, "commandSevenZip");
        requireNonBlank(commandRar, "commandRar");
        requireNonBlank(commandTar, "commandTar");
        requireNonBlank(commandGz, "commandGz");
    }

    /** Thực tế xhunting cấu hình cả 5 lệnh trỏ tới cùng một binary 7z. Đây là helper cho trường hợp đó. */
    public static CommandArchive allUsing(String command) {
        return new CommandArchive(command, command, command, command, command);
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
