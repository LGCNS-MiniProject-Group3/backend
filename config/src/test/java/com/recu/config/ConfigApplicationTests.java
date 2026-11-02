package com.recu.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 설정 저장소 대신 임시 폴더(native)를 읽혀서, 프로필별 값과 /encrypt · '{cipher}' 풀기를 확인한다. GitHub 저장소·토큰 없이 돈다. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    // Bus는 실제 Kafka가 필요해서 끈다. busrefresh 는 로컬 compose 로 확인한다 (PR 확인 방법)
    properties = {"spring.profiles.active=native", "spring.cloud.bus.enabled=false"})
class ConfigApplicationTests {

  private static final String TEST_KEY = "test-only-encrypt-key-at-least-32-chars";

  @Autowired private TestRestTemplate rest;

  private static Path repoDir;

  @DynamicPropertySource
  static void repo(DynamicPropertyRegistry registry) {
    repoDir = writeRepo();
    registry.add(
        "spring.cloud.config.server.native.search-locations", () -> repoDir.toUri().toString());
    registry.add("encrypt.key", () -> TEST_KEY);
  }

  @Test
  void 프로필에_따라_다른_값을_내려준다() {
    assertThat(value("/user/local", "recu.db.host")).isEqualTo("localhost");
    assertThat(value("/user/dev", "recu.db.host")).isEqualTo("mariadb");
  }

  @Test
  void 서비스_파일이_공통_파일보다_우선한다() {
    assertThat(value("/user/local", "recu.greeting")).isEqualTo("user");
    assertThat(value("/content/local", "recu.greeting")).isEqualTo("common");
  }

  @Test
  void encrypt로_만든_값을_풀어서_내려준다() throws IOException {
    // 실제로 값을 넣는 방법과 같다: /encrypt로 암호화 → 설정 파일에 '{cipher}…'로 저장
    String cipher = rest.postForObject("/encrypt", "db-password", String.class);
    Files.writeString(
        repoDir.resolve("guide.yml"),
        "spring:\n  datasource:\n    password: '{cipher}" + cipher + "'\n");

    assertThat(cipher).isNotEqualTo("db-password");
    assertThat(value("/guide/local", "spring.datasource.password")).isEqualTo("db-password");
  }

  /** 우선순위가 높은 파일부터 담긴 응답에서 처음 나오는 값. 서비스가 실제로 받는 값과 같다 */
  @SuppressWarnings("unchecked")
  private Object value(String path, String key) {
    Map<String, Object> body = rest.getForObject(path, Map.class);
    for (Map<String, Object> source : (List<Map<String, Object>>) body.get("propertySources")) {
      Map<String, Object> props = (Map<String, Object>) source.get("source");
      if (props.containsKey(key)) {
        return props.get(key);
      }
    }
    return null;
  }

  private static Path writeRepo() {
    try {
      Path dir = Files.createTempDirectory("config-repo");
      Files.writeString(dir.resolve("application.yml"), "recu:\n  greeting: common\n");
      Files.writeString(
          dir.resolve("application-local.yml"), "recu:\n  db:\n    host: localhost\n");
      Files.writeString(dir.resolve("application-dev.yml"), "recu:\n  db:\n    host: mariadb\n");
      Files.writeString(dir.resolve("user.yml"), "recu:\n  greeting: user\n");
      return dir;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
