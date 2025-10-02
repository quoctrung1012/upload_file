package com.upload_file;

import com.upload_file.config.EnvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableAspectJAutoProxy
public class UploadFileApplication {

  public static void main(String[] args) {
    EnvLoader.loadEnv("dev"); // Gọi class riêng để load env
    SpringApplication.run(UploadFileApplication.class, args);
  }

}