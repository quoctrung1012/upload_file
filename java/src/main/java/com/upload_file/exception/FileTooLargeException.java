package com.upload_file.exception;

import java.io.IOException;

public class FileTooLargeException extends IOException {
  public FileTooLargeException(String message) {
    super(message);
  }
}
