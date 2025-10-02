package com.upload_file.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Setter
@Getter
@Configuration
@EnableScheduling
@ConfigurationProperties(prefix = "libreoffice")
public class LibreOfficeConfig {

  // Getters and Setters
  private String path = "C:/Program Files/LibreOffice/program";
  private long processTimeout = 60000L;
  private long processRetryInterval = 250L;
  private int maxTasksPerProcess = 200;
  private boolean autoRestart = true;

}