package com.upload_file.service;

import com.upload_file.common.Constants;
import com.upload_file.dto.OneDriveUploadResult;
import com.upload_file.entity.FileDB;
import com.upload_file.service.abstract_file.AbstractFileService;
import io.micrometer.core.annotation.Timed;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Service chỉ xử lý lưu trữ vật lý files
 * Không chứa logic database operations
 */
@Service
public class PhysicalFileService extends AbstractFileService {

  private static final Logger logger = LoggerFactory.getLogger(PhysicalFileService.class);

  @Autowired
  private OneDriveService oneDriveService;

  /**
   * Lưu file vào database (cho file nhỏ)
   */
  @Timed(value = "file.save_to_database", description = "Time taken to save file to database")
  public void saveToDatabase(@NotNull FileDB fileDB, byte[] fileBytes) {
    fileDB.setPath("(db)");
    fileDB.setData(fileBytes);
    logger.debug("File prepared for database storage: {}", fileDB.getName());
  }

  /**
   * Lưu file vào file system
   */
  @Timed(value = "file.save_to_filesystem", description = "Time taken to save file to filesystem")
  public void saveToFileSystem(@NotNull FileDB fileDB, byte[] fileBytes) throws IOException {
    if (!Files.exists(Constants.uploadDir)) {
      Files.createDirectories(Constants.uploadDir);
    }

    Path filePath = generateUniqueFilePath(fileDB.getName());
    fileDB.setPath(filePath.toString());
    Files.write(filePath, fileBytes, StandardOpenOption.CREATE_NEW);

    logger.debug("File saved to filesystem: {}", filePath);
  }

  /**
   * Upload file lên OneDrive
   */
  @Timed(value = "file.save_to_onedrive", description = "Time taken to save file to OneDrive")
  public void saveToOneDrive(@NotNull FileDB fileDB, byte[] fileBytes) throws IOException {
    OneDriveUploadResult result = oneDriveService.uploadLargeFile(fileDB.getName(), fileBytes);
    fileDB.setOneDriveId(result.getId());
    fileDB.setPath(result.getPath());

    logger.debug("File saved to OneDrive: {} (ID: {})", fileDB.getName(), result.getId());
  }

  /**
   * Xóa file từ file system
   */
  @Timed(value = "file.delete_from_filesystem", description = "Time taken to delete file from filesystem")
  public void deleteFromFileSystem(String filePath) {
    try {
      Path path = Paths.get(filePath);
      if (Files.deleteIfExists(path)) {
        logger.info("Deleted file from filesystem: {}", path);
      } else {
        logger.warn("File not found on filesystem: {}", path);
      }
    } catch (IOException e) {
      logger.error("Error deleting file from filesystem: {} - {}", filePath, e.getMessage());
    }
  }

  /**
   * Xóa file từ OneDrive
   */
  @Timed(value = "file.delete_from_onedrive", description = "Time taken to delete file from OneDrive")
  public void deleteFromOneDrive(String fileName, String oneDriveId) {
    try {
      if (oneDriveId != null) {
        oneDriveService.deleteFile(fileName, oneDriveId);
        logger.info("Deleted file from OneDrive: {}", fileName);
      } else {
        logger.warn("No OneDrive ID found for file: {}", fileName);
      }
    } catch (IOException e) {
      logger.error("Error deleting file from OneDrive: {} - {}", fileName, e.getMessage());
    }
  }

  /**
   * Lấy dữ liệu file theo storage level
   */
  public byte[] getFileData(FileDB fileDB, Constants.StorageLevel storageLevel) throws IOException {
    return switch (storageLevel) {
      case DATABASE -> fileDB.getData();
      case SYSTEM -> Files.readAllBytes(Paths.get(fileDB.getPath()));
      case ONEDRIVE -> oneDriveService.downloadFile(fileDB.getName());
      default -> throw new IOException("Unknown storage level: " + storageLevel);
    };
  }

  /**
   * Xác định storage level dựa trên file size
   */
  public Constants.StorageLevel determineStorageLevel(FileDB fileDB) {
    if (fileDB.getSize() == null) {
      if (fileDB.getData() != null && fileDB.getData().length > 0) {
        return Constants.StorageLevel.DATABASE;
      }
      throw new RuntimeException("File data not found");
    }
    return checkStorageLevel(fileDB.getSize());
  }
}