package com.wornux.config;

import static com.vaadin.flow.spring.security.VaadinSecurityConfigurer.vaadin;

import com.wornux.ui.auth.LoginView;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain vaadinSecurityFilterChain(HttpSecurity http) {

    http.authorizeHttpRequests(authorize -> {
      authorize
          .requestMatchers(
              "/styles/**",
              "/fonts/**",
              "/frontend/**",
              "/images/*.png",
              "/crow3-frames/**",
              "/icons/**",
              "/line-awesome/**")
          .permitAll();
    });

    http.authorizeHttpRequests(
        authorize -> authorize.requestMatchers("/share/**").anonymous());

    http.with(vaadin(), vaadinSecurity -> {
      vaadinSecurity.loginView(LoginView.class);
    });

    return http.build();
  }
}
