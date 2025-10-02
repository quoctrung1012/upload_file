package com.upload_file.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;

  @Value("${cors.allowed-origins}")
  private String[] allowedOrigins;

  @Value("${app.base-url}")
  private String iframeAllowedOrigins;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .headers(headers -> headers
            // Allow same origin for iframe display
            .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
            .contentTypeOptions(HeadersConfigurer.ContentTypeOptionsConfig::disable)
            .httpStrictTransportSecurity(hsts -> hsts
                .maxAgeInSeconds(31536000)
                .includeSubDomains(true)
                .preload(true))
            .referrerPolicy(referrer -> referrer
                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
            )
            // Add specific headers for file preview
            .addHeaderWriter((request, response) -> {
              String requestURI = request.getRequestURI();
              if (requestURI.startsWith("/files/preview")) {
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                response.setHeader("Pragma", "no-cache");
                response.setHeader("Expires", "0");
                // Allow embedding for preview from configured origins
                response.setHeader("X-Frame-Options", "SAMEORIGIN");
              }
            })
        )
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(
                "/files/preview/**",
                "/files/chunk/**",
                "/files/chunk/check",
                "/files/merge",
                "/api/auth/register",
                "/api/auth/login",
                "/api/auth/refresh",
                "/login",
                "/register",
                "/dashboard",
                "/css/**",
                "/js/**",
                "/images/**",
                "/favicon.ico",
                "/actuator/health"
            ).permitAll()
            .requestMatchers("/files/**").authenticated()
            .requestMatchers("/api/auth/validate", "/api/auth/logout").authenticated()
            .anyRequest().authenticated()
        )
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .exceptionHandling(exceptions -> exceptions
            .authenticationEntryPoint((request, response, authException) -> {
              response.setStatus(401);
              response.setContentType("application/json");
              response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
            })
            .accessDeniedHandler((request, response, accessDeniedException) -> {
              response.setStatus(403);
              response.setContentType("application/json");
              response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"Access denied\"}");
            })
        );
    return http.build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();

    // Use configurable origins from properties file
    List<String> allowedOriginsList = new ArrayList<>(Arrays.asList(allowedOrigins));

    // Add iframe allowed origins from configuration
    if (iframeAllowedOrigins != null && !iframeAllowedOrigins.isEmpty()) {
      // Split by comma if multiple origins are provided
      String[] iframeOrigins = iframeAllowedOrigins.split(",");
      for (String origin : iframeOrigins) {
        String trimmedOrigin = origin.trim();
        if (!allowedOriginsList.contains(trimmedOrigin)) {
          allowedOriginsList.add(trimmedOrigin);
        }
      }
    }

    configuration.setAllowedOriginPatterns(allowedOriginsList);

    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
    configuration.setAllowedHeaders(Arrays.asList("*"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);

    // Add specific headers for file operations
    configuration.setExposedHeaders(Arrays.asList(
        "Content-Disposition",
        "Content-Type",
        "Content-Length",
        "X-Frame-Options"
    ));

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

    // Apply CORS for all endpoints
    source.registerCorsConfiguration("/**", configuration);

    return source;
  }
}