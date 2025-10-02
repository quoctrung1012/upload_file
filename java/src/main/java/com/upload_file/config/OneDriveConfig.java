package com.upload_file.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "onedrive")
public class OneDriveConfig {

  // Getters and Setters
  private String clientId;
  private String clientSecret;
  private String tenantId;
  private String redirectUri;
  private String scope;

  private Upload upload = new Upload();

  @Setter
  @Getter
  public static class Upload {
    private int chunkSize = 10485760; // 10MB
    private int retryCount = 3;
    private int timeout = 300000; // 5 minutes

  }
}