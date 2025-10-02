package com.upload_file.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LogCleanupTask {

  private static final String LOG_DIR = "/var/logs/video-upload/";
  private static final Pattern LOG_PATTERN = Pattern.compile("video-upload\\.log\\.(\\d{4}-\\d{2}-\\d{2})\\.0\\.gz");
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  // Chạy mỗi tuần vào Chủ Nhật lúc 2:00 sáng
  @Scheduled(cron = "0 0 2 ? * SUN")
  public void cleanOldLogs() {
    File dir = new File(LOG_DIR);
    if (!dir.exists() || !dir.isDirectory()) return;

    File[] files = dir.listFiles();
    if (files == null) return;

    LocalDate oneWeekAgo = LocalDate.now().minusWeeks(1);

    for (File file : files) {
      Matcher matcher = LOG_PATTERN.matcher(file.getName());
      if (matcher.matches()) {
        LocalDate fileDate = LocalDate.parse(matcher.group(1), DATE_FORMATTER);
        if (fileDate.isBefore(oneWeekAgo)) {
          boolean deleted = file.delete();
          System.out.println("Deleted " + file.getName() + ": " + deleted);
        }
      }
    }
  }
}

