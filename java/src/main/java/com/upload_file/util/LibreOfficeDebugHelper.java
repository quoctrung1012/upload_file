package com.upload_file.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class LibreOfficeDebugHelper {

  private static final Logger logger = LoggerFactory.getLogger(LibreOfficeDebugHelper.class);

  public void debugLibreOfficeInstallation() {
    logger.info("=== LibreOffice Debug Information ===");

    // Kiểm tra các đường dẫn phổ biến
    String[] commonPaths = {
        "C:\\Program Files\\LibreOffice",
        "C:\\Program Files (x86)\\LibreOffice",
        "C:\\Program Files\\LibreOffice\\program",
        "C:\\Program Files (x86)\\LibreOffice\\program"
    };

    for (String pathStr : commonPaths) {
      debugPath(pathStr);
    }

    // Kiểm tra registry (Windows)
    debugWindowsRegistry();
  }

  private void debugPath(String pathStr) {
    try {
      Path path = Paths.get(pathStr);
      if (!Files.exists(path)) return;
      // List contents
      if (Files.isDirectory(path)) {
        try {
          Files.list(path).forEach(p -> {});
        } catch (Exception e) {
          logger.info("    * Error listing contents: {}", e.getMessage());
        }
      }

      // Check for executables
      String[] executables = {"soffice.exe", "soffice.bin", "soffice"};
      for (String executable : executables) {
        Path execPath = path.resolve(executable);
        if (Files.exists(execPath)) {
          logger.info("  - Found executable: {} (size: {} bytes)",
              executable, Files.size(execPath));
        }
      }

    } catch (Exception e) {
      logger.error("  - Error checking path {}: {}", pathStr, e.getMessage());
    }
  }

  private void debugWindowsRegistry() {
    try {
      logger.info("Checking Windows Registry for LibreOffice...");

      // Try to read registry using Runtime.exec
      String[] commands = {
          "reg query \"HKEY_LOCAL_MACHINE\\SOFTWARE\\LibreOffice\\UNO\\InstallPath\" /ve",
          "reg query \"HKEY_LOCAL_MACHINE\\SOFTWARE\\WOW6432Node\\LibreOffice\\UNO\\InstallPath\" /ve"
      };

      for (String command : commands) {
        try {
          Process process = Runtime.getRuntime().exec(command);
          process.waitFor();

          // Log exit code
          logger.info("Registry command exit code: {} for: {}",
              process.exitValue(), command);

        } catch (Exception e) {
          logger.debug("Registry query failed: {}", e.getMessage());
        }
      }

    } catch (Exception e) {
      logger.debug("Error checking Windows registry: {}", e.getMessage());
    }
  }

  public String findLibreOfficeInstallation() {
    // Thứ tự ưu tiên tìm kiếm
    String[] searchPaths = {
        "C:\\Program Files\\LibreOffice\\program",
        "C:\\Program Files (x86)\\LibreOffice\\program",
        "C:\\Program Files\\LibreOffice",
        "C:\\Program Files (x86)\\LibreOffice"
    };

    for (String pathStr : searchPaths) {
      if (isValidLibreOfficeInstallation(pathStr)) {
        logger.info("Found valid LibreOffice installation at: {}", pathStr);
        return pathStr;
      }
    }

    logger.error("No valid LibreOffice installation found!");
    return null;
  }

  private boolean isValidLibreOfficeInstallation(String pathStr) {
    try {
      Path path = Paths.get(pathStr);
      if (!Files.exists(path) || !Files.isDirectory(path)) {
        return false;
      }

      // Kiểm tra executable
      String[] executables = {"soffice.exe", "soffice.bin", "soffice"};
      boolean hasExecutable = false;

      for (String executable : executables) {
        Path execPath = path.resolve(executable);
        if (Files.exists(execPath) && Files.isRegularFile(execPath)) {
          hasExecutable = true;
          break;
        }
      }

      if (!hasExecutable) {
        // Thử tìm trong thư mục con program
        Path programPath = path.resolve("program");
        if (Files.exists(programPath)) {
          for (String executable : executables) {
            Path execPath = programPath.resolve(executable);
            if (Files.exists(execPath) && Files.isRegularFile(execPath)) {
              hasExecutable = true;
              break;
            }
          }
        }
      }

      return hasExecutable;

    } catch (Exception e) {
      logger.debug("Error validating LibreOffice installation at {}: {}",
          pathStr, e.getMessage());
      return false;
    }
  }
}