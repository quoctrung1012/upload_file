package com.upload_file.service;

import com.upload_file.common.UserIml;
import com.upload_file.entity.FileDB;
import com.upload_file.service.abstract_file.AbstractFileService;
import io.micrometer.core.annotation.Timed;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

@Service
public class ChunkUploadService extends AbstractFileService implements UserIml {

  private static final Logger logger = LoggerFactory.getLogger(ChunkUploadService.class);
  private static final String systemProperty = System.getProperty("java.io.tmpdir");

  @Autowired
  private FileUploadService fileUploadService;

  /**
   * Save chunk async
   */
  @Async("taskExecutor")
  public CompletableFuture<Void> saveChunkAsync(String authHeader, MultipartFile file,
                                                String filename, int chunkIndex) {
    try {
      saveChunk(authHeader, file, filename, chunkIndex);
      return CompletableFuture.completedFuture(null);
    } catch (IOException e) {
      logger.error("Error saving chunk async: {}", e.getMessage(), e);
      return CompletableFuture.failedFuture(e);
    }
  }

  /**
   * Save single chunk
   */
  @Timed(value = "file.save_chunk", description = "Time taken to save file chunk")
  public void saveChunk(String authHeader, MultipartFile file, String filename,
                        int chunkIndex) throws IOException {
    String tempDir = getTempDir(authHeader, filename, "saving chunk");
    File dir = new File(tempDir);
    if (!dir.exists()) {
      dir.mkdirs();
    }

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

//    logger.debug("Saved chunk {} for file {} at: {}", chunkIndex, filename, chunkFile.getAbsolutePath());
  }

  /**
   * Merge chunks and save final file
   */
  @Timed(value = "file.merge_chunks", description = "Time taken to merge chunks and save file")
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED, timeout = 300)
  public void mergeChunksAndSave(String authHeader, String filename, int totalChunks,
                                 String contentType) throws IOException {
    String tempDir = getTempDir(authHeader, filename, "merging chunks");
    File dir = new File(tempDir);

    if (!dir.exists()) {
      throw new IOException("Chunks directory not found for file: " + filename);
    }

    File mergedFile = createMergedFile(authHeader, filename, totalChunks, dir);

    long fileSize = mergedFile.length();
    String currentUser = getCurrentUsername();
    FileDB fileDB = new FileDB(filename, fileSize, contentType, null, currentTimeCreate(), currentUser);

    byte[] fileBytes = Files.readAllBytes(mergedFile.toPath());

    MockMultipartFile mockFile = new MockMultipartFile(filename, filename, contentType, fileBytes);
    fileUploadService.uploadSingleFile(mockFile);

    cleanup(dir, mergedFile);
    logger.info("Successfully merged and saved file: {}", filename);
  }

  /**
   * Check if chunk exists
   */
  public boolean chunkExists(String authHeader, String filename, int chunkIndex) throws IOException {
    String tempDir = getTempDir(authHeader, filename, "existence check");
    File chunkFile = new File(tempDir, String.format("chunk_%d", chunkIndex));
    return chunkFile.exists();
  }

  /**
   * Create merged file from chunks
   */
  @NotNull
  private File createMergedFile(String authHeader, String filename, int totalChunks,
                                File dir) throws IOException {
    String username = extractUserFromToken(authHeader);
    if (username == null) {
      username = "user_anonymous";
      logger.warn("Using anonymous username for merge: {}", username);
    }

    File mergedFile = new File(systemProperty, username + "_merged_" + filename);

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

  /**
   * Cleanup temporary files
   */
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

  /**
   * Get temp directory for chunks
   */
  private String getTempDir(String authHeader, String filename, String message) throws IOException {
    String username = extractUserFromToken(authHeader);
    if (username == null) {
      username = "user_anonymous";
      logger.warn("Using username-based directory for chunk {}: {}", message, username);
    }
    return systemProperty + "/chunks/" + username + "/" + filename;
  }

  /**
   * Mock MultipartFile class for internal use
   */
  private static class MockMultipartFile implements MultipartFile {
    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final byte[] content;

    public MockMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
      this.name = name;
      this.originalFilename = originalFilename;
      this.contentType = contentType;
      this.content = content;
    }

    @Override
    public String getName() { return name; }

    @Override
    public String getOriginalFilename() { return originalFilename; }

    @Override
    public String getContentType() { return contentType; }

    @Override
    public boolean isEmpty() { return content.length == 0; }

    @Override
    public long getSize() { return content.length; }

    @Override
    public byte[] getBytes() { return content; }

    @Override
    public InputStream getInputStream() { return new ByteArrayInputStream(content); }

    @Override
    public void transferTo(File dest) throws IOException, IllegalStateException {
      Files.write(dest.toPath(), content);
    }
  }
}