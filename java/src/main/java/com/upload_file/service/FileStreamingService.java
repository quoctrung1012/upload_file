package com.upload_file.service;

import com.upload_file.common.Constants;
import com.upload_file.entity.FileDB;
import com.upload_file.service.abstract_file.AbstractStreamingService;
import io.micrometer.core.annotation.Timed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.util.Map;

/**
 * Service điều phối streaming dựa trên storage level - Sử dụng logic cũ đã hoạt động ổn định
 */
@Service
public class FileStreamingService extends AbstractStreamingService {

  private static final Logger logger = LoggerFactory.getLogger(FileStreamingService.class);

  @Autowired
  private FileStorageService fileStorageService;

  @Autowired
  private DatabaseStreamingService databaseStreaming;

  @Autowired
  private FileSystemStreamingService fileSystemStreaming;

  @Autowired
  private OneDriveStreamingService oneDriveStreaming;

  @Autowired
  private PhysicalFileService physicalFileService;

  /**
   * Main streaming method - Điều phối dựa trên storage level
   */
  @Timed(value = "file.stream", description = "Time taken to stream file")
  public ResponseEntity<?> streamFile(String id, HttpHeaders headers,
                                      HttpServletRequest request,
                                      HttpServletResponse response) throws Exception {
    try {
      FileDB file = fileStorageService.getFile(id);
      setCommonStreamingHeaders(response, file.getType(), file.getName(), false);

      // Sử dụng logic cũ để determine storage level
      Constants.StorageLevel level = determineStorageLevel(file);

      // COPY nguyên logic switch từ code cũ
      return switch (level) {
        case DATABASE -> databaseStreaming.stream(file, headers, response);
        case SYSTEM -> fileSystemStreaming.stream(file, headers, response);
        case ONEDRIVE -> oneDriveStreaming.stream(file, request, response);
        default -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", "Unknown storage level"));
      };

    } catch (FileNotFoundException e) {
      logger.error("File not found: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of("error", "File không tồn tại"));
    } catch (Exception e) {
      logger.error("Error streaming file: {}", e.getMessage(), e);
      if (isClientDisconnected(e)) {
        logger.info("Client đã ngắt kết nối stream file ID: {}", id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
      }
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", "Lỗi server"));
    }
  }

  /**
   * Xác định storage level - COPY từ FileDBService.determineStorageLevel() cũ
   */
  private Constants.StorageLevel determineStorageLevel(@NotNull FileDB fileDB) throws FileNotFoundException {
    if (fileDB.getSize() == null) {
      if (fileDB.getData() != null && fileDB.getData().length > 0) {
        return Constants.StorageLevel.DATABASE;
      } else {
        throw new FileNotFoundException("File data not found");
      }
    }
    return checkStorageLevel(fileDB.getSize());
  }
}