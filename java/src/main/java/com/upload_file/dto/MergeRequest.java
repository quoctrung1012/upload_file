package com.upload_file.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MergeRequest {
  public String filename;
  public int totalChunks;
  public String type;
  public long totalSize;
}
