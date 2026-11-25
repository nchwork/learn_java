package com.xhunting.coreapi.auth;

import com.xhunting.coreapi.users.User;

/**
 * Tương đương {@code contexts.UserContext} của framework Go: ai đang gọi + tenant đang được yêu cầu.
 *
 * Bản Go có {@code userContext.Clone()} rồi {@code SetRequestTenantId(...)} (sửa tại chỗ).
 * Record bất biến nên ta dùng "wither": {@link #withRequestTenantId} trả object mới, object cũ
 * không đổi. Không cần Clone vì không ai sửa được nó.
 */
public record UserContext(User user, String requestTenantId) {

    public UserContext withRequestTenantId(String tenantId) {
        return new UserContext(user, tenantId);
    }

    /** {@code authx.HasHunterRole(userContext)}. */
    public boolean isHunter() {
        return user.hasRole("hunter");
    }

    public String userId() {
        return user.id();
    }
}
