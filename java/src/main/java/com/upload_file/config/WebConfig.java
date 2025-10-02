package com.upload_file.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Value("${cors.allowed-origins}")
  private String[] allowedOrigins;

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")
        .allowedOriginPatterns(allowedOrigins)
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD")
        .allowedHeaders("*")
        .allowCredentials(true)
        .exposedHeaders("Content-Disposition", "Content-Type", "Content-Length", "X-Frame-Options")
        .maxAge(3600);

    // FIX: Add specific mapping for file operations
    registry.addMapping("/files/**")
        .allowedOriginPatterns("*")
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD")
        .allowedHeaders("*")
        .allowCredentials(true)
        .exposedHeaders("Content-Disposition", "Content-Type", "Content-Length", "X-Frame-Options")
        .maxAge(3600);
  }
}