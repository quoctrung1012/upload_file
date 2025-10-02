package com.upload_file.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upload_file.dto.OneDriveUploadResult;
import io.micrometer.core.annotation.Timed;
import lombok.Getter;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class OneDriveService {

  private static final Logger logger = LoggerFactory.getLogger(OneDriveService.class);

  @Value("${azure.client-id}")
  private String clientId;

  @Value("${azure.client-secret}")
  private String clientSecret;

  @Value("${azure.tenant-id}")
  private String tenantId;

  @Value("${azure.site-id}")
  private String siteId;

  @Value("${azure.drive-id}")
  private String driveId;

  @Value("${azure.user-id}")
  private String userId;

  @Value("${jwt.header}")
  private String jwtHeader;

  @Value("${jwt.prefix}")
  private String jwtPrefix;

  @Getter
  public enum DriveEndpoint {
    ROOT("root:/"),
    CHILDREN("root/children");
    private final String suffix;

    DriveEndpoint(String suffix) {
      this.suffix = suffix;
    }
  }

  private static final String TOKEN_URL_TEMPLATE = "https://login.microsoftonline.com/%s/oauth2/v2.0/token";
  private static final String GRAPH_API_BASE = "https://graph.microsoft.com/v1.0";
  private static final String FOLDER_NAME = "project upload file";
  private static final String ENCODED_FOLDER = URLEncoder.encode(FOLDER_NAME, StandardCharsets.UTF_8).replace("+", "%20") + "/";

  private static final long CHUNK_SIZE = 5L * 1024 * 1024; // 5MB

  private final OkHttpClient httpClient = new OkHttpClient();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Timed(value = "onedrive.access.token", description = "Time taken to fetch OneDrive access token")
  private String getAccessToken() throws IOException {
    String tokenUrl = String.format(TOKEN_URL_TEMPLATE, tenantId);

    RequestBody formBody = new FormBody.Builder()
        .add("client_id", clientId)
        .add("scope", "https://graph.microsoft.com/.default")
        .add("client_secret", clientSecret)
        .add("grant_type", "client_credentials")
        .build();

    Request request = new Request.Builder()
        .url(tokenUrl)
        .post(formBody)
        .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        String errorBody = response.body() != null ? response.body().string() : "No response body";
        logger.error("Failed to get access token. Status: {}, Body: {}", response.code(), errorBody);
        throw new IOException("Failed to get access token: " + response.code() + " - " + errorBody);
      }

      String responseBody = response.body().string();
      JsonNode json = objectMapper.readTree(responseBody);
      String accessToken = json.get("access_token").asText();
      logger.info("Successfully obtained access token");
      return accessToken;
    }
  }

  private String getDriveUrl(DriveEndpoint endpoint) {
    String basePath;
    String endpointSuffix = endpoint.getSuffix();

    if (siteId != null && !siteId.isEmpty() && driveId != null && !driveId.isEmpty()) {
      basePath = "/sites/" + siteId + "/drives/" + driveId + "/";
    } else if (driveId != null && !driveId.isEmpty()) {
      basePath = "/drives/" + driveId + "/";
    } else if (userId != null && !userId.isEmpty()) {
      basePath = "/users/" + userId + "/drive/";
    } else {
      logger.warn("No site-id, drive-id, or user-id specified. Using default site drive.");
      basePath = "/sites/root/drive/";
    }

    return basePath + endpointSuffix;
  }

  @Timed(value = "onedrive.upload.file", description = "Time taken to upload file to OneDrive")
  public OneDriveUploadResult uploadLargeFile(String fileName, byte[] content) throws IOException {
    String accessToken = getAccessToken();
    String cleanFileName = sanitizeFileName(fileName);
    String encodedName = URLEncoder.encode(cleanFileName, StandardCharsets.UTF_8).replace("+", "%20");

    ensureFolderExists();

    String sessionUrl = GRAPH_API_BASE + getDriveUrl(DriveEndpoint.ROOT) + ENCODED_FOLDER + encodedName + ":/createUploadSession";
    String sessionRequestBody = String.format(
        "{\n" +
            "  \"item\": {\n" +
            "    \"@microsoft.graph.conflictBehavior\": \"rename\",\n" +
            "    \"name\": \"%s\"\n" +
            "  }\n" +
            "}", cleanFileName
    );

    RequestBody sessionBody = RequestBody.create(sessionRequestBody, MediaType.parse("application/json"));
    Request sessionRequest = new Request.Builder()
        .url(sessionUrl)
        .post(sessionBody)
        .addHeader(jwtHeader, "Bearer " + accessToken)
        .addHeader("Content-Type", "application/json")
        .build();

    String uploadUrl;
    try (Response response = httpClient.newCall(sessionRequest).execute()) {
      if (!response.isSuccessful()) {
        String errorBody = response.body() != null ? response.body().string() : "No response body";
        throw new IOException("Failed to create upload session: " + response.code() + " - " + errorBody);
      }
      JsonNode json = objectMapper.readTree(response.body().string());
      uploadUrl = json.get("uploadUrl").asText();

    }

    long totalSize = content.length;
    long start = 0;
    String fileId = null;

    while (start < totalSize) {
      long end = Math.min(start + CHUNK_SIZE - 1, totalSize - 1);
      byte[] chunk = new byte[(int) (end - start + 1)];
      System.arraycopy(content, (int) start, chunk, 0, chunk.length);

      RequestBody chunkBody = RequestBody.create(chunk, MediaType.parse("application/octet-stream"));
      Request chunkRequest = new Request.Builder()
          .url(uploadUrl)
          .put(chunkBody)
          .addHeader("Content-Range", "bytes " + start + "-" + end + "/" + totalSize)
          .addHeader("Content-Length", String.valueOf(chunk.length))
          .build();

      try (Response response = httpClient.newCall(chunkRequest).execute()) {
        if (!response.isSuccessful()) {
          String errorBody = response.body() != null ? response.body().string() : "No response body";
          throw new IOException("Chunk upload failed: " + response.code() + " - " + errorBody);
        }

        String responseBody = response.body().string();
        JsonNode json = objectMapper.readTree(responseBody);
        if (json.has("id")) {
          fileId = json.get("id").asText();
        }
        logger.info("Uploaded chunk: {}-{}/{}", start, end, totalSize);
      }

      start = end + 1;
    }

    String filePath = GRAPH_API_BASE + getDriveUrl(DriveEndpoint.ROOT) + ENCODED_FOLDER + encodedName;
    logger.info("File uploaded successfully: {} (ID: {})", cleanFileName, fileId);
    return new OneDriveUploadResult(fileId, filePath);
  }

  // IMPROVED: Better sanitization method
  private String sanitizeFileName(String fileName) {
    if (fileName == null || fileName.isEmpty()) {
      return fileName;
    }

    String sanitized = fileName
        .replaceAll("[<>:\"/\\\\|?*]", "_")    // Replace invalid characters with underscore
        .replaceAll("\\s+", " ")               // Normalize whitespace
        .replaceAll("\\s", "_")                // Replace spaces with underscores to avoid encoding issues
        .replaceAll("[\\p{Cntrl}]", "")        // Remove control characters
        .trim();                               // Remove leading/trailing whitespace

    sanitized = sanitized.replaceAll("_{2,}", "_");
    sanitized = sanitized.replaceAll("^_+|_+$", "");
    if (sanitized.isEmpty()) {
      sanitized = "file_" + System.currentTimeMillis();
    }
    if (sanitized.length() > 255) {
      String extension = "";
      int lastDotIndex = sanitized.lastIndexOf('.');
      if (lastDotIndex > 0) {
        extension = sanitized.substring(lastDotIndex);
        sanitized = sanitized.substring(0, lastDotIndex);
      }
      sanitized = sanitized.substring(0, 255 - extension.length()) + extension;
    }

    return sanitized;
  }

  @Timed(value = "onedrive.download.file", description = "Time taken to download file from OneDrive")
  public byte[] downloadFile(String fileName) throws IOException {
    String accessToken = getAccessToken();
    String encodedName = URLEncoder.encode(sanitizeFileName(fileName), StandardCharsets.UTF_8).replace("+", "%20");

    ensureFolderExists();
    String url = GRAPH_API_BASE + getDriveUrl(DriveEndpoint.ROOT) + ENCODED_FOLDER + encodedName + ":/content";

    Request request = new Request.Builder()
        .url(url)
        .get()
        .addHeader(jwtHeader, "Bearer " + accessToken)
        .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        String errorBody = response.body() != null ? response.body().string() : "No response body";
        logger.error("Failed to download file. Status: {}, Body: {}", response.code(), errorBody);
        throw new IOException("Failed to download file: " + response.code() + " - " + errorBody);
      }
      return response.body().bytes();
    }
  }

  // IMPROVED: Method to delete file with option for permanent deletion
  @Timed(value = "onedrive.delete.file", description = "Time taken to delete file from OneDrive")
  private boolean deleteFileAll(String fileName, String onedriveId, boolean permanentDelete) throws IOException {
    String accessToken = getAccessToken();
    String cleanFileName = sanitizeFileName(fileName);
    String filePath = FOLDER_NAME + "/" + cleanFileName;
    String itemId = onedriveId != null ? onedriveId : fetchOneDriveId(filePath);

    // Step 1: Delete from OneDrive (move to recycle bin or permanent)
    String deleteUrl = GRAPH_API_BASE + "/users/" + userId + "/drive/items/" + itemId;
    if (permanentDelete) {
      deleteUrl += "?permanentDelete=true";
    }

    Request request = new Request.Builder()
        .url(deleteUrl)
        .delete()
        .addHeader(jwtHeader, "Bearer " + accessToken)
        .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        logger.warn("Failed to delete file. Status: {}", response.code());
        return false;
      }
      logger.info("File {}: {}", permanentDelete ? "permanently deleted" : "deleted (moved to recycle bin)", cleanFileName);
    }

    // Step 2: Delete from second stage recycle bin (if permanentDelete is true)
    if (permanentDelete && siteId != null && !siteId.isEmpty()) {
      String secondStageUrl = GRAPH_API_BASE + "/sites/" + siteId + "/recycleBin/deleteAll";
      Request secondStageRequest = new Request.Builder()
          .url(secondStageUrl)
          .post(RequestBody.create("", null)) // POST with empty body
          .addHeader(jwtHeader, "Bearer " + accessToken)
          .build();

      try (Response secondStageResponse = httpClient.newCall(secondStageRequest).execute()) {
        if (!secondStageResponse.isSuccessful()) {
          logger.warn("Failed to delete from second stage recycle bin. Status: {}", secondStageResponse.code());
        } else {
          logger.info("File deleted from second stage recycle bin.");
        }
      }
    }

    return true;
  }

  // Overload method để giữ tương thích với code hiện tại
  public boolean deleteFile(String fileName, String onedriveId) throws IOException {
    return deleteFileAll(fileName, onedriveId, true);
  }

  @Timed(value = "onedrive.preview.file.stream", description = "Time taken to preview file stream from OneDrive")
  public InputStream previewFileStream(String fileName) throws IOException {
    String accessToken = getAccessToken();
    String encodedName = URLEncoder.encode(sanitizeFileName(fileName), StandardCharsets.UTF_8).replace("+", "%20");

    ensureFolderExists();
    String url = GRAPH_API_BASE + getDriveUrl(DriveEndpoint.ROOT) + ENCODED_FOLDER + encodedName + ":/content";

    Request request = new Request.Builder()
        .url(url)
        .get()
        .addHeader(jwtHeader, "Bearer " + accessToken)
        .build();

    Response response = httpClient.newCall(request).execute();
    if (!response.isSuccessful()) {
      String errorBody = "";
      try {
        if (response.body() != null) {
          errorBody = response.body().string();
        }
      } finally {
        response.close(); // Đảm bảo đóng response
      }
      logger.error("Failed to preview file stream. Status: {}, Body: {}", response.code(), errorBody);
      throw new IOException("Failed to preview file stream: " + response.code() + " - " + errorBody);
    }

    // Wrap InputStream để tự động đóng response
    return new FilterInputStream(response.body().byteStream()) {
      @Override
      public void close() throws IOException {
        try {
          super.close();
        } finally {
          response.close();
        }
      }
    };
  }

  @Timed(value = "onedrive.fetch.file.id", description = "Time taken to fetch OneDrive file ID")
  public String fetchOneDriveId(String filePath) throws IOException {
    System.out.println("Fetching OneDrive ID for file path: " + filePath);

    // Encode each path segment separately
    String[] parts = filePath.split("/");
    StringBuilder encodedPath = new StringBuilder();
    for (String part : parts) {
      if (!encodedPath.isEmpty()) encodedPath.append("/");
      encodedPath.append(URLEncoder.encode(part, StandardCharsets.UTF_8));
    }
    String encodedFilePath = encodedPath.toString();

    IOException lastException = null;
    for (int attempt = 0; attempt < 3; attempt++) {
      try {
        String accessToken = getAccessToken();
        String url = GRAPH_API_BASE + "/users/" + userId + "/drive/root:/" + encodedFilePath;
        Request request = new Request.Builder()
            .url(url)
            .get()
            .addHeader(jwtHeader, "Bearer " + accessToken)
            .addHeader("Accept", "application/json")
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
          if (!response.isSuccessful()) {
            throw new IOException("Failed to fetch file metadata: " + response.code());
          }
          JsonNode json = objectMapper.readTree(response.body().string());
          return json.get("id").asText();
        }
      } catch (IOException e) {
        lastException = e;
        try {
          Thread.sleep(1000); // Wait before retrying
        } catch (InterruptedException ignored) {
        }
      }
    }
    throw lastException;
  }

  private void ensureFolderExists() throws IOException {
    if (!folderExists()) {
      createFolderIfNotExists();
    }
  }

  private boolean folderExists() throws IOException {
    String accessToken = getAccessToken();
    String url = GRAPH_API_BASE + getDriveUrl(DriveEndpoint.CHILDREN);

    Request request = new Request.Builder()
        .url(url)
        .get()
        .addHeader(jwtHeader, "Bearer " + accessToken)
        .addHeader("Accept", "application/json")
        .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        String errorBody = response.body() != null ? response.body().string() : "No response body";
        logger.error("Failed to list children. Status: {}, Body: {}", response.code(), errorBody);
        throw new IOException("Failed to list children: " + response.code() + " - " + errorBody);
      }

      String responseBody = response.body().string();
      JsonNode json = objectMapper.readTree(responseBody);
      JsonNode valueNode = json.get("value");

      if (valueNode != null && valueNode.isArray()) {
        for (JsonNode item : valueNode) {
          JsonNode nameNode = item.get("name");
          if (nameNode != null && nameNode.asText().equals(FOLDER_NAME) && item.has("folder")) {
            return true;
          }
        }
      }
      return false;
    }
  }

  private void createFolderIfNotExists() throws IOException {
    String accessToken = getAccessToken();
    String url = GRAPH_API_BASE + getDriveUrl(DriveEndpoint.CHILDREN);
    String jsonBody = "{\n" +
        "  \"name\": \"" + FOLDER_NAME + "\",\n" +
        "  \"folder\": {},\n" +
        "  \"@microsoft.graph.conflictBehavior\": \"rename\"\n" +
        "}";

    RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));
    Request request = new Request.Builder()
        .url(url)
        .post(body)
        .addHeader(jwtHeader, "Bearer " + accessToken)
        .addHeader("Content-Type", "application/json")
        .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        String errorBody = response.body() != null ? response.body().string() : "No response body";
        logger.error("Failed to create folder. Status: {}, Body: {}", response.code(), errorBody);
        throw new IOException("Failed to create folder: " + response.code() + " - " + errorBody);
      }
      logger.info("Folder created successfully: {}", FOLDER_NAME);
    }
  }

  // Updated method to list available drives for application access
  public void listAvailableDrives() throws IOException {
    String accessToken = getAccessToken();

    // For application access, try listing sites first
    String url = GRAPH_API_BASE + "/sites";

    Request request = new Request.Builder()
        .url(url)
        .get()
        .addHeader(jwtHeader, "Bearer " + accessToken)
        .addHeader("Accept", "application/json")
        .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (response.isSuccessful()) {
        String responseBody = response.body().string();
        JsonNode json = objectMapper.readTree(responseBody);
        logger.info("Available sites: {}", json.toPrettyString());
      } else {
        String errorBody = response.body() != null ? response.body().string() : "No response body";
        logger.error("Failed to list sites. Status: {}, Body: {}", response.code(), errorBody);
      }
    }
  }

  // Method to get site information
  public void getSiteInfo() throws IOException {
    String accessToken = getAccessToken();
    String url = GRAPH_API_BASE + "/sites/root";

    Request request = new Request.Builder()
        .url(url)
        .get()
        .addHeader(jwtHeader, "Bearer " + accessToken)
        .addHeader("Accept", "application/json")
        .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (response.isSuccessful()) {
        String responseBody = response.body().string();
        JsonNode json = objectMapper.readTree(responseBody);
        logger.info("Root site info: {}", json.toPrettyString());
      } else {
        String errorBody = response.body() != null ? response.body().string() : "No response body";
        logger.error("Failed to get site info. Status: {}, Body: {}", response.code(), errorBody);
      }
    }
  }

  // Method to test connection
  public void testConnection() throws IOException {
    try {
      String accessToken = getAccessToken();
      logger.info("Successfully obtained access token");

      // Test get site info
      getSiteInfo();

      // Test list available drives/sites
      listAvailableDrives();

      // Test folder check
      boolean exists = folderExists();
      logger.info("Folder exists: {}", exists);

      if (!exists) {
        logger.info("Creating folder...");
        createFolderIfNotExists();
      }

    } catch (Exception e) {
      logger.error("Connection test failed", e);
      throw e;
    }
  }

  @Timed(value = "onedrive.stream.file.with.range", description = "Time taken to stream file with range from OneDrive")
  public InputStream streamFileWithRange(String fileName, String rangeHeader) throws IOException {
    String accessToken = getAccessToken();
    String encodedName = URLEncoder.encode(sanitizeFileName(fileName), StandardCharsets.UTF_8).replace("+", "%20");

    ensureFolderExists();
    String url = GRAPH_API_BASE + getDriveUrl(DriveEndpoint.ROOT) + ENCODED_FOLDER + encodedName + ":/content";

    Request.Builder builder = new Request.Builder()
        .url(url)
        .get()
        .addHeader(jwtHeader, "Bearer " + accessToken);

    if (rangeHeader != null && !rangeHeader.isEmpty()) {
      builder.addHeader("Range", rangeHeader);
    }

    Response response = httpClient.newCall(builder.build()).execute();
    if (!response.isSuccessful()) {
      try {
        String errorBody = response.body() != null ? response.body().string() : "No response body";
        logger.error("Failed to stream file with range: {} - {}", response.code(), errorBody);
      } finally {
        response.close();
      }
      throw new IOException("Failed to stream file with range: " + response.code());
    }

    // Wrap InputStream để tự động đóng response
    return new FilterInputStream(response.body().byteStream()) {
      @Override
      public void close() throws IOException {
        try {
          super.close();
        } finally {
          response.close();
        }
      }
    };
  }

  @Timed(value = "onedrive.get.file.size", description = "Time taken to get OneDrive file size")
  public long getFileSize(String fileName) throws IOException {
    String accessToken = getAccessToken();
    String cleanFileName = sanitizeFileName(fileName);
    String encodedName = URLEncoder.encode(cleanFileName, StandardCharsets.UTF_8).replace("+", "%20");

    ensureFolderExists();
    String url = GRAPH_API_BASE + getDriveUrl(DriveEndpoint.ROOT) + ENCODED_FOLDER + encodedName;

    Request request = new Request.Builder()
        .url(url)
        .get()
        .addHeader(jwtHeader, "Bearer " + accessToken)
        .addHeader("Accept", "application/json")
        .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        String errorBody = response.body() != null ? response.body().string() : "No response body";
        logger.error("Failed to get file size. Status: {}, Body: {}", response.code(), errorBody);
        throw new IOException("Failed to get file size: " + response.code() + " - " + errorBody);
      }

      JsonNode json = objectMapper.readTree(response.body().string());
      return json.get("size").asLong();
    }
  }

}