package com.upload_file.controller;

import com.upload_file.common.Constants;
import com.upload_file.common.UserIml;
import com.upload_file.dto.MergeRequest;
import com.upload_file.dto.ResponseFile;
import com.upload_file.dto.ResponsePage;
import com.upload_file.dto.ResponseResult;
import com.upload_file.entity.FileDB;
import com.upload_file.service.*;
import com.upload_file.service.abstract_file.AbstractValidFile;
import io.micrometer.core.annotation.Timed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

/**
 * Simplified FileController - chỉ xử lý HTTP requests và validation
 * Business logic được delegate đến các specialized services
 */
@RestController
@RequestMapping("/files")
@Validated
public class FileController extends AbstractValidFile implements UserIml {

  private static final Logger logger = LoggerFactory.getLogger(FileController.class);

  @Autowired
  private FileUploadService fileUploadService;

  @Autowired
  private FileStorageService fileStorageService;

  @Autowired
  private FilePreviewService filePreviewService;

  @Autowired
  private ChunkUploadService chunkUploadService;

  // ============= UPLOAD ENDPOINTS =============

  /**
   * Upload single file
   */
  @PostMapping("/upload")
  @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
  @Timed(value = "file.upload", description = "Time taken for single file upload")
  public ResponseEntity<ResponseResult> uploadFile(@RequestParam("file") @NotNull MultipartFile file) {
    String currentUser = getCurrentUsername();
    logger.info("User '{}' starting single file upload: {} (size: {} bytes)",
        currentUser, file.getOriginalFilename(), file.getSize());

    try {
      // Validate file
      validateFile(file);

      // Delegate to upload service
      fileUploadService.uploadSingleFile(file);

      String message = String.format("User '%s' successfully uploaded file: %s (%.2f MB)",
          currentUser, file.getOriginalFilename(), file.getSize() / (1024.0 * 1024.0));

      logger.info("File upload completed successfully by user '{}': {}", currentUser, file.getOriginalFilename());
      return ResponseEntity.ok(new ResponseResult(message, Constants.SUCCESS));

    } catch (IllegalArgumentException e) {
      logger.warn("Invalid file upload request by user '{}': {}", currentUser, e.getMessage());
      return ResponseEntity.badRequest()
          .body(new ResponseResult("Invalid file: " + e.getMessage(), Constants.ERROR));

    } catch (Exception e) {
      logger.error("Error uploading file {} by user '{}': {}", file.getOriginalFilename(), currentUser, e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(new ResponseResult("Upload failed: " + e.getMessage(), Constants.ERROR));
    }
  }

  /**
   * Upload multiple files
   */
  @PostMapping("/uploads")
  @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
  @Timed(value = "file.uploads", description = "Time taken for multiple files upload")
  public ResponseEntity<ResponseResult> uploadMultipleFiles(@RequestParam("files") @NotNull MultipartFile[] files) {
    String currentUser = getCurrentUsername();
    logger.info("User '{}' starting multiple files upload: {} files", currentUser, files.length);

    try {
      // Validate all files first
      for (MultipartFile file : files) {
        validateFile(file);
      }

      // Delegate to upload service
      fileUploadService.uploadMultipleFiles(files);

      String message = String.format("User '%s' successfully uploaded %d files",
          currentUser, files.length);

      logger.info("Multiple files upload completed by user '{}': {} files", currentUser, files.length);
      return ResponseEntity.ok(new ResponseResult(message, Constants.SUCCESS));

    } catch (IllegalArgumentException e) {
      logger.warn("Invalid multiple files upload request by user '{}': {}", currentUser, e.getMessage());
      return ResponseEntity.badRequest()
          .body(new ResponseResult("Invalid files: " + e.getMessage(), Constants.ERROR));

    } catch (Exception e) {
      logger.error("Error uploading multiple files by user '{}': {}", currentUser, e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(new ResponseResult("Failed to upload files: " + e.getMessage(), Constants.ERROR));
    }
  }

  // ============= FILE LISTING =============

  /**
   * Get paginated list of files with search functionality
   */
  @GetMapping("/list")
  @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
  @Timed(value = "file.list", description = "Time taken to list files")
  public ResponseEntity<?> getListFiles(@RequestParam(defaultValue = "") String search,
                                        @RequestParam(defaultValue = "0") @Min(0) Integer page,
                                        @RequestParam(defaultValue = "10") @Min(1) Integer size) {
    String currentUser = getCurrentUsername();
    logger.debug("Getting file list - search: '{}', page: {}, size: {}", search, page, size);

    try {
      Pageable pageable = PageRequest.of(page, size);
      Page<FileDB> filesPage = fileStorageService.getAllFiles(search, pageable, currentUser, isAdmin());
      Page<ResponseFile> responseFiles = filesPage.map(this::getFileDB);

      ResponsePage responsePage = new ResponsePage(
          responseFiles.getContent(),
          responseFiles.getNumber(),
          responseFiles.getTotalElements(),
          responseFiles.getTotalPages()
      );

      logger.debug("Retrieved {} files (page {}/{})",
          responseFiles.getNumberOfElements(),
          responseFiles.getNumber() + 1,
          responseFiles.getTotalPages());
      return ResponseEntity.ok(responsePage);

    } catch (Exception e) {
      logger.error("Error retrieving file list: {}", e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(new ResponsePage(Collections.emptyList(), 0, 0L, 0));
    }
  }

  // ============= PREVIEW/DOWNLOAD ENDPOINTS =============

  /**
   * Preview or download file
   */
  @GetMapping("/preview")
  @Timed(value = "file.preview", description = "Time taken to preview/download file")
  public ResponseEntity<?> previewFile(@RequestParam @NotBlank String id,
                                       @RequestParam(defaultValue = "false") boolean download,
                                       @RequestParam(required = false) String token,
                                       HttpServletRequest request,
                                       HttpServletResponse response) {

    logger.info("File preview request: id={}, download={}", id, download);

    // Set CORS headers
    setCorsHeaders(response);

    try {
      FileDB fileDB = fileStorageService.getFile(id);

      // Check access permissions
      if (token != null && !token.isEmpty()) {
        if (!validateTokenAndAccess(token, fileDB.getUploadedBy())) {
          return ResponseEntity.status(HttpStatus.FORBIDDEN)
              .body(new ResponseResult("Access denied", Constants.ERROR));
        }
      } else {
        if (!canAccessFile(fileDB.getUploadedBy())) {
          return ResponseEntity.status(HttpStatus.FORBIDDEN)
              .body(new ResponseResult("Access denied", Constants.ERROR));
        }
      }

      // Delegate to preview service
      return filePreviewService.previewFile(id, request, response, download);

    } catch (Exception e) {
      logger.error("Error handling file preview for ID {}: {}", id, e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(new ResponseResult("File operation failed: " + e.getMessage(), Constants.ERROR));
    }
  }

  /**
   * Handle OPTIONS request for preview endpoint
   */
  @RequestMapping(value = "/preview", method = RequestMethod.OPTIONS)
  public ResponseEntity<?> handlePreviewOptions(HttpServletResponse response) {
    setCorsHeaders(response);
    return ResponseEntity.ok().build();
  }

  // ============= DELETE ENDPOINT =============

  /**
   * Delete file by ID
   */
  @GetMapping("/delete")
  @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
  @Timed(value = "file.delete", description = "Time taken to delete file")
  public ResponseEntity<ResponseResult> deleteFile(@RequestParam @NotBlank String id) {
    String currentUser = getCurrentUsername();
    logger.info("User '{}' starting file deletion for ID: {}", currentUser, id);

    try {
      // Check permissions
      FileDB fileDB = fileStorageService.getFile(id);
      if (!canDeleteFile(fileDB.getUploadedBy())) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ResponseResult("Access denied", Constants.ERROR));
      }

      // Delegate to upload service (which handles deletion)
      fileUploadService.deleteFile(id);

      String message = String.format("User '%s' successfully deleted file with ID: %s", currentUser, id);
      logger.info("File deletion completed by user '{}' for ID: {}", currentUser, id);

      return ResponseEntity.ok(new ResponseResult(message, Constants.SUCCESS));

    } catch (Exception e) {
      logger.error("Error deleting file with ID {} by user '{}': {}", id, currentUser, e.getMessage(), e);
      String message = String.format("User '%s' failed to delete file with ID: %s. Error: %s",
          currentUser, id, e.getMessage());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(new ResponseResult(message, Constants.ERROR));
    }
  }

  // ============= CHUNK UPLOAD ENDPOINTS =============

  /**
   * Upload file chunk
   */
  @PostMapping("/chunk")
  @Timed(value = "file.chunk", description = "Time taken to upload chunk")
  public ResponseEntity<?> uploadChunk(@RequestParam("file") @NotNull MultipartFile file,
                                       @RequestParam("filename") @NotBlank String filename,
                                       @RequestParam("chunkIndex") @Min(0) int chunkIndex,
                                       @RequestParam("totalChunks") @Min(1) int totalChunks,
                                       @RequestHeader(value = "Authorization", required = false) String authHeader) {

    logger.debug("Uploading chunk {}/{} for file: {}", chunkIndex + 1, totalChunks, filename);

    try {
      ResponseEntity<?> validationError = validateChunkRequest(file, chunkIndex, totalChunks, authHeader);
      if (validationError != null) return validationError;

      // Save chunk asynchronously
      CompletableFuture<Void> future = chunkUploadService.saveChunkAsync(authHeader, file, filename, chunkIndex);
      future.get(); // Wait for completion

      logger.info("Chunk {}/{} uploaded successfully for file: {}", chunkIndex + 1, totalChunks, filename);
      return createChunkSuccessResponse(chunkIndex, totalChunks, filename);

    } catch (Exception e) {
      logger.error("Error uploading chunk {}/{} for file {}: {}", chunkIndex + 1, totalChunks, filename, e.getMessage(), e);
      return createChunkErrorResponse("Chunk upload failed: " + e.getMessage(), chunkIndex, filename);
    }
  }

  /**
   * Merge uploaded chunks into final file
   */
  @PostMapping("/merge")
  @Timed(value = "file.merge", description = "Time taken to merge chunks")
  public ResponseEntity<?> mergeChunks(@RequestBody @NotNull MergeRequest request,
                                       @RequestHeader(value = "Authorization", required = false) String authHeader) {
    logger.info("Starting chunk merge for file: {} ({} chunks)", request.filename, request.totalChunks);

    try {
      ResponseEntity<?> validationError = validateMergeRequest(request, authHeader);
      if (validationError != null) return validationError;

      // Delegate to chunk service
      chunkUploadService.mergeChunksAndSave(authHeader, request.filename, request.totalChunks, request.type);

      logger.info("Chunk merge completed successfully for file: {}", request.filename);
      return createMergeSuccessResponse(request.totalChunks, request.filename);

    } catch (Exception e) {
      logger.error("Error merging chunks for file {}: {}", request.filename, e.getMessage(), e);
      return createMergeErrorResponse("Merge failed: " + e.getMessage(), request.filename);
    }
  }

  /**
   * Handle OPTIONS request for merge endpoint
   */
  @RequestMapping(value = "/merge", method = RequestMethod.OPTIONS)
  public ResponseEntity<?> handleMergeOptions() {
    return ResponseEntity.ok().build();
  }

  /**
   * Check if specific chunk exists
   */
  @GetMapping("/chunk/check")
  @Timed(value = "file.chunk_check", description = "Time taken to check chunk existence")
  public ResponseEntity<?> checkChunk(@RequestParam("filename") @NotBlank String filename,
                                      @RequestParam("chunkIndex") @Min(0) int chunkIndex,
                                      @RequestHeader(value = "Authorization", required = false) String authHeader) {

    logger.debug("Checking chunk existence: {} index {}", filename, chunkIndex);

    try {
      boolean exists = chunkUploadService.chunkExists(authHeader, filename, chunkIndex);
      return createChunkCheckSuccessResponse(exists, chunkIndex, filename);

    } catch (Exception e) {
      logger.error("Error checking chunk {}/{}: {}", filename, chunkIndex, e.getMessage());
      return createChunkCheckErrorResponse(filename, chunkIndex);
    }
  }

  // ============= FILE INFORMATION =============

  /**
   * Get file metadata by ID
   */
  @GetMapping("/info")
  @Timed(value = "file.info", description = "Time taken to get file info")
  public ResponseEntity<?> getFileInfo(@RequestParam @NotBlank String id) {
    logger.debug("Getting file info for ID: {}", id);

    try {
      FileDB fileDB = fileStorageService.getFile(id);
      return ResponseEntity.ok(getFileDB(fileDB));

    } catch (Exception e) {
      logger.error("Error getting file info for ID {}: {}", id, e.getMessage());
      return ResponseEntity.notFound().build();
    }
  }

  // ============= UTILITY METHODS =============

  /**
   * Set CORS headers for browser compatibility
   */
  private void setCorsHeaders(HttpServletResponse response) {
    response.setHeader("Access-Control-Allow-Origin", "*");
    response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
    response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    response.setHeader("Access-Control-Expose-Headers", "Content-Disposition, Content-Type, Content-Length");
    response.setHeader("X-Frame-Options", "ALLOW-FROM *");
    response.setHeader("Content-Security-Policy", "frame-ancestors *");
  }
}