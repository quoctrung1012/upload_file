package com.upload_file.service;

import com.upload_file.entity.FileDB;
import com.upload_file.service.abstract_file.AbstractStreamingService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Service
public class FileSystemStreamingService extends AbstractStreamingService {

  private static final Logger logger = LoggerFactory.getLogger(FileSystemStreamingService.class);

  /**
   * Stream file từ file system
   */
  public ResponseEntity<?> stream(@NotNull FileDB file, HttpHeaders headers,
                                  HttpServletResponse response) throws IOException {
    Path filePath = Paths.get(file.getPath());
    if (!Files.exists(filePath)) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of("error", "File không tồn tại trên filesystem"));
    }

    long fileLength = Files.size(filePath);
    List<HttpRange> ranges = headers.getRange();

    try {
      if (ranges.isEmpty()) {
        return streamFullFile(filePath, fileLength, response);
      } else {
        HttpRange range = ranges.get(0);
        return streamWithRange(filePath, fileLength, range, response);
      }
    } catch (Exception e) {
      if (isClientDisconnected(e)) {
        logger.info("Client ngắt kết nối trong quá trình stream");
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
      }
      throw e;
    }
  }

  /**
   * Stream full file từ file system
   */
  public ResponseEntity<?> streamFullFile(Path filePath, long fileLength,
                                          HttpServletResponse response) throws IOException {

    try (InputStream inputStream = Files.newInputStream(filePath)) {
      return streamFullContent(inputStream, fileLength, response);
    }
  }

  /**
   * Stream partial file với range từ file system
   */
  public ResponseEntity<?> streamWithRange(Path filePath, long fileLength,
                                           @NotNull HttpRange range,
                                           HttpServletResponse response) throws IOException {
    long start = range.getRangeStart(fileLength);
    long end = range.getRangeEnd(fileLength);

    StreamDataProvider dataProvider = (outputStream, startPos, length) -> {
      try (RandomAccessFile randomAccessFile = new RandomAccessFile(filePath.toFile(), "r")) {
        randomAccessFile.seek(startPos);
        streamDataFromRandomAccess(randomAccessFile, outputStream, length);
      }
    };

    return streamPartialContent(start, end, fileLength, response, dataProvider);
  }

  /**
   * Stream data từ RandomAccessFile
   */
  private void streamDataFromRandomAccess(RandomAccessFile randomAccessFile,
                                          ServletOutputStream outputStream,
                                          long length) throws IOException {
    byte[] buffer = new byte[com.upload_file.common.Constants.STREAM_BUFFER_SIZE];
    long bytesRead = 0;
    int read;

    while (bytesRead < length && (read = randomAccessFile.read(buffer, 0,
        (int) Math.min(buffer.length, length - bytesRead))) != -1) {
      try {
        outputStream.write(buffer, 0, read);
        bytesRead += read;

        if (bytesRead % com.upload_file.common.Constants.FLUSH_INTERVAL == 0) {
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
}