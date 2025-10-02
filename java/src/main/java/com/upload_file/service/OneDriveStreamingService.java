package com.upload_file.service;

import com.upload_file.common.Constants;
import com.upload_file.entity.FileDB;
import com.upload_file.service.abstract_file.AbstractStreamingService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Service
public class OneDriveStreamingService extends AbstractStreamingService {

  private static final Logger logger = LoggerFactory.getLogger(OneDriveStreamingService.class);

  @Value("${app.streaming.timeout:30000}")
  private int streamingTimeout;

  @Autowired
  private OneDriveService oneDriveService;

  public ResponseEntity<?> stream(@NotNull FileDB file,
                                                    HttpServletRequest request,
                                                    HttpServletResponse response) {
    String rangeHeader = request.getHeader("Range");

    // Kiểm tra nếu là video file và có range request - COPY logic cũ
    if (isVideoFile(file.getName()) && rangeHeader != null) {
      return streamVideoWithRange(file, rangeHeader, response);
    }

    return streamFile(file, rangeHeader, response, false);
  }

  public ResponseEntity<?> streamFile(@NotNull FileDB file, String rangeHeader,
                                      HttpServletResponse response, boolean download) {
    String fileName = file.getName();
    String disposition = String.format("%s; filename*=UTF-8''%s",
        download ? "attachment" : "inline",
        encodeFilenameForHeader(fileName));
    MediaType mediaType = getMediaTypeForFile(fileName);

    return streamFileFromOneDrive(fileName, disposition, mediaType, rangeHeader, response);
  }

  public ResponseEntity<?> streamVideoWithRange(@NotNull FileDB file, String rangeHeader,
                                                HttpServletResponse response) {
    String fileName = file.getName();
    String disposition = String.format("inline; filename*=UTF-8''%s", encodeFilenameForHeader(fileName));
    MediaType mediaType = getMediaTypeForFile(fileName);

    return streamVideoFromOneDriveWithRange(fileName, disposition, mediaType, rangeHeader, response, file);
  }

  private ResponseEntity<?> streamFileFromOneDrive(String fileName, String disposition,
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

  private ResponseEntity<?> streamVideoFromOneDriveWithRange(String fileName, String disposition,
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

  private MediaType getMediaTypeForFile(String filename) {
    String extension = filename.substring(filename.lastIndexOf(".")).toLowerCase();
    return switch (extension) {
      case ".mp4" -> MediaType.parseMediaType("video/mp4");
      case ".avi" -> MediaType.parseMediaType("video/x-msvideo");
      case ".mov" -> MediaType.parseMediaType("video/quicktime");
      case ".mp3" -> MediaType.parseMediaType("audio/mpeg");
      case ".wav" -> MediaType.parseMediaType("audio/wav");
      case ".pdf" -> MediaType.APPLICATION_PDF;
      case ".txt" -> MediaType.TEXT_PLAIN;
      case ".jpg", ".jpeg" -> MediaType.IMAGE_JPEG;
      case ".png" -> MediaType.IMAGE_PNG;
      default -> MediaType.APPLICATION_OCTET_STREAM;
    };
  }
}