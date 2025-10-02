package com.upload_file.controller;

import com.upload_file.common.Constants;
import com.upload_file.dto.ResponseResult;
import com.upload_file.service.FilePreviewService;
import com.upload_file.service.OneDriveService;
import io.micrometer.core.annotation.Timed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:3000"},
    allowedHeaders = "*",
    methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
@RequestMapping("/test")
public class TestController {

  private static final Logger logger = LoggerFactory.getLogger(TestController.class);

  @Autowired
  private FilePreviewService filePreviewService;

  @Autowired
  private OneDriveService oneDriveService;

  @GetMapping("/onedrive")
  public ResponseEntity<String> testOneDrive() {
    System.out.println("Testing OneDrive connection...");
    try {
      oneDriveService.testConnection();
      return ResponseEntity.ok("OneDrive connection successful");
    } catch (Exception e) {
      return ResponseEntity.status(500).body("Error: " + e.getMessage());
    }
  }

  @GetMapping("/preview-filedb")
  @Timed(value = "file.preview.filedb", description = "Time taken to preview file using FileDBService")
  public ResponseEntity<?> previewFileWithFileDBService(@RequestParam @NotBlank String id,
                                                        @RequestParam(defaultValue = "false") boolean download,
                                                        HttpServletRequest request,
                                                        HttpServletResponse response) {

    long startTime = System.currentTimeMillis();
    logger.info("FileDBService preview request: id={}, download={}, startTime={}", id, download, startTime);

    try {
      ResponseEntity<?> result = filePreviewService.previewFile(id, request, response, download);

      long endTime = System.currentTimeMillis();
      long duration = endTime - startTime;

      logger.info("FileDBService preview completed: id={}, duration={}ms, status={}",
          id, duration, result.getStatusCode());

      // Thêm header để client biết thời gian xử lý
      return ResponseEntity.status(result.getStatusCode())
          .headers(result.getHeaders())
          .header("X-Processing-Time-Ms", String.valueOf(duration))
          .header("X-Service-Used", "FileDBService")
          .body(result.getBody());

    } catch (Exception e) {
      long endTime = System.currentTimeMillis();
      long duration = endTime - startTime;

      logger.error("FileDBService preview error: id={}, duration={}ms, error={}",
          id, duration, e.getMessage(), e);

      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .header("X-Processing-Time-Ms", String.valueOf(duration))
          .header("X-Service-Used", "FileDBService")
          .body(new ResponseResult("FileDBService preview failed: " + e.getMessage(), Constants.ERROR));
    }
  }

  /*@GetMapping("/preview-handle")
  @Timed(value = "file.preview.handle", description = "Time taken to preview file using PreviewFileService")
  public ResponseEntity<?> previewFileWithPreviewFileService(@RequestParam @NotBlank String id,
                                                             @RequestParam(defaultValue = "false") boolean download,
                                                             HttpServletRequest request,
                                                             HttpServletResponse response) {

    long startTime = System.currentTimeMillis();
    logger.info("PreviewFileService preview request: id={}, download={}, startTime={}", id, download, startTime);

    try {
      ResponseEntity<?> result = fileDBService.handlePreviewFileOptimized(id, request, response, download);

      long endTime = System.currentTimeMillis();
      long duration = endTime - startTime;

      logger.info("PreviewFileService preview completed: id={}, duration={}ms, status={}",
          id, duration, result.getStatusCode());

      // Thêm header để client biết thời gian xử lý
      return ResponseEntity.status(result.getStatusCode())
          .headers(result.getHeaders())
          .header("X-Processing-Time-Ms", String.valueOf(duration))
          .header("X-Service-Used", "PreviewFileService")
          .body(result.getBody());

    } catch (Exception e) {
      long endTime = System.currentTimeMillis();
      long duration = endTime - startTime;

      logger.error("PreviewFileService preview error: id={}, duration={}ms, error={}",
          id, duration, e.getMessage(), e);

      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .header("X-Processing-Time-Ms", String.valueOf(duration))
          .header("X-Service-Used", "PreviewFileService")
          .body(new ResponseResult("PreviewFileService preview failed: " + e.getMessage(), Constants.ERROR));
    }
  }*/

  // Method so sánh performance của cả 2 service (optional)
  /*@GetMapping("/preview-compare")
  @Timed(value = "file.preview.compare", description = "Time taken to compare both preview services")
  public ResponseEntity<?> comparePreviewServices(@RequestParam @NotBlank String id,
                                                  @RequestParam(defaultValue = "false") boolean download,
                                                  HttpServletRequest request,
                                                  HttpServletResponse response) {

    logger.info("Starting preview services comparison for file: {}", id);

    Map<String, Object> comparisonResult = new HashMap<>();

    // Test FileDBService
    long fileDBStartTime = System.currentTimeMillis();
    try {
      ResponseEntity<?> fileDBResult = fileDBService.handlePreviewFile(id, request, response, download);
      long fileDBDuration = System.currentTimeMillis() - fileDBStartTime;

      comparisonResult.put("fileDBService", Map.of(
          "duration", fileDBDuration,
          "status", fileDBResult.getStatusCode().value(),
          "success", fileDBResult.getStatusCode().is2xxSuccessful()
      ));

    } catch (Exception e) {
      long fileDBDuration = System.currentTimeMillis() - fileDBStartTime;
      comparisonResult.put("fileDBService", Map.of(
          "duration", fileDBDuration,
          "status", 500,
          "success", false,
          "error", e.getMessage()
      ));
    }

    // Test PreviewFileService
    long handleStartTime = System.currentTimeMillis();
    try {
      ResponseEntity<?> handleResult = fileDBService.handlePreviewFileOptimized(id, request, response, download);
      long handleDuration = System.currentTimeMillis() - handleStartTime;

      comparisonResult.put("previewFileService", Map.of(
          "duration", handleDuration,
          "status", handleResult.getStatusCode().value(),
          "success", handleResult.getStatusCode().is2xxSuccessful()
      ));

    } catch (Exception e) {
      long handleDuration = System.currentTimeMillis() - handleStartTime;
      comparisonResult.put("previewFileService", Map.of(
          "duration", handleDuration,
          "status", 500,
          "success", false,
          "error", e.getMessage()
      ));
    }

    // Determine winner
    Map<String, Object> fileDBStats = (Map<String, Object>) comparisonResult.get("fileDBService");
    Map<String, Object> handleStats = (Map<String, Object>) comparisonResult.get("previewFileService");

    long fileDBDuration = (Long) fileDBStats.get("duration");
    long handleDuration = (Long) handleStats.get("duration");
    boolean fileDBSuccess = (Boolean) fileDBStats.get("success");
    boolean handleSuccess = (Boolean) handleStats.get("success");

    String winner;
    if (fileDBSuccess && handleSuccess) {
      winner = fileDBDuration < handleDuration ? "FileDBService" : "PreviewFileService";
    } else if (fileDBSuccess) {
      winner = "FileDBService";
    } else if (handleSuccess) {
      winner = "PreviewFileService";
    } else {
      winner = "Both failed";
    }

    comparisonResult.put("winner", winner);
    comparisonResult.put("fileId", id);
    comparisonResult.put("download", download);

    logger.info("Preview services comparison completed for file: {}, winner: {}", id, winner);

    return ResponseEntity.ok(comparisonResult);
  }*/
}