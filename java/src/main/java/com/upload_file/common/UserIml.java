package com.upload_file.common;

import com.upload_file.entity.User;
import com.upload_file.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

public interface UserIml {
  Logger log = LoggerFactory.getLogger(UserIml.class);

  @Autowired
  UserService userService = null;

  default String getCurrentUsername() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null) {

      if (authentication.getPrincipal() instanceof UserDetails userDetails) {
        return userDetails.getUsername();
      } else if (authentication.getPrincipal() instanceof String username) {
        return username;
      } else {
        // Fallback to getName()
        return authentication.getName();
      }
    }

    log.warn("❌ No authentication found, returning anonymous");
    return "anonymous";
  }

  /**
   * Lấy User entity của user hiện tại
   */
  default User getCurrentUser() {
    String username = getCurrentUsername();
    if (userService != null && !"anonymous".equals(username)) {
      return userService.findByUsername(username).orElse(null);
    }
    return null;
  }

  /**
   * Kiểm tra user hiện tại có role cụ thể không
   */
  default boolean hasRole(String role) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.isAuthenticated()) {
      return authentication.getAuthorities().stream()
          .map(GrantedAuthority::getAuthority)
          .anyMatch(authority -> authority.equals("ROLE_" + role));
    }
    return false;
  }

  default boolean isAdmin() {
    return hasRole("ADMIN");
  }

  default boolean isUser() {
    return hasRole("USER");
  }

  default boolean canAccessFile(String fileUploadedBy) {
    String currentUser = getCurrentUsername();
    if ("anonymous".equals(currentUser)) {
      return false;
    }
    if (isAdmin()) {
      return true;
    }
    return currentUser.equals(fileUploadedBy);
  }

  default boolean canDeleteFile(String fileUploadedBy) {
    return canAccessFile(fileUploadedBy);
  }

  /**
   * Lấy role của user hiện tại
   */
  default String getCurrentUserRole() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.isAuthenticated()) {
      return authentication.getAuthorities().stream()
          .map(GrantedAuthority::getAuthority)
          .filter(authority -> authority.startsWith("ROLE_"))
          .map(authority -> authority.substring(5)) // Remove "ROLE_" prefix
          .findFirst()
          .orElse("UNKNOWN");
    }
    return "ANONYMOUS";
  }
}