package com.upload_file.service;

import com.upload_file.common.Constants;
import com.upload_file.entity.FileDB;
import com.upload_file.service.abstract_file.AbstractFileService;
import io.micrometer.core.annotation.Timed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service điều phối preview file theo loại content
 */
@Service
public class FilePreviewService extends AbstractFileService {

  private static final Logger logger = LoggerFactory.getLogger(FilePreviewService.class);

  @Autowired
  private FileStorageService fileStorageService;

  @Autowired
  private FileStreamingService streamingService;

  @Autowired
  private TextFilePreviewService textPreviewService;

  @Autowired
  private OfficeFilePreviewService officePreviewService;

  @Autowired
  private PhysicalFileService physicalFileService;

  /**
   * Main preview method - điều phối theo loại file
   */
  @Timed(value = "file.preview", description = "Time taken to preview file")
  public ResponseEntity<?> previewFile(String id, HttpServletRequest request,
                                       HttpServletResponse response, boolean download) throws Exception {
    try {
      FileDB file = fileStorageService.getFile(id);
      String contentType = file.getType();

      logger.info("Previewing file: {} (type: {}, download: {})", file.getName(), contentType, download);

      // Nếu không phải download, xử lý preview đặc biệt
      if (!download) {
        ResponseEntity<?> previewResult = handleSpecialPreview(file, id, request, response);
        if (previewResult != null) {
          return previewResult;
        }
      }

      // Fallback to streaming
      return streamingService.streamFile(id, new HttpHeaders(), request, response);

    } catch (Exception e) {
      logger.error("Error previewing file with ID {}: {}", id, e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", "Cannot preview file: " + e.getMessage()));
    }
  }

  /**
   * Handle special preview cases (text, office, etc.)
   */
  private ResponseEntity<?> handleSpecialPreview(@NotNull FileDB file, String id,
                                                 HttpServletRequest request,
                                                 HttpServletResponse response) throws Exception {
    String contentType = file.getType();
    Constants.StorageLevel storageLevel = physicalFileService.determineStorageLevel(file);

    // Text file preview
    if ("text/plain".equals(contentType)) {
      return textPreviewService.handleTextFileStreaming(file, request, response);
    }
    if (storageLevel == Constants.StorageLevel.DATABASE && officePreviewService.isOfficeDocument(contentType)) {
      return officePreviewService.previewOfficeDocument(file, id);
    }
    if (storageLevel == Constants.StorageLevel.DATABASE && officePreviewService.isConvertibleDocument(contentType, file.getName())) {
      return officePreviewService.convertAndPreview(file, id, storageLevel, request, response);
    }

    return null;
  }
}