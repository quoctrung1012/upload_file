package com.upload_file.service;

import com.upload_file.entity.FileDB;
import com.upload_file.service.abstract_file.AbstractStreamingService;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Service chỉ xử lý streaming từ database - Sử dụng logic cũ đã hoạt động ổn định
 */
@Service
public class DatabaseStreamingService extends AbstractStreamingService {

  private static final Logger logger = LoggerFactory.getLogger(DatabaseStreamingService.class);

  /**
   * Stream file từ database - COPY từ StreamRangeService cũ
   */

  public ResponseEntity<?> stream(@NotNull FileDB file, HttpHeaders headers,
                                  HttpServletResponse response) throws IOException {
    byte[] fileData = file.getData();
    if (fileData == null || fileData.length == 0) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of("error", "Dữ liệu file không tồn tại"));
    }

    List<HttpRange> ranges = headers.getRange();

    try {
      if (ranges.isEmpty()) {
        return streamFullFile(fileData, response);
      } else {
        HttpRange range = ranges.get(0);
        return streamWithRange(fileData, range, response);
      }
    } catch (Exception e) {
      if (isClientDisconnected(e)) {
        logger.info("Client ngắt kết nối trong quá trình stream từ database");
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
      }
      throw e;
    }
  }

  /*public ResponseEntity<?> stream(@NotNull FileDB file, HttpHeaders headers,
                                  HttpServletResponse response) throws IOException {
    byte[] fileData = file.getData();
    if (fileData == null || fileData.length == 0) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of("error", "Dữ liệu file không tồn tại"));
    }

    long fileLength = fileData.length;
    List<HttpRange> ranges = headers.getRange();

    // COPY nguyên logic từ StreamRangeService.streamFileFromDatabase()
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
  }*/

  /**
   * Stream full file từ database
   */
  public ResponseEntity<?> streamFullFile(@NotNull byte[] fileData, HttpServletResponse response) throws IOException {

    long fileLength = fileData.length;
    try (ByteArrayInputStream inputStream = new ByteArrayInputStream(fileData)) {
      return streamFullContent(inputStream, fileLength, response);
    }
  }

  /**
   * Stream partial file với range từ database
   */
  public ResponseEntity<?> streamWithRange(@NotNull byte[] fileData, HttpRange range,
                                           HttpServletResponse response) throws IOException {

    long fileLength = fileData.length;
    long start = range.getRangeStart(fileLength);
    long end = range.getRangeEnd(fileLength);

    StreamDataProvider dataProvider = (outputStream, startPos, length) -> {
      streamBufferedData(fileData, outputStream, startPos, length);
    };

    return streamPartialContent(start, end, fileLength, response, dataProvider);
  }
}