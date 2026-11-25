package com.xhunting.coreapi.auth;

import com.xhunting.coreapi.common.ApiException;
import com.xhunting.coreapi.users.UserService;
import org.springframework.stereotype.Component;

/**
 * Đọc {@code Authorization: Bearer <token>} và tra user demo, giống bản core.
 * Ở dự án Spring thật, phần này là Spring Security ({@code SecurityFilterChain} +
 * {@code oauth2ResourceServer().jwt()}) — chủ đề của giai đoạn 3 trong lộ trình.
 */
@Component
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
