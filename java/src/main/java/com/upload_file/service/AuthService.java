package com.upload_file.service;

import com.upload_file.dto.AuthResponse;
import com.upload_file.dto.LoginRequest;
import com.upload_file.dto.RegisterRequest;
import com.upload_file.entity.User;
import com.upload_file.repository.UserRepository;
import com.upload_file.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final JwtUtil jwtUtil;
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12); // Tăng strength

  // Password pattern: ít nhất 8 ký tự, có chữ hoa, chữ thường, số và ký tự đặc biệt
  private static final Pattern PASSWORD_PATTERN = Pattern.compile(
      "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$"
  );

  // Email pattern
  private static final Pattern EMAIL_PATTERN = Pattern.compile(
      "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$"
  );

  private String currentTimeCreate() {
    return String.valueOf(System.currentTimeMillis());
  }

  public AuthResponse register(RegisterRequest request) {
    // Validate input
    if (!StringUtils.hasText(request.getUsername()) ||
        !StringUtils.hasText(request.getPassword()) ||
        !StringUtils.hasText(request.getEmail())) {
      return new AuthResponse(null, null, null, "All fields are required");
    }

    // Validate username length and characters
    if (request.getUsername().length() < 3 || request.getUsername().length() > 50) {
      return new AuthResponse(null, null, null, "Username must be between 3 and 50 characters");
    }

    // Validate email format
    if (!EMAIL_PATTERN.matcher(request.getEmail()).matches()) {
      return new AuthResponse(null, null, null, "Invalid email format");
    }

    // Validate password strength
    if (!PASSWORD_PATTERN.matcher(request.getPassword()).matches()) {
      return new AuthResponse(null, null, null,
          "Password must be at least 8 characters with uppercase, lowercase, number and special character");
    }

    // Check if username already exists
    if (userRepository.existsByUsername(request.getUsername())) {
      return new AuthResponse(null, null, null, "Username already exists");
    }

    // Check if email already exists
    if (userRepository.existsByEmail(request.getEmail())) {
      return new AuthResponse(null, null, null, "Email already exists");
    }

    try {
      User user = new User();
      user.setUsername(request.getUsername().trim());
      user.setPassword(passwordEncoder.encode(request.getPassword()));
      user.setEmail(request.getEmail().trim().toLowerCase());
      user.setCreationDate(currentTimeCreate());

      userRepository.save(user);

      String userRole = user.getRole() != null ? String.valueOf(user.getRole()) : "USER"; // Lấy role từ User entity
      String accessToken = jwtUtil.generateToken(user.getUsername(), "ROLE_" + userRole);
      String refreshToken = jwtUtil.generateRefreshToken(user.getUsername());

      return new AuthResponse(accessToken, refreshToken, user.getUsername(), "Registration successful");
    } catch (Exception e) {
      return new AuthResponse(null, null, null, "Registration failed. Please try again.");
    }
  }

  public AuthResponse login(LoginRequest request) {
    // Validate input
    if (!StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPassword())) {
      return new AuthResponse(null, null, null, "Username and password are required");
    }

    try {
      User user = userRepository.findByUsername(request.getUsername().trim())
          .orElse(null);

      if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
        // Sử dụng dummy password encoding để tránh timing attack
        if (user == null) {
          passwordEncoder.encode("dummy-password");
        }
        return new AuthResponse(null, null, null, "Invalid username or password");
      }

      String userRole = user.getRole() != null ? String.valueOf(user.getRole()) : "USER"; // Lấy role từ User entity
      String accessToken = jwtUtil.generateToken(user.getUsername(), "ROLE_" + userRole);
      String refreshToken = jwtUtil.generateRefreshToken(user.getUsername());

      return new AuthResponse(accessToken, refreshToken, user.getUsername(), "Login successful");
    } catch (Exception e) {
      return new AuthResponse(null, null, null, "Login failed. Please try again.");
    }
  }

  public AuthResponse refreshToken(String refreshToken) {
    try {
      if (!StringUtils.hasText(refreshToken)) {
        return new AuthResponse(null, null, null, "Refresh token is required");
      }

      if (!jwtUtil.validateToken(refreshToken) || !jwtUtil.isRefreshToken(refreshToken)) {
        return new AuthResponse(null, null, null, "Invalid refresh token");
      }

      String username = jwtUtil.getUsernameFromToken(refreshToken);

      // Verify user still exists
      if (!userRepository.existsByUsername(username)) {
        return new AuthResponse(null, null, null, "User not found");
      }

      User user = userRepository.findByUsername(username).orElse(null);
      if (user == null) {
        return new AuthResponse(null, null, null, "User not found");
      }
      String userRole = user.getRole() != null ? String.valueOf(user.getRole()) : "USER";
      String newAccessToken = jwtUtil.generateToken(username, "ROLE_" + userRole);
      String newRefreshToken = jwtUtil.generateRefreshToken(username);

      return new AuthResponse(newAccessToken, newRefreshToken, username, "Token refreshed successfully");
    } catch (Exception e) {
      return new AuthResponse(null, null, null, "Token refresh failed");
    }
  }

  public boolean logout(String token) {
    try {
      if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
        jwtUtil.blacklistToken(token);
        return true;
      }
      return false;
    } catch (Exception e) {
      return false;
    }
  }
}