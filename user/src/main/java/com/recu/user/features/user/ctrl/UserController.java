package com.recu.user.features.user.ctrl;

import com.recu.user.features.user.domain.dto.UserResponseDTO;
import com.recu.user.features.user.domain.dto.UserSignupRequestDTO;
import com.recu.user.features.user.service.UserService;
import com.recu.user.features.user.vo.Greeting;
import io.micrometer.core.annotation.Timed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
// @RequestMapping("/api/auth")

@RequestMapping("/")
@RequiredArgsConstructor
@Slf4j
public class UserController {

  private final UserService userService;
  private Environment env; // 이거는 new-toy-msa
  private Greeting greeting;

  @Autowired
  public UserController(Environment env, Greeting greeting, UserService userService) {
    this.env = env;
    this.greeting = greeting;
    this.userService = userService;
  }

  @GetMapping("/health-check")
  @Timed(value = "user.status")
  public String status() {
    return String.format(
        "It's Working in User Service"
            + ", port(local.server.port)="
            + env.getProperty("local.server.port")
            + ", port(server.port)="
            + env.getProperty("server.port")
            + ", gateway ip(env)="
            + env.getProperty("gateway.ip")
            + ", token secret key="
            + env.getProperty("token.secret")
            + ", token expiration time="
            + env.getProperty("token.expiration-time"));
  }

  @GetMapping("/welcome")
  @Timed(value = "user.welcome")
  public String welcome(HttpServletRequest request) {
    log.info(
        "users.welcome ip: {}, {}, {}, {}",
        request.getRemoteAddr(),
        request.getRemoteHost(),
        request.getRequestURI(),
        request.getRequestURL());

    //        return env.getProperty("greeting.message");
    return greeting.getMessage();
  }

  // 회원가입
  @PostMapping("/api/auth/signup")
  public ResponseEntity<UserResponseDTO> signUp(@Valid @RequestBody UserSignupRequestDTO request) {

    // postman으로 입력 받
    UserResponseDTO response =
        userService.signUp(request.getEmail(), request.getPassword(), request.getName());

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
  //    @PostMapping("/api/auth/login")
  //    public ResponseEntity<UserResponseDTO> login(
  //        @Valid @RequestBody UserLoginRequestDTO request) {
  //
  //        UserService.LoginResult result = userService.signIn(request);
  //
  //        return ResponseEntity.ok()
  //            .header(HttpHeaders.AUTHORIZATION, "Bearer " + result.accessToken())
  //            .header("Refresh-Token", result.refreshToken())
  //            .body(result.response());
  //    }

  //  @PostMapping("/google")
  //  public ResponseEntity<?> sinIn(@RequestBody UserLoginRequestDTO request) {
  //    UserService.LoginResult result = userService.signIn(request.getGoogleAccessToken());
  //
  //    // 우리 서비스에서 사용할 JWT는 바디가 아닌 응답 헤더로 전달한다.
  //    return ResponseEntity.status(HttpStatus.OK)
  //        .header(HttpHeaders.AUTHORIZATION, "Bearer " + result.accessToken())
  //        .header("Refresh-Token", result.refreshToken())
  //        .body(result.response());
  //  }
  //
  //  // signOut: OAuth 로그아웃
  //  @Operation(summary = "OAuth 로그아웃", description = "로그아웃 실행")
  //  @ApiResponses({
  //    @ApiResponse(responseCode = "204", description = "로그아웃 성공"),
  //    @ApiResponse(responseCode = "401", description = "로그아웃 실패(토큰 유효성 확인)")
  //  })
  //  @PostMapping("/logout")
  //  public ResponseEntity<?> signOut(
  //      @CurrentUserId Long userId, @Valid @RequestBody UserLogoutRequestDTO request) {
  //    userService.signOut(userId, request);
  //    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  //  }
  //
  //  // refreshToken: access token 재발급
  //  @Operation(summary = "access token 재발급", description = "access token이 만료되면 FE에서 자동으로 호출함")
  //  @ApiResponses({
  //    @ApiResponse(responseCode = "200", description = "token 재발급 성공"),
  //    @ApiResponse(responseCode = "403", description = "token 재발급 실패(refresh token 유효성 확인)")
  //  })
  //  @PostMapping("/refresh")
  //  public ResponseEntity<?> refreshToken(
  //      @RequestHeader(value = "Refresh-Token", required = false) String refreshToken) {
  //    String accessToken = userService.refreshToken(refreshToken);
  //
  //    return ResponseEntity.status(HttpStatus.OK)
  //        .header("Authorization", "Bearer " + accessToken)
  //        .build();
  //  }
}
