package com.wornux.config;

import com.vaadin.flow.spring.security.NavigationAccessControlConfigurer;
import com.wornux.security.authorization.PermissionNavigationAccessChecker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class NavigationSecurityConfig {
    @Bean
    NavigationAccessControlConfigurer navigationAccessControlConfigurer(
            PermissionNavigationAccessChecker permissionNavigationAccessChecker) {
        return new NavigationAccessControlConfigurer().withNavigationAccessChecker(permissionNavigationAccessChecker);
    }
}
