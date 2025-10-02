package com.upload_file.service;

import com.upload_file.common.Constants;
import com.upload_file.exception.ConversionException;
import com.upload_file.service.abstract_file.AbstractFileService;
import com.upload_file.util.LibreOfficeDebugHelper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.jodconverter.core.DocumentConverter;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.local.LocalConverter;
import org.jodconverter.local.office.LocalOfficeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LibreOfficeService extends AbstractFileService {

  private static final Logger logger = LoggerFactory.getLogger(LibreOfficeService.class);

  @Autowired
  private LibreOfficeDebugHelper debugHelper;

  @Value("${app.converted.directory:D:/converted_files}")
  private String convertedDirectory;

  @Value("${app.converted.expire-hours:1}")
  private int expireHours;

  @Value("${libreoffice.path:C:/Program Files/LibreOffice/program}")
  private String libreOfficePath;

  @Value("${app.streaming.timeout:30000}")
  private int streamingTimeout;

  private LocalOfficeManager officeManager;
  private DocumentConverter converter;

  private volatile boolean initializationInProgress = false;
  private volatile boolean officeManagerReady = false;

  // Cache để lưu thông tin file đã convert
  private final Map<String, ConvertedFileInfo> convertedFilesCache = new ConcurrentHashMap<>();

  // Supported formats
  private static final Set<String> SUPPORTED_INPUT_FORMATS = Set.of(
      "application/msword",                                                                      // .doc
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",               // .docx
      "application/vnd.ms-powerpoint",                                                          // .ppt
      "application/vnd.openxmlformats-officedocument.presentationml.presentation"              // .pptx
  );

  private static final Set<String> SUPPORTED_FILE_EXTENSIONS = Set.of(
      ".doc", ".docx", ".ppt", ".pptx"
  );

  @PostConstruct
  public void init() {
    try {
      logger.info("Initializing LibreOfficeConversionService...");

      // Debug LibreOffice installation
      debugHelper.debugLibreOfficeInstallation();

      // Tạo thư mục converted nếu chưa có
      Path convertedDir = Paths.get(convertedDirectory);
      if (!Files.exists(convertedDir)) {
        Files.createDirectories(convertedDir);
        logger.info("Created converted directory: {}", convertedDirectory);
      }

      // Load cache từ disk
      loadConvertedFilesCache();

      // Initialize LibreOffice một lần duy nhất
      initializeLibreOfficeOnce();

      logger.info("LibreOfficeConversionService initialization completed");
    } catch (Exception e) {
      logger.error("Failed to initialize LibreOfficeConversionService: {}", e.getMessage(), e);
      // Don't fail startup completely
    }
  }

  private void initializeLibreOfficeOnce() {
    try {
      // Kiểm tra xem đã initialize chưa
      if (officeManager != null && officeManager.isRunning()) {
        logger.info("LibreOffice Office Manager is already running");
        return;
      }

      logger.info("Initializing LibreOffice Office Manager...");

      // Tìm đường dẫn LibreOffice
      String actualLibreOfficePath = findLibreOfficePath();
      if (actualLibreOfficePath == null) {
        logger.error("LibreOffice installation not found - conversion features will be disabled");
        return;
      }

      // Initialize office manager
      String officeHome = actualLibreOfficePath;
      if (actualLibreOfficePath.endsWith("\\program") || actualLibreOfficePath.endsWith("/program")) {
        officeHome = actualLibreOfficePath.substring(0, actualLibreOfficePath.lastIndexOf("program") - 1);
      }

      logger.info("Using office home: {}", officeHome);

      // Kill existing processes
      killExistingLibreOfficeProcesses();

      // Create office manager
      officeManager = LocalOfficeManager.builder()
          .officeHome(officeHome)
          .processTimeout(120000L)
          .processRetryInterval(5000L)
          .maxTasksPerProcess(5)
          .taskQueueTimeout(60000L)
          .taskExecutionTimeout(90000L)
          .portNumbers(2002)
          .build();

      logger.info("Starting LibreOffice Office Manager...");
      officeManager.start();

      // Wait for startup
      Thread.sleep(5000);

      if (officeManager.isRunning()) {
        converter = LocalConverter.make(officeManager);
        officeManagerReady = true;
        logger.info("LibreOffice initialized successfully and ready");
      } else {
        logger.error("LibreOffice Office Manager failed to start");
        officeManager = null;
        officeManagerReady = false;
      }

    } catch (Exception e) {
      logger.error("Failed to initialize LibreOffice: {}", e.getMessage());
      if (officeManager != null) {
        try {
          officeManager.stop();
        } catch (Exception ex) {
          logger.debug("Error cleaning up failed office manager: {}", ex.getMessage());
        }
        officeManager = null;
      }
      converter = null;
    }
  }

  private void initializeOfficeManager() throws OfficeException {
    // Synchronized để tránh multiple initialization
    synchronized (this) {
      try {
        // Kiểm tra xem đã running chưa
        if (officeManager != null && officeManager.isRunning()) {
          logger.debug("LibreOffice Office Manager is already running");
          return;
        }

        logger.info("Reinitializing LibreOffice Office Manager...");

        // Stop existing manager nếu có
        if (officeManager != null) {
          try {
            officeManager.stop();
          } catch (Exception e) {
            logger.debug("Error stopping existing office manager: {}", e.getMessage());
          }
        }

        // Tìm đường dẫn (không test executable nữa vì đã test ở init)
        String actualLibreOfficePath = findLibreOfficePath();
        if (actualLibreOfficePath == null) {
          throw new OfficeException("LibreOffice installation not found");
        }

        String officeHome = actualLibreOfficePath;
        if (actualLibreOfficePath.endsWith("\\program") || actualLibreOfficePath.endsWith("/program")) {
          officeHome = actualLibreOfficePath.substring(0, actualLibreOfficePath.lastIndexOf("program") - 1);
        }

        // Kill existing processes
        killExistingLibreOfficeProcesses();

        // Create new manager
        officeManager = LocalOfficeManager.builder()
            .officeHome(officeHome)
            .processTimeout(120000L)
            .processRetryInterval(5000L)
            .maxTasksPerProcess(5)
            .taskQueueTimeout(60000L)
            .taskExecutionTimeout(90000L)
            .portNumbers(2002)
            .build();

        officeManager.start();
        Thread.sleep(5000);

        if (!officeManager.isRunning()) {
          throw new OfficeException("LibreOffice Office Manager failed to restart");
        }

        converter = LocalConverter.make(officeManager);
        logger.info("LibreOffice Office Manager restarted successfully");

      } catch (Exception e) {
        logger.error("Failed to restart LibreOffice Office Manager: {}", e.getMessage(), e);
        if (officeManager != null) {
          try {
            officeManager.stop();
          } catch (Exception cleanupEx) {
            logger.debug("Error during cleanup: {}", cleanupEx.getMessage());
          }
        }
        throw new OfficeException("Cannot restart LibreOffice: " + e.getMessage(), e);
      }
    }
  }

  private void killExistingLibreOfficeProcesses() {
    try {
      logger.info("Terminating existing LibreOffice processes...");

      // Trước tiên thử terminate gently
      String[] gentleKillCommands = {
          "taskkill /IM soffice.exe",
          "taskkill /IM soffice.bin"
      };

      for (String command : gentleKillCommands) {
        try {
          Process process = Runtime.getRuntime().exec(command);
          boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
          if (finished) {
            int exitCode = process.exitValue();
            logger.debug("Gentle kill command '{}' exit code: {}", command, exitCode);
          }
        } catch (Exception e) {
          logger.debug("Gentle kill command failed: {} - {}", command, e.getMessage());
        }
      }

      // Wait for gentle termination
      Thread.sleep(3000);

      // Sau đó force kill
      String[] forceKillCommands = {
          "taskkill /F /IM soffice.exe",
          "taskkill /F /IM soffice.bin"
      };

      for (String command : forceKillCommands) {
        try {
          Process process = Runtime.getRuntime().exec(command);
          boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
          if (finished) {
            int exitCode = process.exitValue();
            logger.debug("Force kill command '{}' exit code: {}", command, exitCode);
          }
        } catch (Exception e) {
          logger.debug("Force kill command failed: {} - {}", command, e.getMessage());
        }
      }

      // Final wait for processes to terminate
      Thread.sleep(2000);

      logger.info("LibreOffice process termination completed");

    } catch (Exception e) {
      logger.warn("Error terminating existing LibreOffice processes: {}", e.getMessage());
    }
  }

  private String findLibreOfficePath() {
    // Các đường dẫn có thể của LibreOffice trên Windows - Updated order
    String[] possiblePaths = {
        libreOfficePath, // From config first
        "C:\\Program Files\\LibreOffice\\program",
        "C:\\Program Files (x86)\\LibreOffice\\program",
        "C:\\Program Files\\LibreOffice",
        "C:\\Program Files (x86)\\LibreOffice",
        System.getProperty("user.home") + "\\AppData\\Local\\Programs\\LibreOffice\\program",
        "C:\\LibreOffice\\program",
        "D:\\LibreOffice\\program"
    };

    for (String basePath : possiblePaths) {
      if (basePath != null && isValidLibreOfficePath(basePath)) {
        return basePath;
      }

      // Also try with \program subdirectory if not already included
      if (basePath != null && !basePath.endsWith("\\program")) {
        String programPath = basePath + "\\program";
        if (isValidLibreOfficePath(programPath)) {
          return programPath;
        }
      }
    }

    logger.error("LibreOffice installation not found in any of the expected locations");
    debugHelper.debugLibreOfficeInstallation();
    return null;
  }

  private boolean isValidLibreOfficePath(String path) {
    try {
      Path basePath = Paths.get(path);
      if (!Files.exists(basePath) || !Files.isDirectory(basePath)) {
        return false;
      }

      // Kiểm tra các file executable có thể có - prioritize soffice.exe
      String[] executables = {"soffice.exe", "soffice.bin", "soffice"};

      for (String executable : executables) {
        Path execPath = basePath.resolve(executable);
        if (Files.exists(execPath) && Files.isRegularFile(execPath)) {
          try {
            if (Files.isExecutable(execPath)) {
              logger.debug("Found valid LibreOffice executable: {}", execPath);
              return true;
            }
          } catch (Exception e) {
            logger.debug("Cannot verify executable status for {}: {}", execPath, e.getMessage());
            // Continue checking other executables
          }
        }
      }

      logger.debug("No valid LibreOffice executable found in: {}", path);
      return false;
    } catch (Exception e) {
      logger.debug("Error checking LibreOffice path {}: {}", path, e.getMessage());
      return false;
    }
  }

  @PreDestroy
  public void cleanup() {
    try {
      if (officeManager != null && officeManager.isRunning()) {
        officeManager.stop();
        logger.info("LibreOffice Office Manager stopped");
      }
    } catch (Exception e) {
      logger.error("Error stopping LibreOffice Office Manager: {}", e.getMessage());
    }
  }

  /**
   * Kiểm tra xem file có hỗ trợ chuyển đổi không
   */
  public boolean isConvertibleDocument(String contentType, String fileName) {
    // Check if initialization completed
    if (!officeManagerReady) {
      logger.debug("LibreOffice not ready - conversion disabled");
      return false;
    }

    // Check if LibreOffice is available
    if (officeManager == null || !officeManager.isRunning()) {
      logger.debug("LibreOffice not running - conversion disabled");
      return false;
    }

    if (contentType != null && SUPPORTED_INPUT_FORMATS.contains(contentType)) {
      return true;
    }

    if (fileName != null) {
      String extension = getFileExtension(fileName).toLowerCase();
      return SUPPORTED_FILE_EXTENSIONS.contains(extension);
    }

    return false;
  }

  /**
   * Chuyển đổi file sang PDF và stream với Range support
   */
  public ResponseEntity<?> convertAndStreamPDF(String fileId,
                                               String fileName,
                                               byte[] fileData,
                                               HttpServletRequest request,
                                               HttpServletResponse response,
                                               boolean download) {
    try {
      // Kiểm tra kích thước file
      if (fileData.length > Constants.MAX_DB_SIZE) {
        throw new ConversionException(fileName, ConversionException.ConversionErrorType.FILE_TOO_LARGE);
      }

      // Kiểm tra LibreOffice
      if (officeManager == null || !officeManager.isRunning()) {
        throw new ConversionException(fileName, ConversionException.ConversionErrorType.LIBREOFFICE_NOT_AVAILABLE);
      }

      // Kiểm tra format hỗ trợ
      if (!isConvertibleDocument(null, fileName)) {
        throw new ConversionException(fileName, ConversionException.ConversionErrorType.UNSUPPORTED_FORMAT);
      }

      // Tạo cache key
      String cacheKey = generateCacheKey(fileId, fileName, fileData.length);

      // Kiểm tra cache
      Path pdfPath = getCachedPDFPath(cacheKey);
      if (pdfPath == null) {
        // Chuyển đổi mới
        pdfPath = convertToPDF(fileId, fileName, fileData, cacheKey);
        if (pdfPath == null) {
          throw new ConversionException(fileName, ConversionException.ConversionErrorType.CONVERSION_FAILED);
        }
      }

      // Stream PDF với Range support
      return streamPDFFile(pdfPath, fileName, request, response, download);

    } catch (ConversionException e) {
      // Re-throw conversion exceptions
      throw e;
    } catch (Exception e) {
      logger.error("Unexpected error converting and streaming PDF for file {}: {}", fileName, e.getMessage(), e);
      throw new ConversionException(fileName, ConversionException.ConversionErrorType.CONVERSION_FAILED, e);
    }
  }

  private Path convertToPDF(String fileId, String fileName, byte[] fileData, String cacheKey) {
    Path inputPath = null;
    Path outputPath = null;

    try {
      // Kiểm tra LibreOffice trước khi làm gì
      if (!officeManagerReady || officeManager == null || !officeManager.isRunning()) {
        logger.error("LibreOffice is not ready for conversion");
        throw new ConversionException(fileName, ConversionException.ConversionErrorType.LIBREOFFICE_NOT_AVAILABLE);
      }

      // Validate file data
      if (fileData == null || fileData.length == 0) {
        logger.error("File data is null or empty for file: {}", fileName);
        throw new ConversionException(fileName, ConversionException.ConversionErrorType.IO_ERROR);
      }

      // Create unique temporary files
      String inputExtension = getFileExtension(fileName);
      String uniqueId = fileId + "_" + System.currentTimeMillis() + "_" + Thread.currentThread().getId();
      inputPath = Paths.get(convertedDirectory, "temp_input_" + uniqueId + inputExtension);

      // Ensure directory exists
      Files.createDirectories(inputPath.getParent());
      Files.write(inputPath, fileData);

      // Verify input file
      if (!Files.exists(inputPath) || Files.size(inputPath) == 0) {
        logger.error("Failed to create valid input file for conversion: {}", fileName);
        throw new ConversionException(fileName, ConversionException.ConversionErrorType.IO_ERROR);
      }

      // Create output path
      String pdfFileName = sanitizeFileName(fileName) + "_" + uniqueId + ".pdf";
      outputPath = Paths.get(convertedDirectory, pdfFileName);

      logger.info("Converting {} to PDF (attempt): {} -> {}", fileName, inputPath.getFileName(), outputPath.getFileName());

      // Conversion with retry
      boolean conversionSuccess = false;
      int maxRetries = 2; // Giảm retry xuống 2
      Exception lastException = null;

      for (int attempt = 1; attempt <= maxRetries; attempt++) {
        try {
          logger.debug("Conversion attempt {} for {}", attempt, fileName);

          // Double check LibreOffice is still running
          if (!officeManager.isRunning()) {
            logger.warn("LibreOffice stopped, attempting restart...");
            initializeOfficeManager();
          }

          // Perform conversion
          converter.convert(inputPath.toFile()).to(outputPath.toFile()).execute();

          // Verify output
          if (Files.exists(outputPath) && Files.size(outputPath) > 0) {
            conversionSuccess = true;
            logger.info("Conversion successful on attempt {} for {}", attempt, fileName);
            break;
          } else {
            logger.warn("Conversion attempt {} produced no output for {}", attempt, fileName);
          }

        } catch (Exception e) {
          lastException = e;
          logger.warn("Conversion attempt {} failed for {}: {}", attempt, fileName, e.getMessage());

          // Clean up failed output
          try {
            Files.deleteIfExists(outputPath);
          } catch (Exception cleanupEx) {
            logger.debug("Failed to cleanup failed output: {}", cleanupEx.getMessage());
          }

          if (attempt < maxRetries) {
            try {
              Thread.sleep(3000); // Wait longer between retries
            } catch (InterruptedException ie) {
              Thread.currentThread().interrupt();
              break;
            }
          }
        }
      }

      if (!conversionSuccess) {
        logger.error("PDF conversion failed after {} attempts for {}", maxRetries, fileName);
        if (lastException instanceof OfficeException) {
          throw new ConversionException(fileName, ConversionException.ConversionErrorType.CONVERSION_FAILED, lastException);
        } else {
          throw new ConversionException(fileName, ConversionException.ConversionErrorType.CONVERSION_FAILED, lastException);
        }
      }

      // Cache successful conversion
      ConvertedFileInfo fileInfo = new ConvertedFileInfo(
          outputPath.toString(),
          LocalDateTime.now(),
          Files.size(outputPath)
      );
      convertedFilesCache.put(cacheKey, fileInfo);
      saveConvertedFilesCache();

      logger.info("Successfully converted {} to PDF, size: {} bytes", fileName, Files.size(outputPath));
      return outputPath;

    } catch (ConversionException e) {
      throw e;
    } catch (Exception e) {
      logger.error("Unexpected error during conversion of {}: {}", fileName, e.getMessage(), e);
      throw new ConversionException(fileName, ConversionException.ConversionErrorType.CONVERSION_FAILED, e);
    } finally {
      // Clean up input file
      if (inputPath != null) {
        try {
          Files.deleteIfExists(inputPath);
        } catch (Exception e) {
          logger.warn("Failed to delete temp input file {}: {}", inputPath, e.getMessage());
        }
      }
    }
  }

  private ResponseEntity<?> streamPDFFile(Path pdfPath,
                                          String originalFileName,
                                          HttpServletRequest request,
                                          HttpServletResponse response,
                                          boolean download) {
    try {
      if (!Files.exists(pdfPath)) {
        return ResponseEntity.notFound().build();
      }

      long fileSize = Files.size(pdfPath);
      String pdfFileName = sanitizeFileName(originalFileName) + ".pdf";

      // Set headers
      String disposition = String.format("%s; filename*=UTF-8''%s", download ? "attachment" : "inline", encodeFilenameForHeader(pdfFileName));

      response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition);
      response.setHeader("Accept-Ranges", "bytes");
      response.setHeader("Cache-Control", "public, max-age=3600");
      response.setContentType(MediaType.APPLICATION_PDF_VALUE);

      // Handle Range requests
      String rangeHeader = request.getHeader("Range");
      if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
        return handleRangeRequest(pdfPath, rangeHeader, response, fileSize);
      } else {
        return streamFullFile(pdfPath, response, fileSize);
      }

    } catch (Exception e) {
      logger.error("Error streaming PDF file {}: {}", pdfPath, e.getMessage(), e);
      if (isClientDisconnected(e)) {
        logger.debug("Client disconnected while streaming PDF: {}", originalFileName);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
      }
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", "Failed to stream PDF"));
    }
  }

  private ResponseEntity<?> handleRangeRequest(Path pdfPath, String rangeHeader,
                                               HttpServletResponse response, long fileSize) {
    try {
      // Parse range header: bytes=start-end
      String range = rangeHeader.substring(6);
      String[] parts = range.split("-");

      long start = 0;
      long end = fileSize - 1;

      if (parts.length >= 1 && !parts[0].isEmpty()) {
        start = Long.parseLong(parts[0]);
      }

      if (parts.length >= 2 && !parts[1].isEmpty()) {
        end = Long.parseLong(parts[1]);
      }

      // Validate range
      if (start > end || start < 0 || end >= fileSize) {
        response.setStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value());
        response.setHeader("Content-Range", "bytes */" + fileSize);
        return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).build();
      }

      long contentLength = end - start + 1;

      // Set partial content headers
      response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
      response.setHeader("Content-Range", String.format("bytes %d-%d/%d", start, end, fileSize));
      response.setHeader("Content-Length", String.valueOf(contentLength));

      // Stream partial content
      return streamPartialContent(pdfPath, start, contentLength, response);

    } catch (Exception e) {
      logger.error("Error handling range request: {}", e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  private ResponseEntity<?> streamPartialContent(Path pdfPath, long start, long contentLength,
                                                 HttpServletResponse response) {
    try (RandomAccessFile file = new RandomAccessFile(pdfPath.toFile(), "r");
         ServletOutputStream outputStream = response.getOutputStream()) {

      file.seek(start);

      byte[] buffer = new byte[Constants.STREAM_BUFFER_SIZE];
      long remaining = contentLength;

      while (remaining > 0) {
        int bytesToRead = (int) Math.min(buffer.length, remaining);
        int bytesRead = file.read(buffer, 0, bytesToRead);

        if (bytesRead == -1) {
          break;
        }

        try {
          outputStream.write(buffer, 0, bytesRead);
          remaining -= bytesRead;

          // Periodic flush
          if ((contentLength - remaining) % Constants.FLUSH_INTERVAL == 0) {
            outputStream.flush();
          }
        } catch (IOException e) {
          if (isClientDisconnected(e)) {
            logger.debug("Client disconnected during partial content streaming");
            break;
          }
          throw e;
        }
      }

      if (!response.isCommitted()) {
        outputStream.flush();
      }

      return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).build();

    } catch (Exception e) {
      if (isClientDisconnected(e)) {
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
      }
      throw new RuntimeException("Error streaming partial content", e);
    }
  }

  private ResponseEntity<?> streamFullFile(Path pdfPath, HttpServletResponse response, long fileSize) {
    try (InputStream inputStream = Files.newInputStream(pdfPath);
         BufferedInputStream bufferedInput = new BufferedInputStream(inputStream, Constants.STREAM_BUFFER_SIZE);
         ServletOutputStream outputStream = response.getOutputStream()) {

      response.setHeader("Content-Length", String.valueOf(fileSize));

      byte[] buffer = new byte[Constants.STREAM_BUFFER_SIZE];
      int bytesRead;
      long totalBytesRead = 0;

      while ((bytesRead = bufferedInput.read(buffer)) != -1) {
        try {
          outputStream.write(buffer, 0, bytesRead);
          totalBytesRead += bytesRead;

          // Periodic flush
          if (totalBytesRead % Constants.FLUSH_INTERVAL == 0) {
            outputStream.flush();
          }
        } catch (IOException e) {
          if (isClientDisconnected(e)) {
            logger.debug("Client disconnected during full file streaming");
            break;
          }
          throw e;
        }
      }

      if (!response.isCommitted()) {
        outputStream.flush();
      }

      return ResponseEntity.ok().build();

    } catch (Exception e) {
      if (isClientDisconnected(e)) {
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
      }
      throw new RuntimeException("Error streaming full file", e);
    }
  }

  private String generateCacheKey(String fileId, String fileName, long fileSize) {
    return String.format("%s_%s_%d", fileId, sanitizeFileName(fileName), fileSize);
  }

  private Path getCachedPDFPath(String cacheKey) {
    ConvertedFileInfo fileInfo = convertedFilesCache.get(cacheKey);
    if (fileInfo != null) {
      Path pdfPath = Paths.get(fileInfo.filePath);

      // Kiểm tra file có tồn tại và chưa expired
      if (Files.exists(pdfPath) && !isExpired(fileInfo.createdAt)) {
        logger.debug("Using cached PDF: {}", pdfPath);
        return pdfPath;
      } else {
        // Xóa cache entry nếu file không tồn tại hoặc expired
        convertedFilesCache.remove(cacheKey);
        try {
          Files.deleteIfExists(pdfPath);
        } catch (Exception e) {
          logger.warn("Failed to delete expired PDF file: {}", e.getMessage());
        }
      }
    }
    return null;
  }

  private boolean isExpired(LocalDateTime createdAt) {
    return createdAt.plusHours(expireHours).isBefore(LocalDateTime.now());
  }

  @Scheduled(fixedRate = 3600000) // Run every hour
  public void cleanupExpiredFiles() {
    logger.info("Starting cleanup of expired converted files...");

    List<String> keysToRemove = new ArrayList<>();
    int deletedFiles = 0;

    for (Map.Entry<String, ConvertedFileInfo> entry : convertedFilesCache.entrySet()) {
      ConvertedFileInfo fileInfo = entry.getValue();

      if (isExpired(fileInfo.createdAt)) {
        try {
          Path pdfPath = Paths.get(fileInfo.filePath);
          if (Files.deleteIfExists(pdfPath)) {
            deletedFiles++;
            logger.debug("Deleted expired PDF: {}", pdfPath);
          }
          keysToRemove.add(entry.getKey());
        } catch (Exception e) {
          logger.warn("Failed to delete expired file {}: {}", fileInfo.filePath, e.getMessage());
        }
      }
    }

    // Remove from cache
    keysToRemove.forEach(convertedFilesCache::remove);

    if (deletedFiles > 0) {
      saveConvertedFilesCache();
      logger.info("Cleanup completed: deleted {} expired PDF files", deletedFiles);
    }
  }

  private void loadConvertedFilesCache() {
    // Simple implementation - in production, consider using a database or Redis
    Path cacheFile = Paths.get(convertedDirectory, "cache.properties");
    if (Files.exists(cacheFile)) {
      try {
        Properties props = new Properties();
        props.load(Files.newBufferedReader(cacheFile, StandardCharsets.UTF_8));

        for (String key : props.stringPropertyNames()) {
          String[] parts = props.getProperty(key).split("\\|");
          if (parts.length == 3) {
            ConvertedFileInfo info = new ConvertedFileInfo(
                parts[0],
                LocalDateTime.parse(parts[1]),
                Long.parseLong(parts[2])
            );
            convertedFilesCache.put(key, info);
          }
        }
        logger.info("Loaded {} cached file entries", convertedFilesCache.size());
      } catch (Exception e) {
        logger.warn("Failed to load cache: {}", e.getMessage());
      }
    }
  }

  private void saveConvertedFilesCache() {
    Path cacheFile = Paths.get(convertedDirectory, "cache.properties");
    try {
      Properties props = new Properties();
      for (Map.Entry<String, ConvertedFileInfo> entry : convertedFilesCache.entrySet()) {
        ConvertedFileInfo info = entry.getValue();
        String value = String.format("%s|%s|%d",
            info.filePath,
            info.createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            info.fileSize);
        props.setProperty(entry.getKey(), value);
      }

      props.store(Files.newBufferedWriter(cacheFile, StandardCharsets.UTF_8),
          "Converted files cache");
    } catch (Exception e) {
      logger.warn("Failed to save cache: {}", e.getMessage());
    }
  }

  private String sanitizeFileName(String fileName) {
    if (fileName == null) return "document";

    // Remove extension and sanitize
    int lastDot = fileName.lastIndexOf('.');
    String nameWithoutExt = lastDot > 0 ? fileName.substring(0, lastDot) : fileName;

    return nameWithoutExt.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  // Inner class for cache information
  @AllArgsConstructor
  private static class ConvertedFileInfo {
    final String filePath;
    final LocalDateTime createdAt;
    final long fileSize;
  }
}