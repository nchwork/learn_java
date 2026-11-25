package com.xhunting.extractors;

/**
 * Lỗi chung khi giải nén, thay cho giá trị {@code error} mà các hàm Go trả về.
 *
 * Khác biệt Go -> Java:
 * <ul>
 *   <li>Go: {@code errorx.Wrap(err, "msg")} / {@code fmt.Errorf("...: %w", err)}.
 *       Java: {@code new ExtractException("msg", cause)} — tham số {@code cause} chính là "wrap",
 *       và {@code getCause()} tương đương {@code errors.Unwrap}.</li>
 *   <li>Go so lỗi bằng {@code errors.Is/As}. Java bắt theo kiểu: {@code catch (PasswordRequiredException e)}.</li>
 *   <li>Extends {@link Exception} (checked) chứ không phải {@link RuntimeException}: compiler
 *       ép người gọi phải catch hoặc khai báo throws, giống việc Go không cho bỏ qua giá trị
 *       trả về một cách vô tình (dù Go vẫn cho {@code _ = err}).</li>
 * </ul>
 */
public class ExtractException extends Exception {

    public ExtractException(String message) {
        super(message);
    }

    public ExtractException(String message, Throwable cause) {
        super(message, cause);
    }
}
