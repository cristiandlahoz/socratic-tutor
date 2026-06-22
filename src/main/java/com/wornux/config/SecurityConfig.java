package com.wornux.config;

import static com.vaadin.flow.spring.security.VaadinSecurityConfigurer.vaadin;

import com.wornux.ui.auth.LoginView;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain vaadinSecurityFilterChain(
      HttpSecurity http,
      Environment environment,
      @Value("${app.security.disable-for-local-development:false}") boolean disableSecurity) throws Exception {

    boolean isProduction = Arrays.asList(environment.getActiveProfiles()).contains("prod");

    if (isProduction && disableSecurity) {
      throw new IllegalStateException(
          "Refusing to start production with app.security.disable-for-local-development=true");
    }

    if (disableSecurity) {
      http.with(vaadin(), vaadinSecurity -> {
        vaadinSecurity.enableNavigationAccessControl(false);
        vaadinSecurity.anyRequest(any -> any.permitAll());
      });

      return http.build();
    }

    http.authorizeHttpRequests(
      authorize -> authorize
          .requestMatchers(
            "/styles/**",
            "/fonts/**",
            "/frontend/**",
            "/images/*.png",
            "/crow3-frames/**",
            "/icons/**",
            "/line-awesome/**",
            "/invitations/accept/**")
          .permitAll()
          .requestMatchers("/share/**")
          .anonymous());

    http.with(vaadin(), vaadinSecurity -> {
      vaadinSecurity.loginView(LoginView.class);
    });

    return http.build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

}
