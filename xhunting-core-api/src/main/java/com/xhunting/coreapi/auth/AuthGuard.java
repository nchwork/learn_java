package com.xhunting.coreapi.auth;

import com.xhunting.coreapi.common.ApiException;
import com.xhunting.coreapi.users.UserService;

/**
 * Thay cho {@code authGuard.CanActivate(ctx, reqCtx)}: đọc header {@code Authorization: Bearer <token>}.
 *
 * Bản thật kiểm tra JWT (chữ ký, hạn dùng). Ở đây token là chuỗi demo tra trong UserService,
 * đủ để luyện luồng phân quyền. Khi lên Spring: Spring Security + {@code oauth2ResourceServer().jwt()}.
 */
public class AuthGuard {

    private final UserService userService;

    public AuthGuard(UserService userService) {
        this.userService = userService;
    }

    public UserContext canActivate(String authorizationHeader, String requestTenantId) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw ApiException.unauthenticated("missing bearer token");
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        var user = userService.findByAccessToken(token)
                .filter(u -> !u.disabled())
                .orElseThrow(() -> ApiException.unauthenticated("invalid token"));
        return new UserContext(user, requestTenantId);
    }
}
