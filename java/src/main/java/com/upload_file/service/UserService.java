package com.upload_file.service;

import com.upload_file.entity.User;
import com.upload_file.repository.UserRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

  private final UserRepository userRepository;

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    User user = userRepository.findByUsername(username)
        .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

    return new CustomUserDetails(user);
  }

  public Optional<User> findByUsername(String username) {
    return userRepository.findByUsername(username);
  }

  public User save(User user) {
    return userRepository.save(user);
  }

  public boolean existsByUsername(String username) {
    return userRepository.existsByUsername(username);
  }

  public boolean existsByEmail(String email) {
    return userRepository.existsByEmail(email);
  }

  // Custom UserDetails implementation
  @Getter
  public static class CustomUserDetails implements UserDetails {
    private final User user;

    public CustomUserDetails(User user) {
      this.user = user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
      List<GrantedAuthority> authorities = new ArrayList<>();
      // Thêm prefix "ROLE_" cho Spring Security
      authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
      return authorities;
    }

    @Override
    public String getPassword() {
      return user.getPassword();
    }

    @Override
    public String getUsername() {
      return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
      return true;
    }

    @Override
    public boolean isAccountNonLocked() {
      return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
      return true;
    }

    @Override
    public boolean isEnabled() {
      return true;
    }

  }
}