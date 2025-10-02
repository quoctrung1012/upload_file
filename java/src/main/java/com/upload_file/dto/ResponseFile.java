package com.upload_file.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ResponseFile {
  private String id;
  private String name;
  private String type;
  private String creationDate;
  private long size;

}