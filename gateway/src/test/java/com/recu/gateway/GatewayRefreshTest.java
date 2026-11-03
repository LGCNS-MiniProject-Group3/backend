package com.recu.gateway;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 설정 갱신(busrefresh를 받았을 때와 같은 refresh) 뒤 CORS 허용 출처가 재시작 없이 바뀌는지 확인한다. 실제 Kafka 경로는 로컬 compose로
 * 확인한다 (PR 확인 방법).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext
class GatewayRefreshTest {

  private static final String NEW_ORIGIN = "https://recu.example.com";

  @Autowired private WebTestClient client;
  @Autowired private ConfigurableEnvironment environment;
  @Autowired private ContextRefresher refresher;

  @Test
  void 설정을_갱신하면_새_출처를_재시작_없이_허용한다() {
    preflight(NEW_ORIGIN).expectStatus().isForbidden();

    // 설정 저장소의 gateway.yml 을 고친 것과 같다
    environment
        .getPropertySources()
        .addFirst(new MapPropertySource("changed", Map.of("app.cors-origins", NEW_ORIGIN)));
    refresher.refresh();

    preflight(NEW_ORIGIN)
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, NEW_ORIGIN);
    preflight("http://localhost:5173").expectStatus().isForbidden();
  }

  @Test
  void busrefresh는_Gateway로_부를_수_없다() {
    client.post().uri("/actuator/busrefresh").exchange().expectStatus().isNotFound();
  }

  private WebTestClient.ResponseSpec preflight(String origin) {
    return client
        .options()
        .uri("/api/profile")
        .header(HttpHeaders.ORIGIN, origin)
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
        .exchange();
  }
}
