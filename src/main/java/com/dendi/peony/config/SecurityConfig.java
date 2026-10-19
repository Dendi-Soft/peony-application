package com.dendi.peony.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the session-based MVP auth (issue #12).
 *
 * <p>Signup and signin are public; every other route requires a server-side session. CSRF is
 * disabled because the API is JSON-only and consumed by the mobile client, not browser forms.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  /**
   * Creates the BCrypt password encoder used to hash account passwords.
   *
   * @return BCrypt encoder
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * Creates the filter chain protecting the API routes.
   *
   * <p>Public: signup, signin, actuator health (Docker healthcheck), and the API documentation.
   * Everything else requires an authenticated session and gets a 401 JSON response when the session
   * is missing.
   *
   * @param http HTTP security DSL
   * @return the configured filter chain
   * @throws Exception when the filter chain cannot be built
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/auth/signup", "/api/auth/signin")
                    .permitAll()
                    .requestMatchers("/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            handler -> handler.authenticationEntryPoint(new UnauthorizedEntryPoint()));
    return http.build();
  }
}
