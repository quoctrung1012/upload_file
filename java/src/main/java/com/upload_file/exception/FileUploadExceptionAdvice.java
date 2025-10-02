package com.upload_file.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FileUploadExceptionAdvice {

  private static final Logger logger = LoggerFactory.getLogger(FileUploadExceptionAdvice.class);

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<Map<String, Object>> handleMaxSizeException(MaxUploadSizeExceededException exc) {
    logger.warn("Multipart file upload too large", exc);
    return buildError(HttpStatus.EXPECTATION_FAILED, "File vượt quá giới hạn upload multipart!");
  }

  @ExceptionHandler(FileTooLargeException.class)
  public ResponseEntity<Map<String, Object>> handleCustomSizeException(FileTooLargeException exc) {
    logger.warn("Merged file quá lớn", exc);
    return buildError(HttpStatus.PAYLOAD_TOO_LARGE, "File quá lớn, vượt giới hạn cho phép khi xử lý!");
  }

  @ExceptionHandler(IOException.class)
  public ResponseEntity<Map<String, Object>> handleIOException(IOException exc) {
    logger.error("Lỗi IO khi xử lý file", exc);
    return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi đọc hoặc ghi file.");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleGenericException(Exception exc) {
    logger.error("Lỗi không xác định", exc);
    return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi không xác định.");
  }

  private ResponseEntity<Map<String, Object>> buildError(HttpStatus status, String message) {
    Map<String, Object> error = new HashMap<>();
    error.put("status", status.value());
    error.put("error", status.getReasonPhrase());
    error.put("message", message);
    return new ResponseEntity<>(error, status);
  }
}
