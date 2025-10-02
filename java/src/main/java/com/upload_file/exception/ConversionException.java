package com.upload_file.exception;

import lombok.Getter;

/**
 * Custom exception for document conversion errors
 */
public class ConversionException extends RuntimeException {

  private final String fileName;
  private final ConversionErrorType errorType;

  @Getter
  public enum ConversionErrorType {
    LIBREOFFICE_NOT_AVAILABLE("LibreOffice service is not available"),
    UNSUPPORTED_FORMAT("Document format is not supported for conversion"),
    CONVERSION_FAILED("Document conversion failed"),
    FILE_TOO_LARGE("File size exceeds conversion limit"),
    TIMEOUT("Conversion process timed out"),
    IO_ERROR("Input/Output error during conversion");

    private final String message;

    ConversionErrorType(String message) {
      this.message = message;
    }

  }

  public ConversionException(String fileName, ConversionErrorType errorType) {
    super(String.format("Conversion error for file '%s': %s", fileName, errorType.getMessage()));
    this.fileName = fileName;
    this.errorType = errorType;
  }

  public ConversionException(String fileName, ConversionErrorType errorType, Throwable cause) {
    super(String.format("Conversion error for file '%s': %s", fileName, errorType.getMessage()), cause);
    this.fileName = fileName;
    this.errorType = errorType;
  }

  public String getFileName() {
    return fileName;
  }

  public ConversionErrorType getErrorType() {
    return errorType;
  }
}