package com.upload_file.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

@Slf4j
@Component
public class JwtUtil {

  @Value("${jwt.secret}")
  private String jwtSecret;

  @Value("${jwt.expiration}")
  private long jwtExpirationMs;

  @Value("${jwt.refresh-expiration}")
  private long refreshExpirationMs;

  @Value("${spring.application.name}")
  private static String applicationName;

  private static final String ISSUER = applicationName;

  // Token blacklist để lưu trữ các token đã bị logout
  private final Set<String> blacklistedTokens = ConcurrentHashMap.newKeySet();

  private SecretKey getSigningKey() {
    // Đảm bảo secret key đủ mạnh (ít nhất 256 bit)
    if (jwtSecret.length() < 32) {
      throw new IllegalArgumentException("JWT secret must be at least 256 bits (32 characters)");
    }
    return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
  }

  // Tạo parser builder nhất quán cho tất cả methods
  private JwtParserBuilder createParserBuilder() {
    return Jwts.parserBuilder()
        .setSigningKey(getSigningKey())
        .requireIssuer(ISSUER);
  }

  public boolean isTokenValid(String token) {
    return validateToken(token);
  }

  /**
   * Lấy tất cả roles từ token
   */
  public String getRolesFromToken(String token) {
    try {
      Claims claims = createParserBuilder()
          .build()
          .parseClaimsJws(token)
          .getBody();

      Object rolesObj = claims.get("roles");
      if (rolesObj instanceof String roles) {
        return roles;
      }

      return "ROLE_USER"; // Default role nếu không có roles trong token
    } catch (JwtException e) {
      log.warn("❌ Error getting roles from token: {}", e.getMessage());
      return "";
    }
  }

  /**
   * Kiểm tra user có role cụ thể từ token
   */
  public boolean hasRole(String token, String role) {
    try {
      String roles = getRolesFromToken(token);
      return roles.contains("ROLE_" + role);
    } catch (Exception e) {
      log.warn("❌ Error checking role from token: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Generate token with roles
   */
  public String generateToken(String username, String roles) {
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + jwtExpirationMs * 1000);

    String token = Jwts.builder()
        .setSubject(username)
        .setIssuedAt(now)
        .setExpiration(expiryDate)
        .setIssuer(ISSUER)
        .claim("roles", roles) // Thêm roles vào token
        .signWith(getSigningKey(), SignatureAlgorithm.HS256)
        .compact();

    log.debug("🔑 Generated access token for user: {} with roles: {} (expires: {})", username, roles, expiryDate);
    return token;
  }

  public String generateRefreshToken(String username) {
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + refreshExpirationMs * 1000);

    String token = Jwts.builder()
        .setSubject(username)
        .setIssuedAt(now)
        .setExpiration(expiryDate)
        .setIssuer(ISSUER)
        .claim("type", "refresh") // Đánh dấu là refresh token
        .signWith(getSigningKey(), SignatureAlgorithm.HS256)
        .compact();

    log.debug("🔄 Generated refresh token for user: {} (expires: {})", username, expiryDate);
    return token;
  }

  public String getUsernameFromToken(String token) {
    try {
      Claims claims = createParserBuilder()
          .build()
          .parseClaimsJws(token)
          .getBody();

      String username = claims.getSubject();
      log.debug("👤 Extracted username from token: {}", username);
      return username;
    } catch (JwtException e) {
      log.error("❌ Failed to extract username from token: {}", e.getMessage());
      throw new IllegalArgumentException("Invalid JWT token", e);
    }
  }

  public boolean validateToken(String token) {
    try {
      log.debug("🔍 Validating token: {}...", token.substring(0, Math.min(20, token.length())));

      if (blacklistedTokens.contains(token)) {
        log.warn("🚫 Token is blacklisted");
        return false;
      }

      Claims claims = createParserBuilder()
          .build()
          .parseClaimsJws(token)
          .getBody();

      log.debug("✅ Token is valid for user: {}", claims.getSubject());
      return true;
    } catch (ExpiredJwtException e) {
      log.debug("⏰ Token is expired: {}", e.getMessage());
      return false;
    } catch (IllegalArgumentException | JwtException e) {
      return false;
    }
  }

  public boolean isTokenExpired(String token) {
    try {
      Claims claims = createParserBuilder()
          .build()
          .parseClaimsJws(token)
          .getBody();

      Date expiration = claims.getExpiration();
      Date now = new Date();
      boolean expired = expiration.before(now);

      log.debug("⏰ Token expiration check - expires: {}, now: {}, expired: {}", expiration, now, expired);
      return expired;
    } catch (ExpiredJwtException e) {
      log.debug("⏰ Token is expired (caught ExpiredJwtException): {}", e.getMessage());
      return true;
    } catch (JwtException e) {
      log.warn("❌ Error checking token expiration: {}", e.getMessage());
      return true;
    }
  }

  public boolean isRefreshToken(String token) {
    try {
      Claims claims = createParserBuilder()
          .build()
          .parseClaimsJws(token)
          .getBody();

      boolean isRefresh = "refresh".equals(claims.get("type"));
      log.debug("🔄 Token type check - is refresh: {}", isRefresh);
      return isRefresh;
    } catch (JwtException e) {
      log.warn("❌ Error checking token type: {}", e.getMessage());
      return false;
    }
  }

  public void blacklistToken(String token) {
    blacklistedTokens.add(token);
    log.info("🚫 Token blacklisted: {}...", token.substring(0, Math.min(20, token.length())));
  }

  public Date getExpirationDateFromToken(String token) {
    try {
      Claims claims = createParserBuilder()
          .build()
          .parseClaimsJws(token)
          .getBody();
      return claims.getExpiration();
    } catch (JwtException e) {
      log.warn("❌ Error getting expiration date: {}", e.getMessage());
      return null;
    }
  }
}