package com.recu.user.features.user.ctrl;

import com.recu.common.web.auth.CurrentUserId;
import com.recu.user.features.user.domain.dto.UserLoginRequestDTO;
import com.recu.user.features.user.domain.dto.UserLogoutRequestDTO;
import com.recu.user.features.user.domain.dto.UserResponseDTO;
import com.recu.user.features.user.domain.dto.UserSignupRequestDTO;
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

  // signUp: 이메일·비밀번호 회원가입
  @Operation(summary = "회원가입", description = "이메일·비밀번호·이름으로 가입")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "가입 성공"),
    @ApiResponse(responseCode = "400", description = "입력 형식 오류"),
    @ApiResponse(responseCode = "409", description = "이미 가입된 이메일")
  })
  @PostMapping("/signup")
  public ResponseEntity<UserResponseDTO> signUp(@Valid @RequestBody UserSignupRequestDTO request) {
    UserResponseDTO response =
        userService.signUp(request.getEmail(), request.getPassword(), request.getName());

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  // signIn: 이메일·비밀번호 로그인
  @Operation(summary = "로그인", description = "이메일·비밀번호를 확인하고 Access·Refresh 토큰을 발급")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "로그인 성공"),
    @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치")
  })
  @PostMapping("/login")
  public ResponseEntity<UserResponseDTO> signIn(@Valid @RequestBody UserLoginRequestDTO request) {
    UserService.LoginResult result = userService.signIn(request);

    // 우리 서비스에서 사용할 JWT는 바디가 아닌 응답 헤더로 전달한다.
    return ResponseEntity.status(HttpStatus.OK)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + result.accessToken())
        .header("Refresh-Token", result.refreshToken())
        .body(result.response());
  }

  // signOut: 로그아웃
  @Operation(summary = "로그아웃", description = "로그아웃 실행")
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
