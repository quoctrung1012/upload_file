package com.upload_file.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class LibreOfficeValidator {

  private static final Logger logger = LoggerFactory.getLogger(LibreOfficeValidator.class);

  @Value("${libreoffice.path:C:\\Program Files\\LibreOffice\\program}")
  private String libreOfficePath;

  @Value("${libreoffice.skip-version-check:true}")
  private boolean skipVersionCheck;

  @EventListener(ApplicationReadyEvent.class)
  public void validateLibreOfficeInstallation() {
    logger.info("Validating LibreOffice installation...");

    try {
      // 1. Kiểm tra thư mục LibreOffice
      if (!validateLibreOfficePath()) {
        logger.error("LibreOffice path validation failed: {}", libreOfficePath);
        return;
      }

      // 2. Kiểm tra executable
      if (!validateLibreOfficeExecutable()) {
        logger.error("LibreOffice executable validation failed");
        return;
      }

      // 3. Bỏ qua kiểm tra version để tránh terminal popup
//      if (!skipVersionCheck) {
//        validateLibreOfficeVersion();
//      }

      logger.info("LibreOffice validation completed successfully");

    } catch (Exception e) {
      logger.error("LibreOffice validation failed: {}", e.getMessage(), e);
    }
  }

  private boolean validateLibreOfficePath() {
    try {
      Path librePath = Paths.get(libreOfficePath);
      if (!Files.exists(librePath)) {
        logger.warn("LibreOffice directory not found: {}", libreOfficePath);

        // Try common alternative paths on Windows - Updated paths
        String[] alternativePaths = {
            "C:\\Program Files\\LibreOffice\\program",
            "C:\\Program Files (x86)\\LibreOffice\\program",
            "C:\\Program Files\\LibreOffice",
            "C:\\Program Files (x86)\\LibreOffice",
            System.getProperty("user.home") + "\\AppData\\Local\\Programs\\LibreOffice\\program",
            // Add more potential paths
            "C:\\LibreOffice\\program",
            "D:\\LibreOffice\\program",
            System.getenv("PROGRAMFILES") + "\\LibreOffice\\program",
            System.getenv("PROGRAMFILES(X86)") + "\\LibreOffice\\program"
        };

        for (String altPath : alternativePaths) {
          if (altPath != null) { // Check for null environment variables
            Path altLibrePath = Paths.get(altPath);
            if (Files.exists(altLibrePath) && hasValidExecutable(altLibrePath)) {
              logger.info("Found LibreOffice at alternative path: {}", altPath);
              libreOfficePath = altPath;
              return true;
            }
          }
        }
        return false;
      }

      logger.info("LibreOffice directory found: {}", libreOfficePath);
      return hasValidExecutable(librePath);

    } catch (Exception e) {
      logger.error("Error validating LibreOffice path: {}", e.getMessage());
      return false;
    }
  }

  private boolean hasValidExecutable(Path libPath) {
    // Windows executable names in priority order
    String[] executableNames = {"soffice.exe", "soffice.bin", "soffice"};

    for (String execName : executableNames) {
      Path execPath = libPath.resolve(execName);
      if (Files.exists(execPath) && Files.isRegularFile(execPath)) {
        try {
          if (Files.isExecutable(execPath)) {
            logger.info("Found valid LibreOffice executable: {}", execPath);
            return true;
          }
        } catch (Exception e) {
          logger.debug("Cannot check if {} is executable: {}", execPath, e.getMessage());
        }
      }
    }

    logger.warn("No valid LibreOffice executable found in: {}", libPath);
    return false;
  }

  private boolean validateLibreOfficeExecutable() {
    try {
      // Windows executable names
      String[] executableNames = {"soffice.exe", "soffice.bin", "soffice"};

      for (String execName : executableNames) {
        Path execPath = Paths.get(libreOfficePath, execName);
        if (Files.exists(execPath) && Files.isExecutable(execPath)) {
          logger.info("LibreOffice executable found: {}", execPath);
          return true;
        }
      }

      logger.error("No LibreOffice executable found in: {}", libreOfficePath);
      return false;

    } catch (Exception e) {
      logger.error("Error validating LibreOffice executable: {}", e.getMessage());
      return false;
    }
  }

  private void validateLibreOfficeVersion() {
    try {
      Path execPath = Paths.get(libreOfficePath, "soffice.exe");
      if (!Files.exists(execPath)) {
        execPath = Paths.get(libreOfficePath, "soffice");
      }

      // Thêm --invisible và --headless để chạy ngầm
      ProcessBuilder pb = new ProcessBuilder(execPath.toString(), "--version", "--invisible", "--headless");
      pb.redirectErrorStream(true);

      Process process = pb.start();

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.contains("LibreOffice")) {
            logger.info("LibreOffice version: {}", line.trim());
            break;
          }
        }
      }

      // Giảm timeout xuống 5 giây
      boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        logger.debug("LibreOffice version check timed out");
      }

    } catch (Exception e) {
      logger.debug("Could not determine LibreOffice version: {}", e.getMessage());
    }
  }

  /**
   * Manual validation method that can be called from other services
   */
  public boolean isLibreOfficeAvailable() {
    return validateLibreOfficePath() && validateLibreOfficeExecutable();
  }

  public String getValidatedLibreOfficePath() {
    return isLibreOfficeAvailable() ? libreOfficePath : null;
  }
}