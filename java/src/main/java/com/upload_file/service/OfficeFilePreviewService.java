package com.upload_file.service;

import com.upload_file.common.Constants;
import com.upload_file.entity.FileDB;
import com.upload_file.service.abstract_file.AbstractFileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service chỉ xử lý preview office documents
 */
@Service
public class OfficeFilePreviewService extends AbstractFileService {

  private static final Logger logger = LoggerFactory.getLogger(OfficeFilePreviewService.class);

  @Autowired
  private PoiOfficeService poiOfficeService;

  @Autowired
  private LibreOfficeService libreOfficeService;

  @Autowired
  private PhysicalFileService physicalFileService;

  /**
   * Preview office document sử dụng POI (cho files trong database)
   */
  public ResponseEntity<?> previewOfficeDocument(@NotNull FileDB fileDB, String id) throws Exception {
    try {
      logger.info("Converting Office document to HTML using POI: {}", fileDB.getName());
      String htmlContent = poiOfficeService.convertOfficeToHtml(id);

      return ResponseEntity.ok()
          .contentType(MediaType.TEXT_HTML)
          .header(HttpHeaders.CONTENT_DISPOSITION, String.format("inline; filename*=UTF-8''%s.html",
              encodeFilenameForHeader(fileDB.getName())))
          .body(htmlContent);

    } catch (Exception e) {
      logger.error("Failed to convert office document with POI: {} - {}", fileDB.getName(), e.getMessage());
      throw e;
    }
  }

  /**
   * Convert và preview office document sử dụng LibreOffice
   */
  public ResponseEntity<?> convertAndPreview(@NotNull FileDB fileDB, String id,
                                             Constants.StorageLevel storageLevel,
                                             HttpServletRequest request,
                                             HttpServletResponse response) throws Exception {
    try {
      logger.info("Converting Office document to PDF using LibreOffice: {}", fileDB.getName());

      byte[] fileData = getFileDataByStorageLevel(fileDB, storageLevel);
      if (fileData == null) {
        logger.error("Could not retrieve file data for conversion: {}", fileDB.getName());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", "File data not found"));
      }

      return libreOfficeService.convertAndStreamPDF(id, fileDB.getName(), fileData, request, response, false);

    } catch (Exception e) {
      logger.error("Failed to convert office document with LibreOffice: {} - {}", fileDB.getName(), e.getMessage());
      throw e;
    }
  }

  /**
   * Lấy file data theo storage level
   */
  private byte[] getFileDataByStorageLevel(@NotNull FileDB fileDB,
                                           Constants.StorageLevel storageLevel) throws Exception {
    return physicalFileService.getFileData(fileDB, storageLevel);
  }

  /**
   * Kiểm tra có phải office document không
   */
  public boolean isOfficeDocument(String contentType) {
    return poiOfficeService.isOfficeDocument(contentType);
  }

  /**
   * Kiểm tra có thể convert document không
   */
  public boolean isConvertibleDocument(String contentType, String fileName) {
    return libreOfficeService.isConvertibleDocument(contentType, fileName);
  }
}