package com.upload_file.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

@Entity
@Table(name = "files", indexes = {
    @Index(name = "idx_files_uploaded_by", columnList = "uploaded_by"),
    @Index(name = "idx_files_name_uploaded_by", columnList = "name, uploaded_by"),
    @Index(name = "idx_files_creation_date", columnList = "creation_date"),
    @Index(name = "idx_files_name", columnList = "name")
})
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"data"})
public class FileDB {
  @Id
  @GeneratedValue(generator = "uuid")
  @GenericGenerator(name = "uuid", strategy = "uuid2")
  private String id;
  private String name;
  private String path;
  private String type;
  private Long size;
  @Column(name = "creation_date")
  private String creationDate;

  @Column(name = "onedrive_id")
  private String oneDriveId;

  @Lob
  @Column(name = "data", columnDefinition = "LONGBLOB")
  private byte[] data;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(name = "uploaded_by", nullable = false)
  private String uploadedBy;

  public FileDB(String name, Long size, String type, byte[] data, String creationDate, String uploadedBy) {
    this.name = name;
    this.type = type;
    this.size = size;
    this.data = data;
    this.creationDate = creationDate;
    this.uploadedBy = uploadedBy;
    this.oneDriveId = null;
    this.path = null;
  }
}
