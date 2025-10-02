package com.upload_file.service.abstract_file;

import com.upload_file.common.Constants;
import com.upload_file.entity.FileDB;
import io.micrometer.core.annotation.Timed;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.List;

public abstract class AbstractFileService {
  private static final Logger logger = LoggerFactory.getLogger(AbstractFileService.class);

  public String currentTimeCreate() {
    return String.valueOf(System.currentTimeMillis());
  }

  public Constants.StorageLevel checkStorageLevel(long fileSize) {
    if (fileSize <= Constants.MAX_DB_SIZE) {
      return Constants.StorageLevel.DATABASE;
    } else if (fileSize <= Constants.MAX_SYSTEM_SIZE) {
      return Constants.StorageLevel.SYSTEM;
    } else {
      return Constants.StorageLevel.ONEDRIVE;
    }
  }

  // Common streaming methods
  @Timed(value = "file.stream_full_content", description = "Time taken to stream full content")
  protected ResponseEntity<?> streamFullContent(InputStream inputStream, long contentLength,
                                                HttpServletResponse response) throws IOException {
    response.setStatus(HttpStatus.OK.value());
    response.setHeader("Content-Length", String.valueOf(contentLength));

    try (BufferedInputStream bufferedInput = new BufferedInputStream(inputStream, Constants.STREAM_BUFFER_SIZE);
         ServletOutputStream outputStream = response.getOutputStream()) {
      streamData(bufferedInput, outputStream, 0, contentLength);
      outputStream.flush();
      return ResponseEntity.ok().build();
    }
  }

  @Timed(value = "file.stream_data", description = "Time taken to stream data")
  protected void streamData(InputStream inputStream, ServletOutputStream outputStream,
                            long skip, long length) throws IOException {
    if (skip > 0) {
      inputStream.skip(skip);
    }

    byte[] buffer = new byte[Constants.STREAM_BUFFER_SIZE];
    long bytesRead = 0;
    int read;

    while (bytesRead < length && (read = inputStream.read(buffer, 0, (int) Math.min(buffer.length, length - bytesRead))) != -1) {
      try {
        outputStream.write(buffer, 0, read);
        bytesRead += read;

        if (bytesRead % Constants.FLUSH_INTERVAL == 0) {
          outputStream.flush();
        }
      } catch (IOException e) {
        if (isClientDisconnected(e)) {
          logger.info("Client ngắt kết nối trong quá trình stream data");
          break;
        }
        throw e;
      }
    }
  }

  @Timed(value = "file.stream_data_random_access", description = "Time taken to stream data from random access file")
  private void streamDataFromRandomAccess(RandomAccessFile randomAccessFile,
                                            ServletOutputStream outputStream, long length) throws IOException {
    byte[] buffer = new byte[Constants.STREAM_BUFFER_SIZE];
    long bytesRead = 0;
    int read;

    while (bytesRead < length && (read = randomAccessFile.read(buffer, 0,
        (int) Math.min(buffer.length, length - bytesRead))) != -1) {
      try {
        outputStream.write(buffer, 0, read);
        bytesRead += read;

        if (bytesRead % Constants.FLUSH_INTERVAL == 0) {
          outputStream.flush();
        }
      } catch (IOException e) {
        if (isClientDisconnected(e)) {
          logger.info("Client ngắt kết nối trong quá trình stream partial data");
          break;
        }
        throw e;
      }
    }
  }

