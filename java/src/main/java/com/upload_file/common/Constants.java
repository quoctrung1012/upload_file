package com.upload_file.common;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Constants {
  public static final String SUCCESS = "SUCCESS";
  public static final String ERROR = "ERROR";

  public enum StorageLevel {
    DATABASE,    // fileSize <= 10MB (Constants.MAX_DB_SIZE)
    SYSTEM,      // fileSize <= 30MB (Constants.MAX_SYSTEM_SIZE)
    ONEDRIVE     // fileSize > 100MB or fallback
  }

  // File system paths
  public static final Path uploadDir = Paths.get("D:/videos");
  public static final Path tempVideoDir = Paths.get(System.getProperty("java.io.tmpdir"), "video-chunks");

  // File size limits
  public static final long MAX_DB_SIZE = 10L * 1024 * 1024; // 10MB
  public static final long MAX_SYSTEM_SIZE = 30L * 1024 * 1024; // 10MB

  // Buffer sizes
  public static final int STREAM_BUFFER_SIZE = 64 * 1024; // 64KB buffer
  public static final int FLUSH_INTERVAL = 512 * 1024; // Flush every 512KB

  // File conversion settings
  public static final long LARGE_FILE_THRESHOLD = 50 * 1024 * 1024; // 50MB
  public static final long MAX_CACHE_SIZE = 10 * 1024 * 1024; // 10MB

  // Media streaming settings
  public static final int MEDIA_STREAM_BUFFER_SIZE = 64 * 1024; // 64KB cho media
  public static final long MEDIA_FLUSH_INTERVAL = 256 * 1024; // 256KB flush interval cho media
}
