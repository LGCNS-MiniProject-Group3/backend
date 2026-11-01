package com.recu.user.features.user.ctrl;

import com.recu.common.web.auth.CurrentUserId;
import com.recu.user.features.user.domain.dto.UserLoginRequestDTO;
import com.recu.user.features.user.domain.dto.UserLogoutRequestDTO;
import com.recu.user.features.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  // signIn: OAuth 로그인
  @Operation(summary = "OAuth 로그인", description = "구글 계정을 사용해 로그인")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "로그인 성공"),
    @ApiResponse(responseCode = "401", description = "로그인 실패(access token으로 Google userinfo 조회 실패)")
  })
  @PostMapping("/google")
  public ResponseEntity<?> signIn(@RequestBody UserLoginRequestDTO request) {
    UserService.LoginResult result = userService.signIn(request.getGoogleAccessToken());

    // 우리 서비스에서 사용할 JWT는 바디가 아닌 응답 헤더로 전달한다.
    return ResponseEntity.status(HttpStatus.OK)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + result.accessToken())
        .header("Refresh-Token", result.refreshToken())
        .body(result.response());
  }

  // signOut: OAuth 로그아웃
  @Operation(summary = "OAuth 로그아웃", description = "로그아웃 실행")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "로그아웃 성공"),
    @ApiResponse(responseCode = "401", description = "로그아웃 실패(토큰 유효성 확인)")
  })
  @PostMapping("/logout")
  public ResponseEntity<?> signOut(
      @CurrentUserId Long userId, @Valid @RequestBody UserLogoutRequestDTO request) {
    userService.signOut(userId, request);
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  // refreshToken: access token 재발급
  @Operation(summary = "access token 재발급", description = "access token이 만료되면 FE에서 자동으로 호출함")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "token 재발급 성공"),
    @ApiResponse(responseCode = "403", description = "token 재발급 실패(refresh token 유효성 확인)")
  })
  @PostMapping("/refresh")
  public ResponseEntity<?> refreshToken(
      @RequestHeader(value = "Refresh-Token", required = false) String refreshToken) {
    String accessToken = userService.refreshToken(refreshToken);

    return ResponseEntity.status(HttpStatus.OK)
        .header("Authorization", "Bearer " + accessToken)
        .build();
  }
}
