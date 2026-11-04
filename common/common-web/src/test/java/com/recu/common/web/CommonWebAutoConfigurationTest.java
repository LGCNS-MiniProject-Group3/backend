package com.recu.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import feign.Request;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class CommonWebAutoConfigurationTest {

  @Autowired MockMvc mockMvc;
  @Autowired Request.Options feignOptions;
  @Autowired ObservationPredicate actuatorObservationPredicate;

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
  @DisplayName("경로 값의 타입이 다르면 400 INVALID_INPUT과 파라미터 이름")
  void pathTypeMismatch() throws Exception {
    mockMvc
        .perform(get("/posts/abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."))
        .andExpect(jsonPath("$.field").value("postId"));
  }

  @Test
  @DisplayName("컨트롤러 인자 검증(@Positive)에 실패하면 400 INVALID_INPUT")
  void methodValidation() throws Exception {
    mockMvc
        .perform(get("/positive/-1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.field").value("postId"));
  }

  @Test
  @DisplayName("본문이 JSON이 아니면 400 INVALID_INPUT, field는 없음")
  void brokenJson() throws Exception {
    mockMvc
        .perform(post("/ids").contentType(MediaType.APPLICATION_JSON).content("{postIds:"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.field").doesNotExist());
  }

  @Test
  @DisplayName("JSON 필드 타입이 다르면 400 INVALID_INPUT과 필드 이름")
  void jsonFieldTypeMismatch() throws Exception {
    mockMvc
        .perform(
            post("/ids").contentType(MediaType.APPLICATION_JSON).content("{\"postIds\":[\"a\"]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.field").value("postIds"));
  }

  @Test
  @DisplayName("필수 쿼리 파라미터가 없으면 400 INVALID_INPUT과 파라미터 이름")
  void missingParameter() throws Exception {
    mockMvc
        .perform(get("/urls"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.field").value("companyId"));
  }

  @Test
  @DisplayName("필수 헤더가 없으면 400 INVALID_INPUT과 헤더 이름")
  void missingHeaderValue() throws Exception {
    mockMvc
        .perform(get("/trace"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.field").value("X-Trace-Id"));
  }

  @Test
  @DisplayName("없는 경로는 404 NOT_FOUND")
  void noSuchPath() throws Exception {
    mockMvc
        .perform(get("/nowhere"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.field").doesNotExist());
  }

  @Test
  @DisplayName("경로에 없는 메서드는 405 METHOD_NOT_ALLOWED와 Allow 헤더")
  void methodNotAllowed() throws Exception {
    mockMvc
        .perform(delete("/me"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().string("Allow", "GET"))
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
        .andExpect(jsonPath("$.message").value("지원하지 않는 요청 방식입니다."));
  }

  @Test
  @DisplayName("잘못된 요청은 서버 오류 로그를 남기지 않는다")
  void badRequestIsNotLoggedAsError(CapturedOutput output) throws Exception {
    mockMvc.perform(get("/posts/abc")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/nowhere")).andExpect(status().isNotFound());
    mockMvc.perform(delete("/me")).andExpect(status().isMethodNotAllowed());

    assertThat(output).doesNotContain("Unhandled exception");
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

  @Test
  @DisplayName("/actuator/** 요청은 관측(추적·요청 지표)하지 않고, /api/** 요청은 관측한다")
  void actuatorIsNotObserved() {
    assertThat(isObserved("/actuator/prometheus")).isFalse();
    assertThat(isObserved("/actuator/health")).isFalse();
    assertThat(isObserved("/actuator")).isFalse();
    assertThat(isObserved("/api/companies/1/posts")).isTrue();
    assertThat(isObserved("/internal/users/1/preference")).isTrue();
    assertThat(isObserved("/actuators")).isTrue();
  }

  @Test
  @DisplayName("서버 요청이 아닌 관측(Feign 호출 등)은 그대로 둔다")
  void otherObservationsAreKept() {
    assertThat(actuatorObservationPredicate.test("http.client.requests", new Observation.Context()))
        .isTrue();
  }

  private boolean isObserved(String path) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
    ServerRequestObservationContext context =
        new ServerRequestObservationContext(request, new MockHttpServletResponse());
    return actuatorObservationPredicate.test("http.server.requests", context);
  }
}
