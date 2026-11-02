package com.recu.user.features.user.service;

import com.recu.common.web.error.BusinessException;
import com.recu.common.web.error.CommonErrorCode;
import com.recu.user.database.entity.UserEntity;
import com.recu.user.database.repository.UserRepository;
import com.recu.user.error.UserErrorCode;
import com.recu.user.features.user.domain.dto.UserLoginRequestDTO;
import com.recu.user.features.user.domain.dto.UserLogoutRequestDTO;
import com.recu.user.features.user.domain.dto.UserResponseDTO;
import com.recu.user.token.JwtProvider;
import com.recu.user.token.JwtProvider.JwtTokenException;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final JwtProvider jwtProvider;
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  public record LoginResult(UserResponseDTO response, String accessToken, String refreshToken) {}

  @Transactional
  public UserResponseDTO signUp(String email, String rawPassword, String name) {
    if (userRepository.findByEmail(email).isPresent()) {
      throw new BusinessException(UserErrorCode.EMAIL_ALREADY_EXISTS);
    }

    LocalDate now = LocalDate.now();

    UserEntity user =
        userRepository.save(
            UserEntity.builder()
                .email(email)
                .password_hash(passwordEncoder.encode(rawPassword))
                .name(name)
                .career_years(null)
                .profile_version(0)
                .refresh_token(null)
                .role("USER")
                .created_at(now)
                .updated_at(now)
                .build());

    return UserResponseDTO.builder()
        .userId(user.getUser_id())
        .name(user.getName())
        .profileCompleted(false)
        .build();
  }

  // 로그인시 진입하는곳
  @Transactional
  public LoginResult signIn(UserLoginRequestDTO request) {
    UserEntity user =
        userRepository
            .findByEmail(request.getEmail())
            // 로그인시 이메일 없으면 출력하는 에러
            .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));

    // 비밀번호가 아니면?
    if (!passwordEncoder.matches(request.getPassword(), user.getPassword_hash())) {
      throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }

    String accessToken = jwtProvider.createAccessToken(user.getUser_id(), user.getRole());
    String refreshToken = jwtProvider.createRefreshToken(user.getUser_id());

    userRepository.updateRefreshToken(user.getUser_id(), refreshToken);

    UserResponseDTO response =
        UserResponseDTO.builder()
            .userId(user.getUser_id())
            .name(user.getName())
            .profileCompleted(user.getCareer_years() != null)
            .build();

    return new LoginResult(response, accessToken, refreshToken);
  }

  @Transactional
  public void signOut(Long userId, UserLogoutRequestDTO request) {
    String refreshToken = request.getRefreshToken();

    userRepository
        .findById(userId)
        .filter(user -> refreshToken.equals(user.getRefresh_token()))
        .ifPresentOrElse(
            user -> userRepository.updateRefreshToken(userId, null),
            () -> {
              throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
            });
  }

  @Transactional
  public String refreshToken(String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN);
    }

    Long userId;
    try {
      userId = jwtProvider.parseRefreshToken(refreshToken);
    } catch (JwtTokenException exception) {
      throw new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN);
    }

    UserEntity user =
        userRepository
            .findById(userId)
            .filter(found -> refreshToken.equals(found.getRefresh_token()))
            .orElseThrow(() -> new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN));

    return jwtProvider.createAccessToken(user.getUser_id(), user.getRole());
  }
}
