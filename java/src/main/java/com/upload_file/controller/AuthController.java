package com.upload_file.controller;

import com.upload_file.dto.AuthResponse;
import com.upload_file.dto.LoginRequest;
import com.upload_file.dto.RegisterRequest;
import com.upload_file.dto.RefreshTokenRequest;
import com.upload_file.service.AuthService;
import com.upload_file.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001", "http://localhost:4200"})
@Slf4j
public class AuthController {

  private final AuthService authService;
  private final JwtUtil jwtUtil;

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
    try {
      AuthResponse response = authService.register(request);
      if (response.getAccessToken() != null) {
        return ResponseEntity.ok(response);
      }
      return ResponseEntity.badRequest().body(response);
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(new AuthResponse(null, null, null, "Registration failed"));
    }
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
    try {
      AuthResponse response = authService.login(request);
      log.info("Login response for user {}: token={}",
          request.getUsername(),
          response.getAccessToken() != null ? "present" : "null");

      if (response.getAccessToken() != null) {
        return ResponseEntity.ok(response);
      }
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    } catch (Exception e) {
      log.error("Login error for user {}: {}", request.getUsername(), e.getMessage());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(new AuthResponse(null, null, null, "Login failed"));
    }
  }

  @PostMapping("/refresh")
  public ResponseEntity<AuthResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
    try {
      log.info("🔄 Refresh token request received");

      AuthResponse response = authService.refreshToken(request.getRefreshToken());
      if (response.getAccessToken() != null) {
        log.info("✅ Token refresh successful for user: {}", response.getUsername());
        return ResponseEntity.ok(response);
      }

      log.warn("❌ Token refresh failed - no access token in response");
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    } catch (Exception e) {
      log.error("❌ Token refresh error: {}", e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(new AuthResponse(null, null, null, "Token refresh failed: " + e.getMessage()));
    }
  }

  @PostMapping("/logout")
  public ResponseEntity<AuthResponse> logout(HttpServletRequest request) {
    try {
      String token = getTokenFromRequest(request);
      boolean success = authService.logout(token);

      if (success) {
        return ResponseEntity.ok(new AuthResponse(null, null, null, "Logout successful"));
      } else {
        return ResponseEntity.badRequest()
            .body(new AuthResponse(null, null, null, "Logout failed"));
      }
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(new AuthResponse(null, null, null, "Logout failed"));
    }
  }

  @GetMapping("/validate")
  public ResponseEntity<AuthResponse> validateToken(Authentication authentication, HttpServletRequest request) {
    try {
      if (authentication != null && authentication.isAuthenticated()) {
        String username = authentication.getName();
        String token = getTokenFromRequest(request);

        // Double check token validity
        if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
          return ResponseEntity.ok(new AuthResponse(null, null, username, "Token is valid"));
        }
      }
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(new AuthResponse(null, null, null, "Invalid token"));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(new AuthResponse(null, null, null, "Token validation failed"));
    }
  }

  private String getTokenFromRequest(HttpServletRequest request) {
    String bearerToken = request.getHeader("Authorization");
    if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
      return bearerToken.substring(7);
    }
    return null;
  }
}