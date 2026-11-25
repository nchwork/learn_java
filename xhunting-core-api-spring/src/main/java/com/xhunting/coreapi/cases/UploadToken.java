package com.xhunting.coreapi.cases;

/**
 * {@code message UploadToken { folder_path, case_id, expired_at }}.
 *
 * Dữ liệu bất biến -> {@code record}. Jackson 2.12+ đọc/ghi record trực tiếp
 * (bản Go dùng json.Marshal/Unmarshal trên struct proto).
 */
public record UploadToken(String folderPath, String caseId, long expiredAt) {
}
