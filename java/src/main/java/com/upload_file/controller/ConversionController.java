package com.upload_file.controller;

import com.upload_file.service.LibreOfficeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/conversion")
public class ConversionController {

  private static final Logger logger = LoggerFactory.getLogger(ConversionController.class);

  @Autowired
  private LibreOfficeService libreOfficeService;

  /**
   * Test endpoint để convert file trực tiếp sang PDF
   */
  @PostMapping("/test-convert")
  public ResponseEntity<?> testConversion(@RequestParam("file") MultipartFile file,
                                          HttpServletRequest request,
                                          HttpServletResponse response) {
    try {
      if (file.isEmpty()) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", "No file provided"));
      }

      String fileName = file.getOriginalFilename();
      byte[] fileData = file.getBytes();

      logger.info("Testing conversion for file: {} ({} bytes)", fileName, fileData.length);

      // Kiểm tra file có thể convert không
      if (!libreOfficeService.isConvertibleDocument(file.getContentType(), fileName)) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", "File format not supported for conversion",
                "fileName", fileName,
                "contentType", file.getContentType()));
      }

      // Thực hiện conversion và stream
      String testId = "test_" + System.currentTimeMillis();
      return libreOfficeService.convertAndStreamPDF(
          testId, fileName, fileData, request, response, false);

    } catch (Exception e) {
      logger.error("Test conversion failed: {}", e.getMessage(), e);
      return ResponseEntity.internalServerError()
          .body(Map.of("error", "Conversion test failed: " + e.getMessage()));
    }
  }

  /**
   * Endpoint để kiểm tra trạng thái LibreOffice
   */
  @GetMapping("/status")
  public ResponseEntity<Map<String, Object>> getConversionStatus() {
    try {
      // Kiểm tra các định dạng hỗ trợ
      Map<String, Object> status = Map.of(
          "libreOfficeAvailable", true, // Sẽ được update trong service
          "supportedFormats", Map.of(
              "input", new String[]{"doc", "docx", "ppt", "pptx"},
              "output", new String[]{"pdf"}
          ),
          "timestamp", System.currentTimeMillis()
      );

      return ResponseEntity.ok(status);

    } catch (Exception e) {
      logger.error("Error checking conversion status: {}", e.getMessage(), e);
      return ResponseEntity.internalServerError()
          .body(Map.of("error", "Failed to check conversion status"));
    }
  }

  /**
   * Endpoint để force cleanup expired files
   */
  @PostMapping("/cleanup")
  public ResponseEntity<Map<String, Object>> forceCleanup() {
    try {
      libreOfficeService.cleanupExpiredFiles();

      return ResponseEntity.ok(Map.of(
          "message", "Cleanup completed successfully",
          "timestamp", System.currentTimeMillis()
      ));

    } catch (Exception e) {
      logger.error("Manual cleanup failed: {}", e.getMessage(), e);
      return ResponseEntity.internalServerError()
          .body(Map.of("error", "Cleanup failed: " + e.getMessage()));
    }
  }
}