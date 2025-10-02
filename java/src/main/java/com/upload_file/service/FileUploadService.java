package com.upload_file.service;

import com.upload_file.common.Constants;
import com.upload_file.common.UserIml;
import com.upload_file.entity.FileDB;
import com.upload_file.service.abstract_file.AbstractFileService;
import io.micrometer.core.annotation.Timed;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Objects;

/**
 * Service chỉ xử lý upload logic
 * Không chứa streaming hay preview logic
 */
@Service
public class FileUploadService extends AbstractFileService implements UserIml {

  private static final Logger logger = LoggerFactory.getLogger(FileUploadService.class);

  @Autowired
  private FileStorageService fileStorageService;

  @Autowired
  private PhysicalFileService physicalFileService;

  /**
   * Upload single file
   */
  @Timed(value = "file.upload_single", description = "Time taken to upload single file")
  @Transactional(propagation = Propagation.REQUIRED, timeout = 60)
  public void uploadSingleFile(@NotNull MultipartFile file) throws IOException {
    String fileName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
    long fileSize = file.getSize();
    String contentType = file.getContentType();
    String currentUser = getCurrentUsername();

    logger.info("Starting to store file: {} ({} bytes) by user: {}, contentType: {}",
        fileName, fileSize, currentUser, contentType);

    // Đọc bytes một lần và reuse
    byte[] fileBytes = file.getBytes();
    FileDB fileDB = new FileDB(fileName, fileSize, contentType, null, currentTimeCreate(), currentUser);

    // Xử lý lưu trữ theo storage level
    processFileStorage(fileSize, fileDB, fileBytes);

    logger.info("Successfully stored file: {} with size: {}MB by user: {}",
        fileName, fileSize / (1024.0 * 1024.0), currentUser);
  }

  /**
   * Upload multiple files
   */
  @Timed(value = "file.upload_multiple", description = "Time taken to upload multiple files")
  @Transactional(propagation = Propagation.REQUIRED, timeout = 300)
  public void uploadMultipleFiles(@NotNull MultipartFile[] files) throws IOException {
    logger.info("Starting to store {} files", files.length);

    for (int i = 0; i < files.length; i++) {
      logger.debug("Processing file {}/{}: {}", i + 1, files.length, files[i].getOriginalFilename());
      uploadSingleFile(files[i]);
    }

    logger.info("Successfully stored all {} files", files.length);
  }

  /**
   * Xử lý lưu trữ file theo storage level
   */
  private void processFileStorage(long fileSize, FileDB fileDB, byte[] fileBytes) throws IOException {
    Constants.StorageLevel level = checkStorageLevel(fileSize);

    switch (level) {
      case DATABASE -> physicalFileService.saveToDatabase(fileDB, fileBytes);
      case SYSTEM -> physicalFileService.saveToFileSystem(fileDB, fileBytes);
      case ONEDRIVE -> physicalFileService.saveToOneDrive(fileDB, fileBytes);
    }
    logger.debug("File will be saved to {}: {}", level, fileDB.getName());
    fileStorageService.save(fileDB);
  }

  /**
   * Xóa file (bao gồm cả metadata và physical file)
   */
  @Timed(value = "file.delete", description = "Time taken to delete file")
  @Transactional
  public void deleteFile(String id) {
    FileDB fileDB = fileStorageService.getFile(id);
    logger.info("Deleting file: {} (ID: {})", fileDB.getName(), id);

    // Xóa physical file
    deletePhysicalFile(fileDB);

    // Xóa metadata từ database
    fileStorageService.deleteById(id);
    logger.info("Successfully deleted file: {}", fileDB.getName());
  }

  /**
   * Xóa physical file theo storage level
   */
  private void deletePhysicalFile(FileDB fileDB) {
    Constants.StorageLevel level = physicalFileService.determineStorageLevel(fileDB);

    switch (level) {
      case SYSTEM -> physicalFileService.deleteFromFileSystem(fileDB.getPath());
      case ONEDRIVE -> physicalFileService.deleteFromOneDrive(fileDB.getName(), fileDB.getOneDriveId());
      case DATABASE -> logger.debug("File stored in database, no physical file to delete");
    }
  }
}