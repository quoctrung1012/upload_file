package com.upload_file.service.abstract_file;

import com.upload_file.common.Constants;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public abstract class AbstractStreamingService extends AbstractFileService {

  private static final Logger logger = LoggerFactory.getLogger(AbstractStreamingService.class);

  protected boolean isVideoFile(String fileName) {
    String extension = fileName.toLowerCase();
    return
        // video
        extension.endsWith(".mp4") ||
            extension.endsWith(".avi") ||
            extension.endsWith(".mov") ||
            extension.endsWith(".wmv") ||
            extension.endsWith(".flv") ||
            extension.endsWith(".webm") ||
            extension.endsWith(".mkv") ||
            // audio
            extension.endsWith(".mp3") ||
            extension.endsWith(".wav") ||
            extension.endsWith(".flac") ||
            extension.endsWith(".aac") ||
            extension.endsWith(".ogg") ||
            extension.endsWith(".m4a");
  }

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

  protected ResponseEntity<?> streamFullContent(InputStream inputStream, long contentLength,
                                                HttpServletResponse response) throws IOException {
    response.setStatus(HttpStatus.OK.value());
    response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength));

    try (BufferedInputStream bufferedInput = new BufferedInputStream(inputStream, Constants.STREAM_BUFFER_SIZE);
         ServletOutputStream outputStream = response.getOutputStream()) {

      streamData(bufferedInput, outputStream, 0, contentLength);

      // Thêm check committed trước flush
      if (!response.isCommitted()) {
        outputStream.flush();
      }
      return ResponseEntity.ok().build();
    }
  }

  protected ResponseEntity<?> streamPartialContent(long start, long end, long totalLength,
                                                   HttpServletResponse response,
                                                   StreamDataProvider dataProvider) throws IOException {
    long rangeLength = end - start + 1;

    response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
    response.setHeader(HttpHeaders.CONTENT_RANGE, String.format("bytes %d-%d/%d", start, end, totalLength));
    response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(rangeLength));

    try (ServletOutputStream outputStream = response.getOutputStream()) {
      dataProvider.streamData(outputStream, start, rangeLength);

      // Thêm check committed trước flush
      if (!response.isCommitted()) {
        outputStream.flush();
      }
      return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).build();
    }
  }

  protected void setCommonStreamingHeaders(HttpServletResponse response, String contentType,
                                           String filename, boolean download) {
    // Kiểm tra response committed trước khi set headers
    if (response.isCommitted()) {
      logger.warn("Response already committed, cannot set headers for file: {}", filename);
      return;
    }

    response.setHeader("Accept-Ranges", "bytes");
    response.setHeader("Cache-Control", "public, max-age=3600");
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader(HttpHeaders.CONTENT_TYPE, contentType);

    String disposition = String.format("%s; filename*=UTF-8''%s",
        download ? "attachment" : "inline",
        encodeFilenameForHeader(filename));
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition);
  }

  protected void streamData(InputStream inputStream, ServletOutputStream outputStream,
                            long skip, long length) throws IOException {
    if (skip > 0) {
      inputStream.skip(skip);
    }

    byte[] buffer = new byte[Constants.STREAM_BUFFER_SIZE];
    long bytesRead = 0;
    int read;

    while (bytesRead < length && (read = inputStream.read(buffer, 0,
        (int) Math.min(buffer.length, length - bytesRead))) != -1) {
      try {
        outputStream.write(buffer, 0, read);
        bytesRead += read;

        if (bytesRead % Constants.FLUSH_INTERVAL == 0) {
          outputStream.flush();
        }
      } catch (IOException e) {
        if (isClientDisconnected(e)) {
          logger.info("Client disconnected during streaming");
          break;
        }
        throw e;
      }
    }
  }

  /**
   * Stream buffered data from byte array - COPY từ code cũ
   */
  protected void streamBufferedData(byte[] buffer, ServletOutputStream outputStream,
                                    long start, long length) throws IOException {
    long bytesToWrite = Math.min(length, buffer.length - start);
    if (bytesToWrite > 0) {
      outputStream.write(buffer, (int) start, (int) bytesToWrite);
    }
  }

  protected ResponseEntity<?> handleRangeRequest(List<HttpRange> ranges, long fileLength,
                                                 HttpServletResponse response,
                                                 StreamDataProvider dataProvider) throws IOException {
    try {
      if (ranges.isEmpty()) {
        // Stream full content
        response.setStatus(HttpStatus.OK.value());
        response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileLength));

        try (ServletOutputStream outputStream = response.getOutputStream()) {
          dataProvider.streamData(outputStream, 0, fileLength);

          if (!response.isCommitted()) {
            outputStream.flush();
          }
          return ResponseEntity.ok().build();
        }
      } else {
        // Stream partial content
        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(fileLength);
        long end = range.getRangeEnd(fileLength);
        return streamPartialContent(start, end, fileLength, response, dataProvider);
      }
    } catch (Exception e) {
      if (isClientDisconnected(e)) {
        logger.info("Client disconnected during range request");
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
      }
      throw e;
    }
  }

  @FunctionalInterface
  protected interface StreamDataProvider {
    void streamData(ServletOutputStream outputStream, long start, long length) throws IOException;
  }
}