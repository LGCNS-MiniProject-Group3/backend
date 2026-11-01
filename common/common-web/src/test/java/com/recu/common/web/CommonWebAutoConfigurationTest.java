package com.recu.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import feign.Request;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CommonWebAutoConfigurationTest {

  @Autowired MockMvc mockMvc;
  @Autowired Request.Options feignOptions;

  @Test
  @DisplayName("X-User-Id 헤더가 있으면 회원 번호로 받는다")
  void currentUserId() throws Exception {
    mockMvc
        .perform(get("/me").header("X-User-Id", "7"))
        .andExpect(status().isOk())
        .andExpect(content().string("7"));
  }

  @Test
  @DisplayName("X-User-Id 헤더가 없으면 401 UNAUTHORIZED")
  void missingHeader() throws Exception {
    mockMvc
        .perform(get("/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
        .andExpect(jsonPath("$.field").doesNotExist());
  }

  @Test
  @DisplayName("X-User-Id가 숫자가 아니면 401 UNAUTHORIZED")
  void invalidHeader() throws Exception {
    mockMvc
        .perform(get("/me").header("X-User-Id", "abc"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  @DisplayName("BusinessException은 오류 코드의 상태와 {code, message, field}로 응답한다")
  void businessException() throws Exception {
    mockMvc
        .perform(get("/business"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.field").value("postId"));
  }

  @Test
  @DisplayName("입력값 검증에 실패하면 400 INVALID_INPUT과 첫 번째 필드 이름")
  void invalidInput() throws Exception {
    mockMvc
        .perform(post("/valid").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.field").value("name"));
  }

  @Test
  @DisplayName("다른 서비스가 오류로 응답하면 502 UPSTREAM_ERROR")
  void feignErrorResponse() throws Exception {
    mockMvc
        .perform(get("/feign/error"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"));
  }

  @Test
  @DisplayName("다른 서비스 호출이 시간을 넘기면 502 UPSTREAM_ERROR")
  void feignTimeout() throws Exception {
    mockMvc
        .perform(get("/feign/timeout"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"));
  }

  @Test
  @DisplayName("처리하지 못한 예외는 500 INTERNAL_ERROR, 내부 메시지는 응답에 싣지 않는다")
  void unexpectedException() throws Exception {
    mockMvc
        .perform(get("/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
  }

  @Test
  @DisplayName("Feign 기본 타임아웃: 연결 2초, 응답 5초")
  void feignTimeouts() {
    assertThat(feignOptions.connectTimeout()).isEqualTo(2_000);
    assertThat(feignOptions.connectTimeoutUnit()).isEqualTo(TimeUnit.MILLISECONDS);
    assertThat(feignOptions.readTimeout()).isEqualTo(5_000);
  }
}
