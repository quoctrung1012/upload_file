package com.upload_file.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

@ControllerAdvice
public class ConversionExceptionHandler {

  private static final Logger logger = LoggerFactory.getLogger(ConversionExceptionHandler.class);

  @ExceptionHandler(ConversionException.class)
  public ResponseEntity<Map<String, Object>> handleConversionException(ConversionException e) {
    logger.error("Document conversion failed: {}", e.getMessage(), e);

    HttpStatus status = switch (e.getErrorType()) {
      case LIBREOFFICE_NOT_AVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
      case UNSUPPORTED_FORMAT -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
      case FILE_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
      case TIMEOUT -> HttpStatus.REQUEST_TIMEOUT;
      default -> HttpStatus.INTERNAL_SERVER_ERROR;
    };

    Map<String, Object> errorResponse = Map.of(
        "error", e.getErrorType().getMessage(),
        "fileName", e.getFileName(),
        "errorType", e.getErrorType().name(),
        "timestamp", System.currentTimeMillis()
    );

    return ResponseEntity.status(status).body(errorResponse);
  }
}