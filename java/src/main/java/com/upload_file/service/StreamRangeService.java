package com.upload_file.service;

import com.upload_file.common.Constants;
import com.upload_file.entity.FileDB;
import com.upload_file.service.abstract_file.AbstractFileService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Service
public class StreamRangeService extends AbstractFileService {

  private static final Logger logger = LoggerFactory.getLogger(StreamRangeService.class);

  @Value("${app.streaming.timeout:30000}")
  private int streamingTimeout;

  @Autowired
  OneDriveService oneDriveService;

  // Helper method để parse range header
  protected HttpRange parseRangeHeader(String rangeHeader, long fileSize) {
    if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) {
      return HttpRange.createByteRange(0, fileSize - 1);
    }

    String rangeValue = rangeHeader.substring(6); // Remove "bytes="
    String[] parts = rangeValue.split("-");

    long start = 0;
    long end = fileSize - 1;

    if (parts.length > 0 && !parts[0].isEmpty()) {
      start = Long.parseLong(parts[0]);
    }

    if (parts.length > 1 && !parts[1].isEmpty()) {
      end = Long.parseLong(parts[1]);
    }

    return HttpRange.createByteRange(start, end);
  }

  // =============== Method stream range video ===============

  protected ResponseEntity<?> streamFileFromDatabase(@NotNull FileDB file, HttpHeaders headers,
                                                     HttpServletResponse response) throws IOException {
    byte[] fileData = file.getData();
    if (fileData == null || fileData.length == 0) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of("error", "Dữ liệu file không tồn tại"));
    }

    long fileLength = fileData.length;
    List<HttpRange> ranges = headers.getRange();

    try {
      if (ranges.isEmpty()) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(fileData)) {
          return streamFullContent(inputStream, fileLength, response);
        }
      } else {
        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(fileLength);
        long end = range.getRangeEnd(fileLength);

        StreamDataProvider dataProvider = (outputStream, startPos, length) -> {
          streamBufferedData(fileData, outputStream, startPos, length);
        };

        return streamPartialContent(start, end, fileLength, response, dataProvider);
      }
    } catch (Exception e) {
      if (isClientDisconnected(e)) {
        logger.info("Client ngắt kết nối trong quá trình stream từ database");
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
      }
      throw e;
    }
  }

  protected ResponseEntity<?> streamFileFromFileSystem(@NotNull FileDB file, HttpHeaders headers,
                                                       HttpServletResponse response) throws IOException {
    Path filePath = Paths.get(file.getPath());
    if (!Files.exists(filePath)) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of("error", "File không tồn tại trên filesystem"));
    }
    return handleRangeRequest(filePath, headers.getRange(), response);
  }

  protected ResponseEntity<?> streamFileFromOneDrive(String fileName, String disposition,
                                                     MediaType mediaType, String rangeHeader,
                                                     HttpServletResponse response) {
    InputStream inputStream = null;

    try {
      if (response.isCommitted()) {
        logger.warn("Response already committed for file: {}", fileName);
        return null;
      }

      inputStream = oneDriveService.streamFileWithRange(fileName, rangeHeader);

      response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition);
      response.setHeader(HttpHeaders.CONTENT_TYPE, mediaType.toString());
      response.setHeader("Accept-Ranges", "bytes");
      response.setHeader("Cache-Control", "public, max-age=3600");
      response.setHeader("Connection", "close");
      response.setHeader("Keep-Alive", "timeout=" + (streamingTimeout / 1000));

      try (ServletOutputStream outputStream = response.getOutputStream();
           BufferedInputStream bufferedInput = new BufferedInputStream(inputStream, Constants.STREAM_BUFFER_SIZE)) {

        byte[] buffer = new byte[Constants.STREAM_BUFFER_SIZE];
        int bytesRead;
        long totalBytesRead = 0;

        while ((bytesRead = bufferedInput.read(buffer)) != -1) {
          try {
            outputStream.write(buffer, 0, bytesRead);
            totalBytesRead += bytesRead;

            if (totalBytesRead % Constants.FLUSH_INTERVAL == 0) {
              outputStream.flush();
            }
          } catch (IOException e) {
            if (isClientDisconnected(e)) {
              logger.debug("Client disconnected while streaming file: {} after {} bytes",
                  fileName, totalBytesRead);
              break;
            }
            throw e;
          }
        }

        if (!response.isCommitted()) {
          outputStream.flush();
        }

        logger.debug("Successfully streamed {} bytes for file: {}", totalBytesRead, fileName);
        return ResponseEntity.ok().build();

      } catch (IOException e) {
        if (isClientDisconnected(e)) {
          logger.debug("Client disconnected during stream setup for file: {}", fileName);
          return null;
        }
        throw e;
      }

    } catch (Exception e) {
      logger.error("Error streaming OneDrive file: {} - {}", fileName, e.getMessage(), e);

      if (!response.isCommitted()) {
        try {
          response.resetBuffer();
          return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("error", "Error streaming file: " + e.getMessage()));
        } catch (Exception resetException) {
          logger.error("Failed to reset response buffer: {}", resetException.getMessage());
          return null;
        }
      }
      return null;

    } finally {
      if (inputStream != null) {
        try {
          inputStream.close();
        } catch (IOException e) {
          logger.warn("Error closing input stream for file: {} - {}", fileName, e.getMessage());
        }
      }
    }
  }

  protected ResponseEntity<?> streamVideoFromOneDriveWithRange(String fileName, String disposition,
                                                               MediaType mediaType, String rangeHeader,
                                                               HttpServletResponse response, FileDB fileDB) {
    InputStream inputStream = null;

    try {
      if (response.isCommitted()) {
        logger.warn("Response already committed for video file: {}", fileName);
        return null;
      }

      // Lấy file size với timeout handling
      long fileSize;
      if (fileDB.getSize() != null) {
        fileSize = fileDB.getSize();
      } else {
        try {
          fileSize = oneDriveService.getFileSize(fileName);
        } catch (Exception e) {
          logger.error("Failed to get file size from OneDrive: {}", e.getMessage());
          // Fallback to non-range streaming
          return streamFileFromOneDrive(fileName, disposition, mediaType, null, response);
        }
      }

      // Parse range header
      HttpRange range = parseRangeHeader(rangeHeader, fileSize);
      long start = range.getRangeStart(fileSize);
      long end = range.getRangeEnd(fileSize);
      long contentLength = end - start + 1;

      // Giới hạn chunk size cho video streaming để tránh timeout
      long maxChunkSize = Constants.MAX_DB_SIZE; // 10MB
      if (contentLength > maxChunkSize) {
        end = start + maxChunkSize - 1;
        contentLength = maxChunkSize;
      }

      // Set response headers for partial content
      response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
      response.setHeader(HttpHeaders.CONTENT_TYPE, mediaType.toString());
      response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength));
      response.setHeader(HttpHeaders.CONTENT_RANGE, String.format("bytes %d-%d/%d", start, end, fileSize));
      response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition);
      response.setHeader("Accept-Ranges", "bytes");
      response.setHeader("Cache-Control", "public, max-age=3600");

      // Tạo range header cho OneDrive request
      String oneDriveRangeHeader = String.format("bytes=%d-%d", start, end);

      try {
        inputStream = oneDriveService.streamFileWithRange(fileName, oneDriveRangeHeader);
      } catch (Exception e) {
        logger.error("Failed to get OneDrive stream: {}", e.getMessage());
        if (!response.isCommitted()) {
          response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return null;
      }

      // Stream data với timeout protection
      try (ServletOutputStream outputStream = response.getOutputStream();
           BufferedInputStream bufferedInput = new BufferedInputStream(inputStream, Constants.STREAM_BUFFER_SIZE)) {

        byte[] buffer = new byte[Constants.STREAM_BUFFER_SIZE];
        int bytesRead;
        long totalBytesWritten = 0;
        long startTime = System.currentTimeMillis();

        while ((bytesRead = bufferedInput.read(buffer)) != -1 && totalBytesWritten < contentLength) {
          // Check timeout
          if (System.currentTimeMillis() - startTime > streamingTimeout) {
            logger.warn("Streaming timeout for video file: {}", fileName);
            break;
          }

          try {
            int bytesToWrite = (int) Math.min(bytesRead, contentLength - totalBytesWritten);
            outputStream.write(buffer, 0, bytesToWrite);
            totalBytesWritten += bytesToWrite;

            if (totalBytesWritten % Constants.FLUSH_INTERVAL == 0) {
              outputStream.flush();
            }
          } catch (IOException e) {
            if (isClientDisconnected(e)) {
              logger.debug("Client disconnected while streaming video: {} after {} bytes",
                  fileName, totalBytesWritten);
              break;
            }
            throw e;
          }
        }

        if (!response.isCommitted()) {
          outputStream.flush();
        }

        logger.debug("Successfully streamed {} bytes (range {}-{}) for video file: {}",
            totalBytesWritten, start, end, fileName);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).build();

      } catch (IOException e) {
        if (isClientDisconnected(e)) {
          logger.debug("Client disconnected during video stream setup for file: {}", fileName);
          return null;
        }
        throw e;
      }

    } catch (Exception e) {
      logger.error("Error streaming video file with range: {} - {}", fileName, e.getMessage(), e);

      if (!response.isCommitted()) {
        try {
          response.resetBuffer();
          response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
          return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("error", "Error streaming video file: " + e.getMessage()));
        } catch (Exception resetException) {
          logger.error("Failed to reset response buffer: {}", resetException.getMessage());
          return null;
        }
      }
      return null;

    } finally {
      if (inputStream != null) {
        try {
          inputStream.close();
        } catch (IOException e) {
          logger.warn("Error closing input stream for video file: {} - {}", fileName, e.getMessage());
        }
      }
    }
  }

  // =============== Method stream range text/plain ===============
  protected ResponseEntity<?> handleTextFileStreaming(FileDB fileDB,
                                                      @NotNull HttpServletRequest request,
                                                      HttpServletResponse response) throws IOException {
    String rangeHeader = request.getHeader("Range");

    if (rangeHeader != null) {
      // Streaming với range support cho large text files
      return handleTextFileWithRange(fileDB, rangeHeader, response);
    } else {
      // Streaming toàn bộ file nhưng với buffer
      return streamTextFileBuffered(fileDB, response);
    }
  }

  private ResponseEntity<?> handleTextFileWithRange(FileDB fileDB, String rangeHeader,
                                                    HttpServletResponse response) throws IOException {
    long fileSize = fileDB.getSize();
    HttpRange range = parseRangeHeader(rangeHeader, fileSize);
    long start = range.getRangeStart(fileSize);
    long end = range.getRangeEnd(fileSize);

    // SỬA: Giảm chunk size cho text file lớn (từ 1MB xuống 256KB)
    long maxChunkSize = 256 * 1024; // 256KB thay vì 1MB
    if (end - start + 1 > maxChunkSize) {
      end = start + maxChunkSize - 1;
    }

    long contentLength = end - start + 1;

    response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
    response.setHeader(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8");
    response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength));
    response.setHeader(HttpHeaders.CONTENT_RANGE, String.format("bytes %d-%d/%d", start, end, fileSize));
    response.setHeader("Accept-Ranges", "bytes");
    response.setHeader("Cache-Control", "no-cache"); // Thêm no-cache

    try (InputStream inputStream = getInputStreamByStorageLevel(fileDB, start, end);
         ServletOutputStream outputStream = response.getOutputStream()) {

      byte[] buffer = new byte[4096]; // Giảm buffer size từ 8192 xuống 4096
      int bytesRead;
      long totalWritten = 0;

      while ((bytesRead = inputStream.read(buffer)) != -1 && totalWritten < contentLength) {
        int bytesToWrite = (int) Math.min(bytesRead, contentLength - totalWritten);
        outputStream.write(buffer, 0, bytesToWrite);
        totalWritten += bytesToWrite;

        // Flush thường xuyên hơn
        if (totalWritten % 2048 == 0) {
          outputStream.flush();
        }
      }

      outputStream.flush();
    }

    return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).build();
  }

  private InputStream getInputStreamByStorageLevel(FileDB fileDB, long start, long end) throws IOException {
    Constants.StorageLevel level = checkStorageLevel(fileDB.getSize());

    switch (level) {
      case ONEDRIVE:
        String rangeHeader = String.format("bytes=%d-%d", start, end);
        return oneDriveService.streamFileWithRange(fileDB.getName(), rangeHeader);

      case SYSTEM:
        Path filePath = Paths.get(fileDB.getPath());
        FileInputStream fis = new FileInputStream(filePath.toFile());
        if (start > 0) {
          long skipped = fis.skip(start);
          if (skipped != start) {
            fis.close();
            throw new IOException("Could not skip to start position: " + start);
          }
        }
        return new BoundedInputStream(fis, end - start + 1);

      case DATABASE:
        byte[] data = fileDB.getData();
        int startInt = (int) Math.min(start, data.length);
        int endInt = (int) Math.min(end + 1, data.length);
        int length = endInt - startInt;
        return new ByteArrayInputStream(data, startInt, length);

      default:
        throw new IOException("Unknown storage level: " + level);
    }
  }

  private ResponseEntity<?> streamTextFileBuffered(FileDB fileDB, HttpServletResponse response) throws IOException {
    Constants.StorageLevel level = checkStorageLevel(fileDB.getSize());

    response.setHeader(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8");
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodeFilenameForHeader(fileDB.getName()));
    response.setHeader("Accept-Ranges", "bytes");
    response.setHeader("Cache-Control", "no-cache"); // Đổi từ public, max-age=3600 sang no-cache cho file lớn

    // SỬA: Chỉ stream một phần đầu file cho preview thay vì toàn bộ
    long maxPreviewSize = 1024 * 1024; // 1MB cho preview
    long actualStreamSize = Math.min(fileDB.getSize(), maxPreviewSize);

    response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(actualStreamSize));

    try (InputStream inputStream = getLimitedInputStreamByStorageLevel(fileDB, level, actualStreamSize);
         ServletOutputStream outputStream = response.getOutputStream();
         BufferedInputStream bufferedInput = new BufferedInputStream(inputStream, 8192)) { // Giảm buffer size

      byte[] buffer = new byte[8192]; // Giảm buffer size từ STREAM_BUFFER_SIZE
      int bytesRead;
      long totalBytesRead = 0;

      while ((bytesRead = bufferedInput.read(buffer)) != -1 && totalBytesRead < actualStreamSize) {
        try {
          int bytesToWrite = (int) Math.min(bytesRead, actualStreamSize - totalBytesRead);
          outputStream.write(buffer, 0, bytesToWrite);
          totalBytesRead += bytesToWrite;

          // Flush thường xuyên hơn cho file lớn
          if (totalBytesRead % 4096 == 0) { // Giảm flush interval
            outputStream.flush();
          }
        } catch (IOException e) {
          if (isClientDisconnected(e)) {
            logger.debug("Client disconnected while streaming text file: {} after {} bytes",
                fileDB.getName(), totalBytesRead);
            break;
          }
          throw e;
        }
      }

      if (!response.isCommitted()) {
        outputStream.flush();
      }

      logger.debug("Successfully streamed {} bytes (limited preview) for text file: {}", totalBytesRead, fileDB.getName());
      return ResponseEntity.ok().build();

    } catch (Exception e) {
      if (isClientDisconnected(e)) {
        logger.debug("Client disconnected during text file stream setup: {}", fileDB.getName());
        return null;
      }
      throw e;
    }
  }

  private InputStream getLimitedInputStreamByStorageLevel(FileDB fileDB, Constants.StorageLevel level, long maxBytes) throws IOException {
    return switch (level) {
      case ONEDRIVE -> {
        String rangeHeader = String.format("bytes=0-%d", maxBytes - 1);
        yield oneDriveService.streamFileWithRange(fileDB.getName(), rangeHeader);
      }
      case SYSTEM -> {
        Path filePath = Paths.get(fileDB.getPath());
        FileInputStream fis = new FileInputStream(filePath.toFile());
        yield new BoundedInputStream(fis, maxBytes);
      }
      case DATABASE -> {
        byte[] data = fileDB.getData();
        int length = (int) Math.min(data.length, maxBytes);
        yield new ByteArrayInputStream(data, 0, length);
      }
      default -> throw new IOException("Unknown storage level: " + level);
    };
  }

  protected static class BoundedInputStream extends FilterInputStream {
    private long remaining;

    protected BoundedInputStream(InputStream in, long maxBytes) {
      super(in);
      this.remaining = maxBytes;
    }

    @Override
    public int read() throws IOException {
      if (remaining <= 0) {
        return -1;
      }
      int result = super.read();
      if (result != -1) {
        remaining--;
      }
      return result;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      if (remaining <= 0) {
        return -1;
      }
      len = (int) Math.min(len, remaining);
      int result = super.read(b, off, len);
      if (result > 0) {
        remaining -= result;
      }
      return result;
    }
  }
}
