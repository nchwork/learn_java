package com.xhunting.coreapi.common;

/**
 * Thay cho {@code status.Error(codes.X, "...")} của gRPC và các sentinel error như
 * {@code ErrCaseNotFound}, {@code ErrUploadTokenInvalid}.
 *
 * Bản Go chạy qua grpc-gateway: lỗi gRPC được đổi sang HTTP status
 * (NotFound -> 404, InvalidArgument -> 400, ...) và body {@code {"code":5,"message":"..."}}.
 * Bản Java giữ nguyên cả mã gRPC lẫn HTTP status để client cũ vẫn hiểu.
 *
 * Là {@link RuntimeException} (unchecked): lỗi nghiệp vụ bay thẳng lên tầng HTTP mà không cần
 * mọi method trung gian khai báo {@code throws}. Đây là cách Spring cũng làm
 * ({@code @ResponseStatus}, {@code @ControllerAdvice}).
 */
public class ApiException extends RuntimeException {

    /** Mã gRPC (google.golang.org/grpc/codes) và HTTP status tương ứng của grpc-gateway. */
    public enum Code {
        INVALID_ARGUMENT(3, 400),
        NOT_FOUND(5, 404),
        PERMISSION_DENIED(7, 403),
        UNAUTHENTICATED(16, 401),
        INTERNAL(13, 500);

        public final int grpcCode;
        public final int httpStatus;

        Code(int grpcCode, int httpStatus) {
            this.grpcCode = grpcCode;
            this.httpStatus = httpStatus;
        }
    }

    private final Code code;

    public ApiException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    // Tương đương các biến var ErrXxx = status.Error(...) trong Go.
    // Java tạo mới mỗi lần (factory method) vì exception mang stack trace, không nên dùng chung một instance.
    public static ApiException caseNotFound() {
        return new ApiException(Code.NOT_FOUND, "CaseNotFound");
    }

    public static ApiException uploadTokenInvalid() {
        return new ApiException(Code.INVALID_ARGUMENT, "UploadTokenInvalid");
    }

    public static ApiException invalidArgument(String message) {
        return new ApiException(Code.INVALID_ARGUMENT, message);
    }

    public static ApiException permissionDenied(String message) {
        return new ApiException(Code.PERMISSION_DENIED, message);
    }

    public static ApiException unauthenticated(String message) {
        return new ApiException(Code.UNAUTHENTICATED, message);
    }
}
