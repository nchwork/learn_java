package com.xhunting.coreapi.auth;

import com.xhunting.coreapi.common.ApiException;
import com.xhunting.coreapi.common.Constants;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Thay cho {@code permissionGuard.CanActivate(userContext, "cases", action)}.
 *
 * Bản Go đọc policy từ identity service. Bản demo dùng bảng role -> action cố định:
 * <pre>
 * admin  : get, create, update, delete (mọi tenant)
 * hunter : get, create, update, delete (chỉ tenant của mình, và bị lọc theo case của mình)
 * viewer : get
 * </pre>
 */
@Component
public class PermissionGuard {

    private static final Map<String, Set<String>> ROLE_ACTIONS = Map.of(
            "admin", Set.of(Constants.ACTION_GET, Constants.ACTION_CREATE, Constants.ACTION_UPDATE, Constants.ACTION_DELETE),
            "hunter", Set.of(Constants.ACTION_GET, Constants.ACTION_CREATE, Constants.ACTION_UPDATE, Constants.ACTION_DELETE),
            "viewer", Set.of(Constants.ACTION_GET));

    public void canActivate(UserContext ctx, String resource, String action) {
        if (!hasPermission(ctx, action)) {
            throw ApiException.permissionDenied("permission denied: " + resource + ":" + action);
        }
    }

    /** Phiên bản trả boolean, dùng khi chỉ muốn kiểm tra (Go: {@code err == nil}). */
    public boolean hasPermission(UserContext ctx, String action) {
        boolean roleAllows = ctx.user().roles().stream()
                .anyMatch(role -> ROLE_ACTIONS.getOrDefault(role, Set.of()).contains(action));
        return roleAllows && canAccessTenant(ctx);
    }

    private static boolean canAccessTenant(UserContext ctx) {
        Set<String> tenants = ctx.user().tenantIds();
        if (tenants.contains(Constants.TENANT_MASTER)) {
            return true;
        }
        String requested = ctx.requestTenantId();
        return requested != null && tenants.contains(requested);
    }
}
