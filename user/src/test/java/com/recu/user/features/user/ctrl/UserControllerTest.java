package com.recu.user.features.user.ctrl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recu.user.database.entity.UserEntity;
import com.recu.user.database.repository.UserJobFieldRepository;
import com.recu.user.database.repository.UserRepository;
import com.recu.user.database.repository.UserSkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 이메일·비밀번호 회원가입·로그인과 토큰 재발급·로그아웃이 design.md 4장 응답대로 나가는지. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerTest {

  private static final String EMAIL = "user@example.com";
  private static final String PASSWORD = "password123";

  @Autowired MockMvc mockMvc;
  @Autowired UserRepository users;
  @Autowired UserJobFieldRepository jobFields;
  @Autowired UserSkillRepository skills;

  @BeforeEach
  void setUp() {
    skills.deleteAll();
    jobFields.deleteAll();
    users.deleteAll();
  }

  @Test
  @DisplayName("가입하면 201과 회원 번호·이름, 비밀번호는 BCrypt 해시로만 저장")
  void signUp() throws Exception {
    signUp(EMAIL, PASSWORD, "홍길동")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.userId").isNumber())
        .andExpect(jsonPath("$.name").value("홍길동"))
        .andExpect(jsonPath("$.profileCompleted").value(false));

    UserEntity saved = users.findByEmail(EMAIL).orElseThrow();
    assertThat(saved.getPassword_hash()).isNotEqualTo(PASSWORD).startsWith("$2");
    assertThat(saved.getRole()).isEqualTo("USER");
    assertThat(saved.getProfile_version()).isZero();
  }

  @Test
  @DisplayName("같은 이메일로 다시 가입하면 409 EMAIL_ALREADY_EXISTS")
  void duplicateEmail() throws Exception {
    signUp(EMAIL, PASSWORD, "홍길동").andExpect(status().isCreated());

    signUp(EMAIL, PASSWORD, "다른사람")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    assertThat(users.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("이메일 형식이 아니면 400 INVALID_INPUT, field email")
  void invalidEmail() throws Exception {
    signUp("not-email", PASSWORD, "홍길동")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.field").value("email"));
  }

  @Test
  @DisplayName("비밀번호가 8자보다 짧으면 400 INVALID_INPUT, field password")
  void shortPassword() throws Exception {
    signUp(EMAIL, "short", "홍길동")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.field").value("password"));
    assertThat(users.count()).isZero();
  }

  @Test
  @DisplayName("로그인하면 200과 Authorization·Refresh-Token 헤더, Refresh 토큰은 DB에 저장")
  void login() throws Exception {
    signUp(EMAIL, PASSWORD, "홍길동").andExpect(status().isCreated());

    String refreshToken =
        login(EMAIL, PASSWORD)
            .andExpect(status().isOk())
            .andExpect(header().string("Authorization", startsWith("Bearer ")))
            .andExpect(header().exists("Refresh-Token"))
            .andExpect(jsonPath("$.name").value("홍길동"))
            .andExpect(jsonPath("$.profileCompleted").value(false))
            .andReturn()
            .getResponse()
            .getHeader("Refresh-Token");

    assertThat(users.findByEmail(EMAIL).orElseThrow().getRefresh_token()).isEqualTo(refreshToken);
  }

  @Test
  @DisplayName("비밀번호가 틀리거나 없는 이메일이면 같은 401 INVALID_CREDENTIALS")
  void wrongCredentials() throws Exception {
    signUp(EMAIL, PASSWORD, "홍길동").andExpect(status().isCreated());

    login(EMAIL, "wrong-password")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    login("nobody@example.com", PASSWORD)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  @DisplayName("로그인 때 받은 Refresh 토큰으로 /api/auth/refresh를 부르면 새 Access 토큰")
  void refresh() throws Exception {
    signUp(EMAIL, PASSWORD, "홍길동").andExpect(status().isCreated());
    String refreshToken = loggedInRefreshToken();

    mockMvc
        .perform(post("/api/auth/refresh").header("Refresh-Token", refreshToken))
        .andExpect(status().isOk())
        .andExpect(header().string("Authorization", startsWith("Bearer ")));
  }

  @Test
  @DisplayName("/api/auth/logout은 204, 저장된 Refresh 토큰을 지워 이후 재발급은 403")
  void logout() throws Exception {
    signUp(EMAIL, PASSWORD, "홍길동").andExpect(status().isCreated());
    String refreshToken = loggedInRefreshToken();
    Long userId = users.findByEmail(EMAIL).orElseThrow().getUser_id();

    mockMvc
        .perform(
            post("/api/auth/logout")
                .header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"refreshToken": "%s"}
                    """
                        .formatted(refreshToken)))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    assertThat(users.findById(userId).orElseThrow().getRefresh_token()).isNull();
    mockMvc
        .perform(post("/api/auth/refresh").header("Refresh-Token", refreshToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
  }

  private String loggedInRefreshToken() throws Exception {
    return login(EMAIL, PASSWORD)
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getHeader("Refresh-Token");
  }

  private ResultActions signUp(String email, String password, String name) throws Exception {
    return mockMvc.perform(
        post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"email": "%s", "password": "%s", "name": "%s"}
                """
                    .formatted(email, password, name)));
  }

  private ResultActions login(String email, String password) throws Exception {
    return mockMvc.perform(
        post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"email": "%s", "password": "%s"}
                """
                    .formatted(email, password)));
  }
}
