package com.recu.gateway.log;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.HashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.test.context.ActiveProfiles;

/** 실제 ObservationRegistry에 규칙이 걸렸는지 본다. 빈이 있는지만 보면 등록이 빠져도 통과해 버린다. */
@SpringBootTest
@ActiveProfiles("test")
class ObservationConfigTest {

  @Autowired ObservationRegistry registry;

  @Test
  @DisplayName("/actuator/** 요청은 관측(추적·요청 지표)하지 않고, /api/** 요청은 관측한다")
  void actuatorIsNotObserved() {
    assertThat(isObserved("/actuator/prometheus")).isFalse();
    assertThat(isObserved("/actuator/health")).isFalse();
    assertThat(isObserved("/actuator")).isFalse();
    assertThat(isObserved("/api/companies/1/posts")).isTrue();
    assertThat(isObserved("/actuators")).isTrue();
  }

  @Test
  @DisplayName("서버 요청이 아닌 관측(서비스로 넘기는 호출 등)은 그대로 둔다")
  void otherObservationsAreKept() {
    Observation observation =
        Observation.createNotStarted("http.client.requests", Observation.Context::new, registry);
    assertThat(observation.isNoop()).isFalse();
  }

  // 규칙에 걸리면 레지스트리가 아무것도 기록하지 않는 noop 관측을 돌려준다
  private boolean isObserved(String path) {
    ServerRequestObservationContext context =
        new ServerRequestObservationContext(
            MockServerHttpRequest.get(path).build(), new MockServerHttpResponse(), new HashMap<>());
    return !Observation.createNotStarted("http.server.requests", () -> context, registry).isNoop();
  }
}
