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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Paths;

/**
 * Service chỉ xử lý preview text files
 */
@Service
public class TextFilePreviewService extends AbstractStreamingService {

  private static final Logger logger = LoggerFactory.getLogger(TextFilePreviewService.class);

  @Autowired
  private OneDriveService oneDriveService;

  @Autowired
  private PhysicalFileService physicalFileService;

  /**
   * Handle text file streaming với range support
   */
  public ResponseEntity<?> handleTextFileStreaming(@NotNull FileDB fileDB,
                                                   @NotNull HttpServletRequest request,
                                                   HttpServletResponse response) throws IOException {
    String rangeHeader = request.getHeader("Range");

    if (rangeHeader != null) {
      return handleTextFileWithRange(fileDB, rangeHeader, response);
    } else {
      return streamTextFileBuffered(fileDB, response);
    }
  }

  /**
   * Handle text file với range request
   */
  private ResponseEntity<?> handleTextFileWithRange(@NotNull FileDB fileDB, String rangeHeader,
                                                    HttpServletResponse response) throws IOException {
    long fileSize = fileDB.getSize();
    HttpRange range = parseRangeHeader(rangeHeader, fileSize);
    long start = range.getRangeStart(fileSize);
    long end = range.getRangeEnd(fileSize);

    // Giảm chunk size cho text file lớn (256KB thay vì 1MB)
    long maxChunkSize = 256 * 1024;
    if (end - start + 1 > maxChunkSize) {
      end = start + maxChunkSize - 1;
    }

    long contentLength = end - start + 1;

    // Set response headers
    response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
    response.setHeader(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8");
    response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength));
    response.setHeader(HttpHeaders.CONTENT_RANGE, String.format("bytes %d-%d/%d", start, end, fileSize));
    response.setHeader("Accept-Ranges", "bytes");
    response.setHeader("Cache-Control", "no-cache");

    try (InputStream inputStream = getInputStreamByStorageLevel(fileDB, start, end);
         ServletOutputStream outputStream = response.getOutputStream()) {

      byte[] buffer = new byte[4096]; // Giảm buffer size
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

  /**
   * Stream toàn bộ text file với buffer
   */
  private ResponseEntity<?> streamTextFileBuffered(@NotNull FileDB fileDB,
                                                   HttpServletResponse response) throws IOException {
    Constants.StorageLevel level = physicalFileService.determineStorageLevel(fileDB);

    response.setHeader(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8");
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodeFilenameForHeader(fileDB.getName()));
    response.setHeader("Accept-Ranges", "bytes");
    response.setHeader("Cache-Control", "no-cache");

    // Chỉ stream một phần đầu file cho preview thay vì toàn bộ
    long maxPreviewSize = 1024 * 1024; // 1MB cho preview
    long actualStreamSize = Math.min(fileDB.getSize(), maxPreviewSize);

    response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(actualStreamSize));

    try (InputStream inputStream = getLimitedInputStreamByStorageLevel(fileDB, level, actualStreamSize);
         ServletOutputStream outputStream = response.getOutputStream();
         BufferedInputStream bufferedInput = new BufferedInputStream(inputStream, 8192)) {

      byte[] buffer = new byte[8192];
      int bytesRead;
      long totalBytesRead = 0;

      while ((bytesRead = bufferedInput.read(buffer)) != -1 && totalBytesRead < actualStreamSize) {
        try {
          int bytesToWrite = (int) Math.min(bytesRead, actualStreamSize - totalBytesRead);
          outputStream.write(buffer, 0, bytesToWrite);
          totalBytesRead += bytesToWrite;

          // Flush thường xuyên hơn cho file lớn
          if (totalBytesRead % 4096 == 0) {
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

      logger.debug("Successfully streamed {} bytes (limited preview) for text file: {}",
          totalBytesRead, fileDB.getName());
      return ResponseEntity.ok().build();

    } catch (Exception e) {
      if (isClientDisconnected(e)) {
        logger.debug("Client disconnected during text file stream setup: {}", fileDB.getName());
        return null;
      }
      throw e;
    }
  }

  /**
   * Get InputStream theo storage level với range
   */
  private InputStream getInputStreamByStorageLevel(@NotNull FileDB fileDB, long start, long end) throws IOException {
    Constants.StorageLevel level = physicalFileService.determineStorageLevel(fileDB);

    return switch (level) {
      case ONEDRIVE -> {
        String rangeHeader = String.format("bytes=%d-%d", start, end);
        yield oneDriveService.streamFileWithRange(fileDB.getName(), rangeHeader);
      }
      case SYSTEM -> {
        var filePath = Paths.get(fileDB.getPath());
        FileInputStream fis = new FileInputStream(filePath.toFile());
        if (start > 0) {
          long skipped = fis.skip(start);
          if (skipped != start) {
            fis.close();
            throw new IOException("Could not skip to start position: " + start);
          }
        }
        yield new BoundedInputStream(fis, end - start + 1);
      }
      case DATABASE -> {
        byte[] data = fileDB.getData();
        int startInt = (int) Math.min(start, data.length);
        int endInt = (int) Math.min(end + 1, data.length);
        int length = endInt - startInt;
        yield new ByteArrayInputStream(data, startInt, length);
      }
      default -> throw new IOException("Unknown storage level: " + level);
    };
  }

  /**
   * Get limited InputStream theo storage level
   */
  private InputStream getLimitedInputStreamByStorageLevel(@NotNull FileDB fileDB,
                                                          Constants.StorageLevel level,
                                                          long maxBytes) throws IOException {
    return switch (level) {
      case ONEDRIVE -> {
        String rangeHeader = String.format("bytes=0-%d", maxBytes - 1);
        yield oneDriveService.streamFileWithRange(fileDB.getName(), rangeHeader);
      }
      case SYSTEM -> {
        var filePath = Paths.get(fileDB.getPath());
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

  /**
   * Bounded InputStream class
   */
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