package com.upload_file.service.abstract_file;

import com.upload_file.common.UserIml;
import com.upload_file.dto.MergeRequest;
import com.upload_file.dto.ResponseFile;
import com.upload_file.entity.FileDB;
import com.upload_file.util.JwtUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractValidFile implements UserIml {
  private static final Logger logger = LoggerFactory.getLogger(AbstractValidFile.class);

  @Autowired
  private JwtUtil jwtUtil;

  /**
   * Kiểm tra token có hợp lệ không
   * @param token chuỗi token cần kiểm tra
   * @return true nếu token hợp lệ, false nếu không hợp lệ
   */
  protected boolean validateTokenAndAccess(String token, String fileUploadedBy) {
    try {
      if (!jwtUtil.validateToken(token)) {
        logger.warn("Invalid token provided");
        return false;
      }
      String usernameFromToken = jwtUtil.getUsernameFromToken(token);
      String roles = jwtUtil.getRolesFromToken(token);
      if (roles.contains("ROLE_ADMIN")) {
        logger.debug("Admin access granted for file: {}", fileUploadedBy);
        return true;
      }
      boolean canAccess = usernameFromToken.equals(fileUploadedBy);
      logger.debug("User {} access to file uploaded by {}: {}", usernameFromToken, fileUploadedBy, canAccess);

      return canAccess;

    } catch (Exception e) {
      logger.error("Token validation failed: {}", e.getMessage());
      return false;
    }
  }

  protected boolean validateChunkToken(String authHeader) {
    try {
      String jwtToken = null;

      // Nếu không có token parameter, lấy từ Authorization header
      if (authHeader != null && authHeader.startsWith("Bearer ")) {
        jwtToken = authHeader.substring(7);
      }

      if (jwtToken == null || jwtToken.isEmpty()) {
        logger.warn("No token provided for chunk upload");
        return true;
      }

      return !jwtUtil.validateToken(jwtToken);

    } catch (Exception e) {
      logger.error("Token validation failed for chunk upload: {}", e.getMessage());
      return true;
    }
  }

  // ============= UTILITY METHODS =============
  protected void validateFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("File cannot be empty");
    }

    if (file.getOriginalFilename() == null || file.getOriginalFilename().trim().isEmpty()) {
      throw new IllegalArgumentException("File name cannot be empty");
    }
  }

  protected long getFilesize(@NotNull FileDB fileDB) {
    long fileSize = 0;
    if (fileDB.getPath() != null && !fileDB.getPath().equals("(db)")) {
      fileSize = fileDB.getSize() != null ? fileDB.getSize() : 0;
    } else if (fileDB.getData() != null) {
      fileSize = fileDB.getData().length;
    }
    return fileSize;
  }

  protected ResponseFile getFileDB(@NotNull FileDB fileDB) {
    return new ResponseFile(
        fileDB.getId(),
        fileDB.getName(),
        fileDB.getType(),
        fileDB.getCreationDate(),
        getFilesize(fileDB)
    );
  }
  // =============== METHOD VALIDATE REQUEST ===============
  protected ResponseEntity<?> validateChunkRequest(MultipartFile file,
                                                   int chunkIndex,
                                                   int totalChunks,
                                                   String authHeader) {
    if (validateChunkToken(authHeader)) return createErrorResponse("Invalid or missing authentication token", HttpStatus.UNAUTHORIZED);
    if (file.isEmpty()) return createErrorResponse("Chunk is empty", HttpStatus.BAD_REQUEST);
    if (chunkIndex >= totalChunks) return createErrorResponse("Invalid chunk index", HttpStatus.BAD_REQUEST);
    return null;
  }
  protected ResponseEntity<?> validateMergeRequest(MergeRequest request,
                                                   String authHeader) {
    if (validateChunkToken(authHeader)) return createErrorResponse("Invalid or missing authentication token", HttpStatus.UNAUTHORIZED);
    if (request.totalChunks <= 0) return createErrorResponse("Invalid total chunks count", HttpStatus.BAD_REQUEST);
    if (request.filename == null || request.filename.trim().isEmpty()) return createErrorResponse("Filename cannot be empty", HttpStatus.BAD_REQUEST);
    return null;
  }

  // =============== METHOD CREATE ERROR RESPONSE ===============
  protected ResponseEntity<?> createErrorResponse(String errorMessage, HttpStatus status) {
    Map<String, Object> error = new HashMap<>();
    error.put("error", errorMessage);
    error.put("status", "failed");
    return ResponseEntity.status(status).body(error);
  }
  protected ResponseEntity<?> createChunkErrorResponse(String errorMessage, int chunkIndex, String filename) {
    Map<String, Object> error = new HashMap<>();
    error.put("error", errorMessage);
    error.put("chunkIndex", chunkIndex);
    error.put("filename", filename);
    error.put("status", "failed");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
  }
  protected ResponseEntity<?> createMergeErrorResponse(String errorMessage, String filename) {
    Map<String, Object> error = new HashMap<>();
    error.put("error", errorMessage);
    error.put("filename", filename);
    error.put("status", "failed");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
  }
  protected ResponseEntity<?> createChunkCheckErrorResponse(String filename, int chunkIndex) {
    Map<String, Object> error = new HashMap<>();
    error.put("error", "Failed to check chunk");
    error.put("filename", filename);
    error.put("chunkIndex", chunkIndex);
    error.put("status", "failed");

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
  }

  // =============== METHOD CREATE SUCCESS RESPONSE ===============
  protected ResponseEntity<?> createChunkSuccessResponse(int chunkIndex, int totalChunks, String filename) {
    Map<String, Object> response = new HashMap<>();
    response.put("message", String.format("Chunk %d received and processing", chunkIndex));
    response.put("chunkIndex", chunkIndex);
    response.put("totalChunks", totalChunks);
    response.put("filename", filename);
    response.put("status", "processing");
    return ResponseEntity.ok(response);
  }
  protected ResponseEntity<?> createMergeSuccessResponse(int totalChunks, String filename) {
    Map<String, Object> response = new HashMap<>();
    response.put("message", "File merged and saved successfully");
    response.put("filename", filename);
    response.put("totalChunks", totalChunks);
    response.put("status", "success");
    return ResponseEntity.ok(response);
  }
  protected ResponseEntity<?> createChunkCheckSuccessResponse(boolean exists, int chunkIndex, String filename) {
    Map<String, Object> response = new HashMap<>();
    response.put("exists", exists);
    response.put("filename", filename);
    response.put("chunkIndex", chunkIndex);
    response.put("status", "success");
    return ResponseEntity.ok(response);
  }
}
