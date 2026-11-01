package com.recu.user.features.user.service;

import com.recu.common.web.error.BusinessException;
import com.recu.common.web.error.CommonErrorCode;
import com.recu.user.database.entity.UserEntity;
import com.recu.user.database.repository.UserRepository;
import com.recu.user.error.UserErrorCode;
import com.recu.user.features.user.domain.dto.UserLogoutRequestDTO;
import com.recu.user.features.user.domain.dto.UserResponseDTO;
import com.recu.user.features.user.service.GoogleUserInfoClient.GoogleUserInfo;
import com.recu.user.token.JwtProvider;
import com.recu.user.token.JwtProvider.JwtTokenException;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

  private final GoogleUserInfoClient googleUserInfoClient;
  private final UserRepository userRepository;
  private final JwtProvider jwtProvider;

  @Transactional
  public LoginResult signIn(String googleAccessToken) {
    GoogleUserInfo info = googleUserInfoClient.getUserInfo(googleAccessToken);

    // 같은 Google 계정의 중복 가입을 막고, 첫 로그인일 때만 사용자를 만든다.
    UserEntity user = userRepository.findByGoogleSub(info.id()).orElseGet(() -> createUser(info));

    // Google 토큰은 사용자 확인에만 쓰고, 이후 인증에는 우리 JWT를 사용한다.
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

  private UserEntity createUser(GoogleUserInfo info) {
    LocalDate now = LocalDate.now();

    // 신규 사용자는 프로필 미완료 상태로 생성한다.
    return userRepository.save(
        UserEntity.builder()
            .google_sub(info.id())
            .name(info.name())
            .career_years(null)
            .profile_version(0)
            .role("USER")
            .created_at(now)
            .updated_at(now)
            .build());
  }

  // 컨트롤러가 응답 바디와 두 토큰 헤더를 한 번에 조립하도록 묶은 내부 결과다.
  public record LoginResult(UserResponseDTO response, String accessToken, String refreshToken) {}

  @Transactional
  public void signOut(Long userId, UserLogoutRequestDTO request) {
    String refreshToken = request.getRefreshToken();

    userRepository
        .findById(userId)
        .filter(user -> refreshToken.equals(user.getRefresh_token()))
        .ifPresentOrElse(
            (user) -> userRepository.updateRefreshToken(userId, null),
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
