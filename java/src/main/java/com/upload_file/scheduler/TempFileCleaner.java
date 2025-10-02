package com.upload_file.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;

@Component
public class TempFileCleaner {

  private final Path tempDir = Paths.get("upload_tmp");

  @Scheduled(fixedRate = 3600 * 1000) // mỗi giờ chạy 1 lần
  public void cleanOldChunks() throws IOException {
    if (!Files.exists(tempDir)) return;

    Files.list(tempDir)
        .filter(this::isOlderThanHours)
        .forEach(path -> {
          try {
            Files.deleteIfExists(path);
            System.out.println("Delete chunk success!");
          } catch (IOException e) {
            System.err.println("Could not delete chunk: " + path);
          }
        });
  }

  private boolean isOlderThanHours(Path path) {
    try {
      FileTime lastModified = Files.getLastModifiedTime(path);
      return lastModified.toMillis() < System.currentTimeMillis() - 24 * 3600_000L;
    } catch (IOException e) {
      return false;
    }
  }
}
