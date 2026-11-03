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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
// @RequestMapping("/api/auth")

@Slf4j
public class UserController {

  private final UserService userService;

  @Autowired
  public UserController(UserService userService) {

    this.userService = userService;
  }

  // 회원가입
  @PostMapping("/api/auth/signup")
  public ResponseEntity<UserResponseDTO> signUp(@Valid @RequestBody UserSignupRequestDTO request) {

    // postman으로 입력 받
    UserResponseDTO response =
        userService.signUp(request.getEmail(), request.getPassword(), request.getName());

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/api/auth/login")
  public ResponseEntity<UserResponseDTO> login(@Valid @RequestBody UserLoginRequestDTO request) {

    UserService.LoginResult result = userService.signIn(request);

    return ResponseEntity.ok()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + result.accessToken())
        .header("Refresh-Token", result.refreshToken())
        .body(result.response());
  }

  //  signOut: OAuth 로그아웃
  @Operation(summary = "OAuth 로그아웃", description = "로그아웃 실행")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "로그아웃 성공"),
    @ApiResponse(responseCode = "401", description = "로그아웃 실패(토큰 유효성 확인)")
  })
  @PostMapping("/logout")
  public ResponseEntity<?> signOut(
      @CurrentUserId Long userId, @Valid @RequestBody UserLogoutRequestDTO request) {
    userService.signOut(userId, request);
    log.info("========== 로그아웃 성공 ==========");
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
