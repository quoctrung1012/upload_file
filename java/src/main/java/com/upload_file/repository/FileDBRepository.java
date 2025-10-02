package com.upload_file.repository;

import com.upload_file.entity.FileDB;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileDBRepository  extends JpaRepository<FileDB, String> {

  Page<FileDB> findByNameContainingOrderByCreationDateAsc(String name, Pageable pageable);

  Page<FileDB> findAllByOrderByCreationDateAsc(Pageable pageable);

  default Page<FileDB> findAllByNameOrderByCreationDate(String name, Pageable pageable) {
    if (name == null || name.trim().isEmpty()) {
      return findAllByOrderByCreationDateAsc(pageable);
    }
    return findByNameContainingOrderByCreationDateAsc(name, pageable);
  }

  Page<FileDB> findByNameContainingAndUploadedByOrderByCreationDateAsc(String name, String uploadedBy, Pageable pageable);

  Page<FileDB> findAllByUploadedByOrderByCreationDateAsc(String uploadedBy, Pageable pageable);

  default Page<FileDB> findAllByNameAndUserOrderByCreationDate(String name, String uploadedBy, Pageable pageable) {
    if (name == null || name.trim().isEmpty()) {
      return findAllByUploadedByOrderByCreationDateAsc(uploadedBy, pageable);
    }
    return findByNameContainingAndUploadedByOrderByCreationDateAsc(name, uploadedBy, pageable);
  }
}
