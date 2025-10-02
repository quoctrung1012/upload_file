package com.upload_file.config;

import com.upload_file.repository.UserRepository;
import com.upload_file.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtUtil jwtUtil;

  @Autowired
  private UserRepository userRepository;

  @Override
  protected void doFilterInternal(@NotNull HttpServletRequest request,
                                  @NotNull HttpServletResponse response,
                                  @NotNull FilterChain filterChain) throws ServletException, IOException {

    String requestURI = request.getRequestURI();

    try {
      String token = getTokenFromRequest(request);

      if (StringUtils.hasText(token)) {

        // Kiểm tra từng bước
        boolean isValid = jwtUtil.validateToken(token);

        boolean isExpired = jwtUtil.isTokenExpired(token);

        boolean isRefresh = jwtUtil.isRefreshToken(token);

        if (isValid && !isExpired) {
          if (!isRefresh) {
            String username = jwtUtil.getUsernameFromToken(token);

            if (StringUtils.hasText(username) && SecurityContextHolder.getContext().getAuthentication() == null) {
              // Lấy authorities từ database
              List<SimpleGrantedAuthority> authorities = getUserAuthorities(username);

              UsernamePasswordAuthenticationToken authentication =
                  new UsernamePasswordAuthenticationToken(username, null, authorities);

              authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
              SecurityContextHolder.getContext().setAuthentication(authentication);

            } else {
              log.warn("⚠️ Username empty or authentication already exists. Username: {}, Auth exists: {}",
                  username, SecurityContextHolder.getContext().getAuthentication() != null);
            }
          } else {
            log.warn("🔄 Attempted to use refresh token as access token from IP: {}", getClientIpAddress(request));
          }
        }
      } else {
        log.debug("🔍 No token found in request to: {}", requestURI);
      }
    } catch (Exception e) {
      log.error("💥 Cannot set user authentication for {}: {}", requestURI, e.getMessage(), e);
      SecurityContextHolder.clearContext();
    }

    // Log final authentication status
    String currentUser = SecurityContextHolder.getContext().getAuthentication() != null ?
        SecurityContextHolder.getContext().getAuthentication().getName() : "anonymous";

    filterChain.doFilter(request, response);
  }

  private String getTokenFromRequest(HttpServletRequest request) {
    String bearerToken = request.getHeader("Authorization");

    if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
      return bearerToken.substring(7);
    }
    return null;
  }

  private String getClientIpAddress(HttpServletRequest request) {
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (StringUtils.hasText(xForwardedFor)) {
      return xForwardedFor.split(",")[0].trim();
    }

    String xRealIP = request.getHeader("X-Real-IP");
    if (StringUtils.hasText(xRealIP)) {
      return xRealIP;
    }

    return request.getRemoteAddr();
  }

  private List<SimpleGrantedAuthority> getUserAuthorities(String username) {
    try {
      return userRepository.findByUsername(username)
          .map(user -> List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
          .orElse(List.of(new SimpleGrantedAuthority("ROLE_USER")));
    } catch (Exception e) {
      log.warn("Error fetching user authorities for {}: {}", username, e.getMessage());
      return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
  }
}