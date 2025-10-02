package com.upload_file.config;

import io.github.cdimascio.dotenv.Dotenv;

public class EnvLoader {

  public static void loadEnv(String defaultProfile) {
    String profile = System.getProperty("spring.profiles.active", defaultProfile);

    Dotenv dotenv = Dotenv.configure()
        .directory("env")
        .filename(".env." + profile)
        .ignoreIfMalformed()
        .ignoreIfMissing()
        .load();

    System.setProperty("SERVER_PORT", dotenv.get("SERVER_PORT"));
    System.setProperty("APP_URL", dotenv.get("APP_URL"));
    System.setProperty("AZURE_CLIENT_ID", dotenv.get("AZURE_CLIENT_ID"));
    System.setProperty("AZURE_CLIENT_SECRET", dotenv.get("AZURE_CLIENT_SECRET"));
    System.setProperty("AZURE_TENANT_ID", dotenv.get("AZURE_TENANT_ID"));
    System.setProperty("AZURE_USER_ID", dotenv.get("AZURE_USER_ID"));
    System.setProperty("JWT_SECRET", dotenv.get("JWT_SECRET"));
  }
}

