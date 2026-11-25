package com.xhunting.coreapi.common;

import java.util.List;

/**
 * Gom các hằng số dùng trong Case CRUD, lấy từ {@code golang-common/constants} và
 * {@code core-api/internal/constants}.
 *
 * Go: {@code const ( CaseStatusOpen = "open" ... )} ở cấp package.
 * Java: {@code public static final} trong một lớp không tạo instance được.
 * Ở đây giữ String (không dùng enum) vì giá trị đi thẳng ra/vào JSON giống bản Go.
 */
public final class Constants {

    private Constants() {
    }

    // Trạng thái case (constants.go: CaseStatusXxx)
    public static final String CASE_STATUS_OPEN = "open";
    public static final String CASE_STATUS_IN_PROGRESS = "in_progress";
    public static final String CASE_STATUS_PENDING = "pending";
    public static final String CASE_STATUS_CANCELLED = "cancelled";
    public static final String CASE_STATUS_VERIFIED = "verified";
    public static final String CASE_STATUS_UNVERIFIED = "unverified";

    public static final List<String> CASE_STATUSES = List.of(
            CASE_STATUS_OPEN, CASE_STATUS_IN_PROGRESS, CASE_STATUS_PENDING,
            CASE_STATUS_CANCELLED, CASE_STATUS_UNVERIFIED, CASE_STATUS_VERIFIED);

    public static final List<String> PRIORITIES = List.of("low", "medium", "high", "critical");

    // Action dùng cho permission guard (core-api/internal/constants: ActionXxx)
    public static final String ACTION_GET = "get";
    public static final String ACTION_CREATE = "create";
    public static final String ACTION_UPDATE = "update";
    public static final String ACTION_DELETE = "delete";

    // Tenant đặc biệt: MASTER được đổi thành ANY = không lọc theo tenant
    public static final String TENANT_MASTER = "MASTER";
    public static final String TENANT_ANY = "ANY";

    // isCompressedFile(): các đuôi file được chấp nhận
    public static final List<String> COMPRESSED_EXTENSIONS = List.of(".tar.gz", ".gz", ".tar", ".7z", ".rar", ".zip");
}
