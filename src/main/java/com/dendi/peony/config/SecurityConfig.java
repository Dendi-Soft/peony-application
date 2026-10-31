package com.dendi.peony.config;

import com.dendi.peony.service.auth.PeonyUserDetailsService;
import com.dendi.peony.service.jwt.JwtTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for the JWT-based auth flow (spec #17).
 *
 * <p>Signup, signin, and refresh are public; every other route requires a valid bearer access
 * token. The flow is stateless: no HTTP session is created. CSRF is disabled because the API is
 * JSON-only and consumed by clients that never send browser-form requests.
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
   * Creates the authentication manager verifying account credentials against the stored BCrypt
   * hash.
   *
   * @param userDetailsService loads accounts by username or email
   * @param passwordEncoder encoder used to verify the password
   * @return manager delegating to the DAO provider
   */
  @Bean
  public AuthenticationManager authenticationManager(
      PeonyUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
  }

  /**
   * Creates the filter chain protecting the API routes.
   *
   * <p>Public: signup, signin, refresh, actuator health (Docker healthcheck), and the API
   * documentation. Everything else requires a bearer access token and gets a 401 JSON response when
   * it is missing or invalid.
   *
   * @param http HTTP security DSL
   * @param jwtTokenService verifies bearer access tokens
   * @param userDetailsService loads the account behind a token
   * @return the configured filter chain
   * @throws Exception when the filter chain cannot be built
   */
  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtTokenService jwtTokenService,
      PeonyUserDetailsService userDetailsService)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtTokenService, userDetailsService),
            UsernamePasswordAuthenticationFilter.class)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/v1/auth/signup", "/api/v1/auth/signin", "/api/v1/auth/refresh")
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