  protected ResponseEntity<?> streamPartialContent(long start, long end, long totalLength,
                                                   HttpServletResponse response,
                                                   StreamDataProvider dataProvider) throws IOException {
    long rangeLength = end - start + 1;

    response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
    response.setHeader("Content-Range", String.format("bytes %d-%d/%d", start, end, totalLength));
    response.setHeader("Content-Length", String.valueOf(rangeLength));

    try (ServletOutputStream outputStream = response.getOutputStream()) {
      dataProvider.streamData(outputStream, start, rangeLength);
      outputStream.flush();
      return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).build();
    }
  }

  @FunctionalInterface
  protected interface StreamDataProvider {
    void streamData(ServletOutputStream outputStream, long start, long length) throws IOException;
  }

  protected void streamBufferedData(byte[] buffer, ServletOutputStream outputStream,
                                    long start, long length) throws IOException {
    long bytesToWrite = Math.min(length, buffer.length - start);
    if (bytesToWrite > 0) {
      outputStream.write(buffer, (int) start, (int) bytesToWrite);
    }
  }

  protected boolean isClientDisconnected(Exception e) {
    if (e == null) return false;

    // Kiểm tra các exception types đặc trưng của client disconnect
    if (e instanceof ClientAbortException ||
        e instanceof AsyncRequestNotUsableException) {
      return true;
    }

    String message = e.getMessage();
    if (message == null) return false;

    // Normalize message để so sánh
    String normalizedMessage = message.toLowerCase();

    return normalizedMessage.contains("connection reset by peer") ||
        normalizedMessage.contains("broken pipe") ||
        normalizedMessage.contains("connection aborted") ||
        normalizedMessage.contains("an established connection was aborted") ||
        normalizedMessage.contains("connection reset") ||
        normalizedMessage.contains("software caused connection abort") ||
        normalizedMessage.contains("stream closed") ||
        normalizedMessage.contains("socket closed") ||
        normalizedMessage.contains("connection closed") ||
        normalizedMessage.contains("connection timed out") ||
        normalizedMessage.contains("network is unreachable") ||
        normalizedMessage.contains("no route to host") ||
        normalizedMessage.contains("connection refused") ||
        normalizedMessage.contains("premature eof") ||
        normalizedMessage.contains("unexpected end of stream");
  }

  protected ResponseEntity<?> handleRangeRequest(Path path, List<HttpRange> ranges,
                                                 HttpServletResponse response) throws IOException {
    long fileLength = java.nio.file.Files.size(path);

    try {
      if (ranges.isEmpty()) {
        return streamFullFileFromPath(path, fileLength, response);
      } else {
        HttpRange range = ranges.get(0);
        return streamPartialFileFromPath(path, fileLength, range, response);
      }
    } catch (Exception e) {
      if (isClientDisconnected(e)) {
        logger.info("Client ngắt kết nối trong quá trình stream");
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
      }
      throw e;
    }
  }

  protected ResponseEntity<?> streamFullFileFromPath(Path path, long fileLength,
                                                     HttpServletResponse response) throws IOException {
    try (InputStream inputStream = java.nio.file.Files.newInputStream(path)) {
      return streamFullContent(inputStream, fileLength, response);
    }
  }

  private ResponseEntity<?> streamPartialFileFromPath(Path path, long fileLength,
                                                        HttpRange range, HttpServletResponse response) throws IOException {
    long start = range.getRangeStart(fileLength);
    long end = range.getRangeEnd(fileLength);

    StreamDataProvider dataProvider = (outputStream, startPos, length) -> {
      try (RandomAccessFile randomAccessFile = new RandomAccessFile(path.toFile(), "r")) {
        randomAccessFile.seek(startPos);
        streamDataFromRandomAccess(randomAccessFile, outputStream, length);
      }
    };

    return streamPartialContent(start, end, fileLength, response, dataProvider);
  }

  protected String getFileNameWithoutExtension(String filename) {
    int lastDotIndex = filename.lastIndexOf('.');
    return lastDotIndex > 0 ? filename.substring(0, lastDotIndex) : filename;
  }

  protected String getFileExtension(String filename) {
    int lastDotIndex = filename.lastIndexOf('.');
    return lastDotIndex > 0 ? filename.substring(lastDotIndex) : "";
  }

  protected Path generateUniqueFilePath(String filename) {
    String nameWithoutExt = getFileNameWithoutExtension(filename);
    String extension = getFileExtension(filename);
    String timestamp = String.valueOf(System.currentTimeMillis());
    String uniqueFilename = String.format("%s_%s%s", nameWithoutExt, timestamp, extension);

    return Constants.uploadDir.resolve(uniqueFilename);
  }

  /**
   * Set các header chung cho streaming
   *
   * @param response HttpServletResponse
   * @param file     File entity (FileDB)
   */
  protected void setCommonStreamingHeaders(HttpServletResponse response, Object file) {
    response.setHeader("Accept-Ranges", "bytes");
    response.setHeader("Cache-Control", "public, max-age=3600");
    response.setHeader("X-Content-Type-Options", "nosniff");

    // Xác định content type và filename dựa vào loại entity
    String contentType;
    String filename;
    if (file instanceof FileDB fileDB) {
      contentType = fileDB.getType();
      filename = fileDB.getName();
    } else {
      throw new IllegalArgumentException("Unsupported file type");
    }

    response.setHeader("Content-Type", contentType);

    // Encode filename theo RFC 5987
    String encodedFilename = encodeFilenameForHeader(filename);
    response.setHeader("Content-Disposition", String.format("inline; filename*=UTF-8''%s", encodedFilename));
  }

  /**
   * Encode filename cho HTTP header theo RFC 5987
   */
  protected String encodeFilenameForHeader(String filename) {
    try {
      return java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8)
          .replaceAll("\\+", "%20");
    } catch (Exception e) {
      return filename;
    }
  }

  // =============== LOGGING METHOD ===============
  protected String extractUserFromToken(String authHeader) throws IOException  {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;

    try {
      String token = authHeader.substring(7); // Remove "Bearer " prefix
      String[] parts = token.split("\\.");
      if (parts.length >= 2) {
        byte[] payload = java.util.Base64.getDecoder().decode(parts[1]);
        String payloadStr = new String(payload);

        if (payloadStr.contains("\"sub\"")) return payloadStr.replaceAll(".*\"sub\"\\s*:\\s*\"([^\"]+)\".*", "$1");
      }
    } catch (Exception e) {
      logger.warn("Failed to extract user from token: {}", e.getMessage());
    }

    return null;
  }
}
