package com.xhunting.coreapi.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Cho phép controller khai báo tham số {@code UserContext ctx} và nhận sẵn user đã xác thực.
 *
 * Go: mỗi handler tự gọi {@code inst.getUserContext(ctx, request.Ctx, action)}; grpc-gateway đưa header
 * vào metadata. Spring: một "argument resolver" chạy trước controller, đọc header Authorization và
 * {@code {tenantId}} trên URL, gọi {@link AuthGuard}. Kiểm tra quyền theo action vẫn ở service
 * (vì action khác nhau theo từng API), giống bản Go.
 */
@Component
public class UserContextArgumentResolver implements HandlerMethodArgumentResolver {

    private final AuthGuard authGuard;

    public UserContextArgumentResolver(AuthGuard authGuard) {
        this.authGuard = authGuard;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(UserContext.class);
    }

    @Override
    public UserContext resolveArgument(MethodParameter parameter, ModelAndViewContainer mav,
                                       NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        @SuppressWarnings("unchecked")
        Map<String, String> pathVars = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String tenantId = pathVars == null ? null : pathVars.get("tenantId");
        return authGuard.canActivate(request.getHeader("Authorization"), tenantId);
    }
}
