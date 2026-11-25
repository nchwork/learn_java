package com.xhunting.extractors;

/**
 * Tương đương {@code errorx.New(common_constants.PasswordRequired)} với
 * {@code PasswordRequired = "Password required"}.
 *
 * Trong Go, phía gọi muốn phân biệt lỗi này thường phải so chuỗi message hoặc dùng sentinel
 * error + {@code errors.Is}. Trong Java, tạo một subclass riêng rồi
 * {@code catch (PasswordRequiredException e)} là cách tự nhiên nhất.
 */
public class PasswordRequiredException extends ExtractException {

    public static final String MESSAGE = "Password required";

    public PasswordRequiredException() {
        super(MESSAGE);
    }
}
