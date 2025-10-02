package com.upload_file.service;

import com.upload_file.common.Constants;
import com.upload_file.common.Constants.StorageLevel;
import com.upload_file.common.UserIml;
import com.upload_file.dto.OneDriveUploadResult;
import com.upload_file.entity.FileDB;
import com.upload_file.repository.FileDBRepository;
import com.upload_file.service.abstract_file.AbstractFileService;
import io.micrometer.core.annotation.Timed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
public class FileDBService extends AbstractFileService implements UserIml {

  @Autowired
  private FileDBRepository fileDBRepository;

  @Autowired
  private FileStorageService fileStorageService;

  @Autowired
  private OneDriveService oneDriveService;

  @Autowired
  private PoiOfficeService poiOfficeService;

  @Autowired
  private LibreOfficeService libreOfficeService;

  @Autowired
  private StreamRangeService streamRangeService;

  private static final Logger logger = LoggerFactory.getLogger(FileDBService.class);
  private static final String systemProperty = System.getProperty("java.io.tmpdir");

  // Cache for media types
  private static final Map<String, MediaType> MEDIA_TYPE_CACHE = Map.ofEntries(
      Map.entry(".pdf", MediaType.APPLICATION_PDF),
      Map.entry(".txt", MediaType.TEXT_PLAIN),
      Map.entry(".doc", MediaType.parseMediaType("application/msword")),
      Map.entry(".docx", MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
      Map.entry(".xls", MediaType.parseMediaType("application/vnd.ms-excel")),
      Map.entry(".xlsx", MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
      // image formats
      Map.entry(".jpg", MediaType.IMAGE_JPEG),
      Map.entry(".jpeg", MediaType.IMAGE_JPEG),
      Map.entry(".png", MediaType.IMAGE_PNG),
      Map.entry(".gif", MediaType.IMAGE_GIF),
      // video formats
      Map.entry(".mp4", MediaType.parseMediaType("video/mp4")),
      Map.entry(".avi", MediaType.parseMediaType("video/x-msvideo")),
      Map.entry(".mov", MediaType.parseMediaType("video/quicktime")),
      Map.entry(".wmv", MediaType.parseMediaType("video/x-ms-wmv")),
      Map.entry(".flv", MediaType.parseMediaType("video/x-flv")),
      Map.entry(".webm", MediaType.parseMediaType("video/webm")),
      Map.entry(".mkv", MediaType.parseMediaType("video/x-matroska")),
      // audio formats
      Map.entry(".mp3", MediaType.parseMediaType("audio/mpeg")),
      Map.entry(".wav", MediaType.parseMediaType("audio/wav")),
      Map.entry(".flac", MediaType.parseMediaType("audio/flac")),
      Map.entry(".aac", MediaType.parseMediaType("audio/aac")),
      Map.entry(".ogg", MediaType.parseMediaType("audio/ogg")),
      Map.entry(".m4a", MediaType.parseMediaType("audio/mp4"))
  );

  private MediaType getMediaTypeForFile(String filename) {
    String extension = filename.substring(filename.lastIndexOf(".")).toLowerCase();
    return MEDIA_TYPE_CACHE.getOrDefault(extension, MediaType.APPLICATION_OCTET_STREAM);
  }

  private boolean isVideoFile(String fileName) {
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

  @Timed(value = "file.store", description = "Time taken to store single file")
  @Transactional(propagation = Propagation.REQUIRED, timeout = 60)
  public void store(MultipartFile file) throws IOException {
    try {
      String fileName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
      long fileSize = file.getSize();
      String contentType = file.getContentType();
      String currentUser = getCurrentUsername();

      logger.info("Starting to store file: {} ({} bytes) by user: {}, contentType: {}", fileName, fileSize, currentUser, contentType);

      // Đọc bytes một lần và reuse
      byte[] fileBytes = file.getBytes();
      FileDB fileDB = new FileDB(fileName, fileSize, contentType, null, currentTimeCreate(), currentUser);

      // Gọi đồng bộ thay vì async để tránh transaction issue
      saveTypeFile(fileSize, fileDB, fileBytes);

      logger.info("Successfully stored file: {} with size: {}MB by user: {}", fileName, fileSize / (1024.0 * 1024.0), currentUser);

    } catch (Exception e) {
      logger.error("Error storing file: {}", e.getMessage(), e);
      throw e;
    }
  }

  @Transactional(propagation = Propagation.REQUIRED)
  protected void saveTypeFile(long fileSize, FileDB fileDB, byte[] fileBytes) {
    FileDB file = switch (checkStorageLevel(fileSize)) {
      case DATABASE -> saveToDatabase(fileDB, fileBytes);
      case SYSTEM -> saveToSystem(fileDB, fileBytes);
      case ONEDRIVE -> saveToOneDrive(fileDB, fileBytes);
    };
    fileDBRepository.save(file);
  }

  private FileDB saveToDatabase(@NotNull FileDB fileDB, byte[] fileBytes) {
    fileDB.setPath("(db)");
    fileDB.setData(fileBytes); // Store data in DB for small files
    logger.debug("File will be saved to database: {}", fileDB.getName());
    return fileDB;
  }

  private FileDB saveToSystem(@NotNull FileDB fileDB, byte[] fileBytes) {
    try {
      saveToFilesystem(fileDB, fileBytes);
      logger.debug("File will be saved to filesystem: {}", fileDB.getName());
    } catch (IOException e) {
      logger.error("Error saving file to filesystem: {}", e.getMessage(), e);
    }
    return fileDB;
  }

  private FileDB saveToOneDrive(@NotNull FileDB fileDB, byte[] fileBytes) {
    try {

      OneDriveUploadResult result = oneDriveService.uploadLargeFile(fileDB.getName(), fileBytes);
      fileDB.setOneDriveId(result.getId());
      fileDB.setPath(result.getPath());
      logger.debug("File will be saved to OneDrive: {}", fileDB.getName());
    } catch (IOException e) {
      logger.error("Error uploading file to OneDrive: {}", e.getMessage(), e);
    }
    return fileDB;
  }

  @Timed(value = "file.store_multiple", description = "Time taken to store multiple files")
  @Transactional(propagation = Propagation.REQUIRED, timeout = 300)
  public void storeMultiple(@NotNull MultipartFile[] files) throws IOException {
    logger.info("Starting to store {} files", files.length);

    try {
      for (int i = 0; i < files.length; i++) {
        logger.debug("Processing file {}/{}: {}", i + 1, files.length, files[i].getOriginalFilename());
        store(files[i]);
      }
      logger.info("Successfully stored all {} files", files.length);
    } catch (Exception e) {
      logger.error("Error storing multiple files: {}", e.getMessage(), e);
      throw e;
    }
  }

  @Timed(value = "file.get_all", description = "Time taken to get all files")
  @Transactional(readOnly = true)
  public Page<FileDB> getAllFiles(String search, Pageable pageable, String username, boolean isAdmin) throws IOException {
    if (isAdmin) {
      return fileDBRepository.findAllByNameOrderByCreationDate(search.trim(), pageable);
    } else {
      return fileDBRepository.findAllByNameAndUserOrderByCreationDate(search.trim(), username, pageable);
    }
  }

  @Timed(value = "file.delete", description = "Time taken to delete file")
  @Transactional
  public void removeFileById(String id) {
    Optional<FileDB> optional = fileDBRepository.findById(id);
    if (optional.isEmpty()) {
      logger.warn("File not found for deletion: {}", id);
      return;
    }

    FileDB file = optional.get();
    logger.info("Deleting file: {} (ID: {})", file.getName(), id);
    long fileSize = file.getSize() != null ? file.getSize() : 0;
    switch (checkStorageLevel(fileSize)) {
      case SYSTEM -> removeFileSystem(file);
      case ONEDRIVE -> removeFileOneDrive(file);
    }

    fileDBRepository.deleteById(id);
    logger.info("Successfully deleted file from database: {}", file.getName());
  }

  @Timed(value = "file.delete_all", description = "Time taken to delete all files")
  private void removeFileSystem(@NotNull FileDB file) {
    try {
      Path path = Paths.get(file.getPath());
      if (Files.deleteIfExists(path)) {
        logger.info("Deleted file from filesystem: {}", path);
      }
    } catch (IOException e) {
      logger.error("Error deleting file from filesystem: {}", e.getMessage());
    }
  }

  private void removeFileOneDrive(@NotNull FileDB file) {
    try {
      if (file.getOneDriveId() != null) {
        oneDriveService.deleteFile(file.getOneDriveId(), file.getOneDriveId());
        logger.info("Deleted file from OneDrive: {}", file.getName());
      } else {
        logger.warn("No OneDrive ID found for file: {}", file.getName());
      }
    } catch (IOException e) {
      logger.error("Error deleting file from OneDrive: {}", e.getMessage(), e);
    }
  }

  @Async("taskExecutor")
  public CompletableFuture<Void> saveChunkAsync(String authHeader, MultipartFile file, String filename, int chunkIndex) {
    try {
      saveChunk(authHeader, file, filename, chunkIndex);
      return CompletableFuture.completedFuture(null);
    } catch (IOException e) {
      logger.error("Error saving chunk async: {}", e.getMessage(), e);
      return CompletableFuture.failedFuture(e);
    }
  }

  @Timed(value = "file.save_chunk", description = "Time taken to save file chunk")
  public void saveChunk(String authHeader, MultipartFile file, String filename, int chunkIndex) throws IOException {
    // Tạo thư mục temp theo UUID của user
    String tempDir = getTempDir(authHeader, filename, "saving chunk");
    File dir = new File(tempDir);
    if (!dir.exists()) dir.mkdirs();
    // Lưu chunk vào file tạm
    String chunkFilename = String.format("chunk_%d", chunkIndex);
    File chunkFile = new File(dir, chunkFilename);

    try (FileOutputStream fos = new FileOutputStream(chunkFile);
         InputStream is = file.getInputStream()) {

      byte[] buffer = new byte[8192];
      int bytesRead;
      while ((bytesRead = is.read(buffer)) != -1) {
        fos.write(buffer, 0, bytesRead);
      }
      fos.flush();
    }

    logger.debug("Saved chunk {} for file {} at: {}", chunkIndex, filename, chunkFile.getAbsolutePath());
  }

  @Timed(value = "file.merge_chunks", description = "Time taken to merge chunks and save file")
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED, timeout = 300)
  public void mergeChunksAndSave(String authHeader, String filename, int totalChunks, String contentType) throws IOException {
    String tempDir = getTempDir(authHeader, filename, "merging chunks");
    File dir = new File(tempDir);
    if (!dir.exists()) throw new IOException("Chunks directory not found for file: " + filename);
    File mergedFile = getMergedFile(authHeader, filename, totalChunks, dir);

    long fileSize = mergedFile.length();
    String currentUser = getCurrentUsername();
    FileDB fileDB = new FileDB(filename, fileSize, contentType, null, currentTimeCreate(), currentUser);
    byte[] fileBytes = Files.readAllBytes(mergedFile.toPath());
    saveTypeFile(fileSize, fileDB, fileBytes);
    cleanup(dir, mergedFile);
    logger.info("Successfully merged and saved file: {}", filename);
  }

  @NotNull
  private File getMergedFile(String authHeader, String filename, int totalChunks, File dir) throws IOException {
    String username = extractUserFromToken(authHeader);
    if (username == null) {
      // Fallback: sử dụng username nếu UUID null
      username = "user_anonymous";
      log.warn("Using username-based directory for chunk {}", username);
    }

    System.out.println("username in merge: " + getCurrentUsername());
    File mergedFile = new File(systemProperty, username + "_merged_" + filename);
//    File mergedFile = new File(systemProperty, "merged_" + filename);

    try (FileOutputStream fos = new FileOutputStream(mergedFile)) {
      for (int i = 0; i < totalChunks; i++) {
        File chunkFile = new File(dir, String.format("chunk_%d", i));
        if (!chunkFile.exists()) {
          throw new IOException("Missing chunk " + i + " for file: " + filename);
        }

        try (FileInputStream fis = new FileInputStream(chunkFile)) {
          byte[] buffer = new byte[8192];
          int bytesRead;
          while ((bytesRead = fis.read(buffer)) != -1) {
            fos.write(buffer, 0, bytesRead);
          }
        }
      }
      fos.flush();
    }
    return mergedFile;
  }

  private void cleanup(File chunkDir, File mergedFile) {
    try {
      // Xóa chunks
      if (chunkDir.exists()) {
        File[] chunks = chunkDir.listFiles();
        if (chunks != null) {
          for (File chunk : chunks) {
            chunk.delete();
          }
        }
        chunkDir.delete();
      }

      // Xóa merged file tạm
      if (mergedFile.exists()) {
        mergedFile.delete();
      }
    } catch (Exception e) {
      logger.warn("Error during cleanup: {}", e.getMessage());
    }
  }

  public boolean chunkExists(String authHeader, String filename, int chunkIndex) throws IOException {
    String tempDir = getTempDir(authHeader, filename, "existence check");
    File chunkFile = new File(tempDir, String.format("chunk_%d", chunkIndex));
    return chunkFile.exists();
  }

  private String getTempDir(String authHeader, String filename, String message) throws IOException {
    String username = extractUserFromToken(authHeader);
    if (username == null) {
      username = "user_anonymous";
      log.warn("Using username-based directory for chunk {}: {}", message, username);
    }
//    return systemProperty + "/chunks/" + filename;
    return systemProperty + "/chunks/" + username + "/" + filename;
  }

  @Timed(value = "file.save_to_filesystem", description = "Time taken to save file to filesystem")
  public void saveToFilesystem(FileDB fileDB, byte[] content) throws IOException {
    if (!Files.exists(Constants.uploadDir)) {
      Files.createDirectories(Constants.uploadDir);
    }
    Path filePath = generateUniqueFilePath(fileDB.getName());
    fileDB.setPath(filePath.toString());
    Files.write(filePath, content, StandardOpenOption.CREATE_NEW);
    logger.debug("File saved to filesystem: {}", filePath);
  }

  // ============= STREAMING METHODS =============

  public ResponseEntity<?> handlePreviewFile(String id,
                                             HttpServletRequest request,
                                             HttpServletResponse response,
                                             boolean download) throws Exception {

    FileDB fileDB;
    try {
      fileDB = fileStorageService.getFile(id);
    } catch (Exception e) {
      logger.error("Error retrieving file with id {}: {}", id, e.getMessage());
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "File not found"));
    }

    StorageLevel storageLevel = determineStorageLevel(fileDB);

    if (!download && storageLevel == StorageLevel.DATABASE) {
      if ("text/plain".equals(fileDB.getType())) {
        return streamRangeService.handleTextFileStreaming(fileDB, request, response);
      }
      // Office document preview (only for non-download requests)
      if (poiOfficeService.isOfficeDocument(fileDB.getType())) {
        return poiOfficeToHtml(fileDB, id);
      }
      // Office document preview với LibreOffice conversion (only for files < 10MB and not download)
      if (libreOfficeService.isConvertibleDocument(fileDB.getType(), fileDB.getName())) {
        return libreOfficeToHtml(fileDB, id, storageLevel, request, response);
      }
    }

    // Original handling cho các trường hợp khác
    return switch (storageLevel) {
      case DATABASE -> handleDatabaseFile(fileDB, download);
      case SYSTEM -> handleSystemFile(fileDB, request, response, download);
      case ONEDRIVE -> handleOneDriveFile(fileDB, download, response, request);
      default -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", "Unknown storage level"));
    };
  }

