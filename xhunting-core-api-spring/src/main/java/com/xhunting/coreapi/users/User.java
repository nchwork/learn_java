package com.xhunting.coreapi.users;

import java.util.Set;

/**
 * Tài khoản đầy đủ (bản Go lấy từ identity service qua gRPC: {@code users.UserService}).
 *
 * @param tenantIds  tenant user được truy cập; "MASTER" = mọi tenant
 * @param accessToken token demo để gọi API (thay cho JWT thật do authGuard kiểm tra)
 */
public record User(
        String id,
        String name,
        String displayName,
        String email,
        Set<String> roles,
        Set<String> tenantIds,
        boolean disabled,
        String accessToken) {

    public UserInfo toInfo() {
        return new UserInfo(id, name, displayName, email);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
