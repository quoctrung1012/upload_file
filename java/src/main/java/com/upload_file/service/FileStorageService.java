package com.upload_file.service;

import com.upload_file.entity.FileDB;
import com.upload_file.repository.FileDBRepository;
import io.micrometer.core.annotation.Timed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service chỉ chịu trách nhiệm CRUD operations với database
 * Không chứa business logic phức tạp
 */
@Service
@Transactional
public class FileStorageService {

  @Autowired
  private FileDBRepository fileDBRepository;

  @Timed(value = "file.save", description = "Time taken to save file to database")
  public void save(FileDB fileDB) {
    fileDBRepository.save(fileDB);
  }

  @Timed(value = "file.get", description = "Time taken to get file by ID")
  @Transactional(readOnly = true)
  public FileDB getFile(String id) {
    return fileDBRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("File not found with id: " + id));
  }

  @Timed(value = "file.get_all", description = "Time taken to get all files")
  @Transactional(readOnly = true)
  public Page<FileDB> getAllFiles(String search, Pageable pageable, String username, boolean isAdmin) {
    if (isAdmin) {
      return fileDBRepository.findAllByNameOrderByCreationDate(search.trim(), pageable);
    } else {
      return fileDBRepository.findAllByNameAndUserOrderByCreationDate(search.trim(), username, pageable);
    }
  }

  @Timed(value = "file.delete", description = "Time taken to delete file from database")
  public void deleteById(String id) {
    fileDBRepository.deleteById(id);
  }

  @Transactional(readOnly = true)
  public boolean existsById(String id) {
    return fileDBRepository.existsById(id);
  }
}