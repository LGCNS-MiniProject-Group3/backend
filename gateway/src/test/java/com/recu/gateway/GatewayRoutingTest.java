package com.recu.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 실제 서비스 대신 서비스마다 작은 HTTP 서버를 띄우고, Gateway를 거친 요청이 어디로 가는지와 어떤 헤더를 달고 가는지 확인한다. Eureka 대신 simple
 * discovery로 서비스 이름(lb://user 등)을 이 서버들에 연결한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayRoutingTest {

  private static final String SECRET = "test-only-secret-key-must-be-at-least-32-bytes-long";
  private static final List<String> SERVICES =
      List.of("user", "content", "collector", "guide", "bookmark");

  /** 서비스 이름 → 가짜 서버 */
  private static final Map<String, HttpServer> SERVERS = new LinkedHashMap<>();

  /** 가짜 서버가 마지막으로 받은 요청: 서비스 이름, 경로, 헤더 */
  private static final Map<String, String> received = new ConcurrentHashMap<>();

  @Autowired private WebTestClient client;

  @DynamicPropertySource
  static void services(DynamicPropertyRegistry registry) {
    for (String service : SERVICES) {
      HttpServer server = SERVERS.computeIfAbsent(service, GatewayRoutingTest::start);
      registry.add(
          "spring.cloud.discovery.client.simple.instances." + service + "[0].uri",
          () -> "http://localhost:" + server.getAddress().getPort());
    }
  }

  @AfterAll
  static void stop() {
    SERVERS.values().forEach(server -> server.stop(0));
  }

  @BeforeEach
  void clear() {
    received.clear();
  }

  // ---- 이슈 #9 완료 기준 테스트 3개 ----

  @Test
  void 토큰이_필요한_경로에_토큰이_없으면_401() {
    client
        .get()
        .uri("/api/profile")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("UNAUTHORIZED")
        .jsonPath("$.message")
        .isEqualTo("인증이 필요합니다.")
        .jsonPath("$.field")
        .isEmpty();

    assertThat(received).isEmpty();
  }

  @Test
  void 일반_사용자가_관리자_API를_부르면_403() {
    client
        .post()
        .uri("/api/admin/collect")
        .header(HttpHeaders.AUTHORIZATION, bearer(access(1L, "USER")))
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("FORBIDDEN");

    assertThat(received).isEmpty();
  }

  @Test
  void 클라이언트가_보낸_X_User_헤더는_지우고_토큰의_값을_넘긴다() {
    client
        .get()
        .uri("/api/profile")
        .header(HttpHeaders.AUTHORIZATION, bearer(access(7L, "USER")))
        .header("X-User-Id", "999")
        .header("x-user-role", "ADMIN")
        .header("X-User-Anything", "fake")
        .exchange()
        .expectStatus()
        .isOk();

    assertThat(received.get("service")).isEqualTo("user");
    assertThat(received.get("x-user-id")).isEqualTo("7");
    assertThat(received.get("x-user-role")).isEqualTo("USER");
    assertThat(received).doesNotContainKey("x-user-anything");
  }

  @Test
  void 공개_경로에서도_위조한_X_User_Id는_지운다() {
    client.get().uri("/api/tech-tags").header("X-User-Id", "999").exchange().expectStatus().isOk();

    assertThat(received.get("service")).isEqualTo("content");
    assertThat(received).doesNotContainKey("x-user-id");
  }

  // ---- 라우팅 ----

  @Test
  void 경로별로_서비스에_나눠_준다() {
    String token = bearer(access(1L, "ADMIN"));
    Map<String, String> expected = new LinkedHashMap<>();
    expected.put("GET /api/auth/x", "user");
    expected.put("GET /api/profile", "user");
    expected.put("GET /api/companies", "content");
    expected.put("GET /api/companies/1/posts", "content");
    expected.put("GET /api/posts/1", "content");
    expected.put("GET /api/tech-tags", "content");
    expected.put("POST /api/posts/1/guide", "guide");
    expected.put("POST /api/admin/collect/import", "collector");
    expected.put("GET /api/bookmarks", "bookmark");
    expected.put("DELETE /api/bookmarks/3", "bookmark");

    expected.forEach(
        (request, service) -> {
          String[] parts = request.split(" ");
          received.clear();
          client
              .method(org.springframework.http.HttpMethod.valueOf(parts[0]))
              .uri(parts[1])
              .header(HttpHeaders.AUTHORIZATION, token)
              .exchange()
              .expectStatus()
              .isOk();
          assertThat(received.get("service")).as(request).isEqualTo(service);
          assertThat(received.get("path")).as(request).isEqualTo(parts[1]);
        });
  }

  @Test
  void 내부_API는_외부에서_부를_수_없다() {
    client
        .post()
        .uri("/internal/posts/summaries")
        .header(HttpHeaders.AUTHORIZATION, bearer(access(1L, "ADMIN")))
        .exchange()
        .expectStatus()
        .isNotFound();

    assertThat(received).isEmpty();
  }

  // ---- 공개 경로와 토큰 검사 ----

  @Test
  void 공개_경로는_토큰_없이_통과한다() {
    client.post().uri("/api/auth/google").exchange().expectStatus().isOk();
    client.post().uri("/api/auth/refresh").exchange().expectStatus().isOk();
    client.get().uri("/api/tech-tags").exchange().expectStatus().isOk();
    client.get().uri("/api/companies/1").exchange().expectStatus().isOk();
  }

  @Test
  void 공개_경로와_비슷하지만_토큰이_필요한_경로() {
    client.post().uri("/api/auth/logout").exchange().expectStatus().isUnauthorized();
    client.get().uri("/api/companies").exchange().expectStatus().isUnauthorized();
    client.get().uri("/api/companies/1/posts").exchange().expectStatus().isUnauthorized();
    // 원본에서 임시로 열어 둔 적재 경로. 이번에 닫았다
    client.post().uri("/api/admin/collect/import").exchange().expectStatus().isUnauthorized();
  }

  @Test
  void 관리자는_관리자_API를_부를_수_있다() {
    client
        .post()
        .uri("/api/admin/collect")
        .header(HttpHeaders.AUTHORIZATION, bearer(access(1L, "ADMIN")))
        .exchange()
        .expectStatus()
        .isOk();

    assertThat(received.get("service")).isEqualTo("collector");
    assertThat(received.get("x-user-role")).isEqualTo("ADMIN");
  }

  @Test
  void 만료된_토큰은_TOKEN_EXPIRED() {
    String expired =
        Jwts.builder()
            .setSubject("1")
            .claim("role", "USER")
            .claim("type", "access")
            .setIssuedAt(new Date(System.currentTimeMillis() - 120_000))
            .setExpiration(new Date(System.currentTimeMillis() - 60_000))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();

    client
        .get()
        .uri("/api/profile")
        .header(HttpHeaders.AUTHORIZATION, bearer(expired))
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("TOKEN_EXPIRED");
  }

  @Test
  void 잘못된_토큰은_공개_경로라도_401() {
    String otherKey =
        Jwts.builder()
            .setSubject("1")
            .claim("role", "USER")
            .claim("type", "access")
            .signWith(
                Keys.hmacShaKeyFor(
                    "another-secret-key-must-be-at-least-32-bytes"
                        .getBytes(StandardCharsets.UTF_8)))
            .compact();
    String refresh =
        Jwts.builder()
            .setSubject("1")
            .claim("type", "refresh")
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();

    for (String authorization :
        List.of(bearer(otherKey), bearer(refresh), "Basic abc", "Bearer ", bearer("not-a-jwt"))) {
      client
          .get()
          .uri("/api/tech-tags")
          .header(HttpHeaders.AUTHORIZATION, authorization)
          .exchange()
          .expectStatus()
          .isUnauthorized()
          .expectBody()
          .jsonPath("$.code")
          .isEqualTo("UNAUTHORIZED")
          .jsonPath("$.message")
          .isEqualTo("유효하지 않은 인증 정보입니다.");
    }
    assertThat(received).isEmpty();
  }

  // ---- CORS ----

  @Test
  void 허용한_출처의_사전_요청은_토큰_없이_통과한다() {
    client
        .options()
        .uri("/api/profile")
        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173");

    assertThat(received).isEmpty();
  }

  @Test
  void 응답의_토큰_헤더를_브라우저에_노출한다() {
    client
        .post()
        .uri("/api/auth/google")
        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Authorization, Refresh-Token");
  }

  @Test
  void 허용하지_않은_출처는_막는다() {
    client
        .get()
        .uri("/api/tech-tags")
        .header(HttpHeaders.ORIGIN, "http://evil.example")
        .exchange()
        .expectStatus()
        .isForbidden();

    assertThat(received).isEmpty();
  }

  // ---- 도우미 ----

  private static String access(Long userId, String role) {
    return Jwts.builder()
        .setSubject(userId.toString())
        .claim("role", role)
        .claim("type", "access")
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + 60_000))
        .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
        .compact();
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  /** 받은 요청을 기록하고 200을 돌려주는 서버 */
  private static HttpServer start(String service) {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext(
          "/",
          exchange -> {
            received.put("service", service);
            received.put("path", exchange.getRequestURI().getPath());
            exchange
                .getRequestHeaders()
                .forEach((name, values) -> received.put(name.toLowerCase(), values.get(0)));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
          });
      server.start();
      return server;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
