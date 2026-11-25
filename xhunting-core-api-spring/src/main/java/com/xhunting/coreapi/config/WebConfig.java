package com.xhunting.coreapi.config;

import com.xhunting.coreapi.auth.UserContextArgumentResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Đăng ký {@link UserContextArgumentResolver} vào Spring MVC. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final UserContextArgumentResolver userContextResolver;

    public WebConfig(UserContextArgumentResolver userContextResolver) {
        this.userContextResolver = userContextResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(userContextResolver);
    }
}