  @NotNull
  private ResponseEntity<?> poiOfficeToHtml(@NotNull FileDB fileDB, String id) throws Exception {
    String htmlContent = poiOfficeService.convertOfficeToHtml(id);
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_HTML)
        .header(HttpHeaders.CONTENT_DISPOSITION, String.format("inline; filename*=UTF-8''%s.html", encodeFilenameForHeader(fileDB.getName())))
        .body(htmlContent);
  }

  private ResponseEntity<?> libreOfficeToHtml(@NotNull FileDB fileDB, String id,
                                              StorageLevel storageLevel,
                                              HttpServletRequest request,
                                              HttpServletResponse response) throws Exception {
    logger.info("Converting Office document to PDF for preview: {}", fileDB.getName());
    byte[] fileData = getFileDataByStorageLevel(fileDB, storageLevel);
    if (fileData == null) {
      logger.error("Could not retrieve file data for conversion: {}", fileDB.getName());
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "File data not found"));
    }
    return libreOfficeService.convertAndStreamPDF(id, fileDB.getName(), fileData, request, response, false);
  }

  @Nullable
  private byte[] getFileDataByStorageLevel(FileDB fileDB, @NotNull StorageLevel storageLevel) throws Exception {
    return switch (storageLevel) {
      case DATABASE -> fileDB.getData();
      case SYSTEM -> Files.readAllBytes(Paths.get(fileDB.getPath()));
      case ONEDRIVE -> oneDriveService.downloadFile(fileDB.getName());
      default -> null;
    };
  }

  private ResponseEntity<?> handleDatabaseFile(@NotNull FileDB fileDB,
                                               boolean download) throws Exception {
    String contentType = fileDB.getType();
    String disposition = String.format("%s; filename*=UTF-8''%s", download ? "attachment" : "inline", encodeFilenameForHeader(fileDB.getName()));
    MediaType mediaType = getMediaTypeForFile(fileDB.getName());

    byte[] fileData;

    if (fileDB.getPath() != null && !fileDB.getPath().equals("(db)")) {
      Path filePath = Paths.get(fileDB.getPath());
      if (!Files.exists(filePath)) {
        return ResponseEntity.notFound().build();
      }
      fileData = Files.readAllBytes(filePath);
    } else {
      fileData = fileDB.getData();
      if (fileData == null) {
        return ResponseEntity.notFound().build();
      }
    }

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
        .contentType(contentType != null ? MediaType.parseMediaType(contentType) : mediaType)
        .contentLength(fileData.length)
        .body(fileData);
  }

  private ResponseEntity<?> handleSystemFile(@NotNull FileDB fileDB,
                                             HttpServletRequest request,
                                             HttpServletResponse response,
                                             boolean download) throws Exception {
    Path filePath = Paths.get(fileDB.getPath());

    if (!Files.exists(filePath)) return handleOneDriveFile(fileDB, download, response, request);
    HttpHeaders headers = new HttpHeaders();
    String rangeHeader = request.getHeader("Range");
    if (rangeHeader != null) {
      headers.set("Range", rangeHeader);
    }

    return streamFile(fileDB.getId(), headers, request, response);
  }

  @Nullable
  private ResponseEntity<?> handleOneDriveFile(@NotNull FileDB fileDB,
                                               boolean download,
                                               HttpServletResponse response,
                                               HttpServletRequest request) {
    String fileName = fileDB.getName();
    try {
      String disposition = String.format("%s; filename*=UTF-8''%s", download ? "attachment" : "inline", encodeFilenameForHeader(fileName));
      MediaType mediaType = getMediaTypeForFile(fileName);
      String rangeHeader = request.getHeader("Range");

      // Kiểm tra nếu là video file và có range request
      if (!download && isVideoFile(fileName) && rangeHeader != null) {
        return streamRangeService.streamVideoFromOneDriveWithRange(fileName, disposition, mediaType, rangeHeader, response, fileDB);
      }

      return streamRangeService.streamFileFromOneDrive(fileName, disposition, mediaType, rangeHeader, response);

    } catch (Exception e) {
      logger.error("Failed to start OneDrive streaming for file: {} - {}", fileName, e.getMessage());

      if (!response.isCommitted()) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("error", "Unable to stream file", "fileName", fileName));
      }
      return null;
    }
  }

  @Timed(value = "file.stream", description = "Time taken to stream file")
  public ResponseEntity<?> streamFile(String id, HttpHeaders headers,
                                      HttpServletRequest request,
                                      HttpServletResponse response) throws Exception {
    try {
      FileDB file = fileStorageService.getFile(id);
      setCommonStreamingHeaders(response, file);

      if (file.getPath() == null || file.getPath().equals("(db)")) {
        return streamRangeService.streamFileFromDatabase(file, headers, response);
      } else {
        return streamRangeService.streamFileFromFileSystem(file, headers, response);
      }
    } catch (FileNotFoundException e) {
      logger.error("Không tìm thấy file: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of("error", "File không tồn tại"));
    } catch (Exception e) {
      logger.error("Lỗi stream file: {}", e.getMessage(), e);
      if (isClientDisconnected(e)) {
        logger.info("Client đã ngắt kết nối stream file ID: {}", id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
      }
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", "Lỗi server"));
    }
  }

  private StorageLevel determineStorageLevel(@NotNull FileDB fileDB) throws FileNotFoundException {
    if (fileDB.getSize() == null) {
      if (fileDB.getData() != null && fileDB.getData().length > 0) {
        return StorageLevel.DATABASE;
      } else {
        throw new FileNotFoundException("File data not found");
      }
    }
    return checkStorageLevel(fileDB.getSize());
  }
}
