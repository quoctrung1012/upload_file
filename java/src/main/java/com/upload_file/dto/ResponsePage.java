package com.upload_file.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ResponsePage {
  private List<?> items;
  private int pageNumber;
  private long totalElements;
  private int totalPages;
}
