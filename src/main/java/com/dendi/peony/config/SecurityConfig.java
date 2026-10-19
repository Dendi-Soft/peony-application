package com.dendi.peony.config;

import com.dendi.peony.service.auth.PeonyUserDetailsService;
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
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

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
   * Creates the repository binding the security context to the HTTP session, so a signed-in user
   * stays authenticated across requests.
   *
   * @return session-backed security context repository
   */
  @Bean
  public SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
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
                auth.requestMatchers("/api/v1/auth/signup", "/api/v1/auth/signin")
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
