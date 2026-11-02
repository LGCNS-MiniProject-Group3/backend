package com.recu.collector.features.collect.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recu.collector.features.collect.domain.dto.CollectedPost;
import com.recu.collector.features.collect.domain.dto.ImportedPost;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

/**
 * 글 한 편에 AI 1차 분류를 매긴다. 수집할 때 글마다 한 번만 부른다.
 *
 * <p>결과 칸은 blog_post 의 is_tech · field · level · summary 와 blog_post_tag 의 핵심 기술이다. 허용값 밖의 답은
 * 버린다(ERD 5.6). 끝내 실패하면 빈 값을 돌려주고, 저장 단계는 그 글을 분류값 없이 넣는다.
 */
@Component
public class PostClassifier {

  private static final Logger log = LoggerFactory.getLogger(PostClassifier.class);

  private static final Set<String> FIELDS =
      Set.of("Backend", "Frontend", "Data", "Infra", "Mobile", "General");
  private static final Set<String> LEVELS = Set.of("입문", "중급", "고급");
  private static final int MAX_TECHS = 3;

  /** 호출 한도(429)나 일시 오류(5xx)가 나면 쉬었다가 다시 부른다. */
  private static final int MAX_ATTEMPTS = 5;

  private static final long RATE_LIMIT_WAIT_MILLIS = 20_000;

  private final RestClient restClient;
  private final String model;
  private final int bodyChars;
  private final double temperature;
  private final Sleeper sleeper;
  private final ObjectMapper mapper = new ObjectMapper();
  private final String template;

  @Autowired
  public PostClassifier(
      RestClient.Builder builder,
      @Value("${openai.api-key}") String apiKey,
      @Value("${collect.classify.model:}") String model,
      @Value("${collect.classify.body-chars:7000}") int bodyChars,
      @Value("${collect.classify.temperature:0}") double temperature) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(5));
    requestFactory.setReadTimeout(Duration.ofSeconds(60));
    this.restClient = openAi(builder.clone(), apiKey).requestFactory(requestFactory).build();
    this.model = model;
    this.bodyChars = bodyChars;
    this.temperature = temperature;
    this.sleeper = Thread::sleep;
    this.template = loadTemplate();
  }

  PostClassifier(
      RestClient restClient, String model, int bodyChars, double temperature, Sleeper sleeper) {
    this.restClient = restClient;
    this.model = model;
    this.bodyChars = bodyChars;
    this.temperature = temperature;
    this.sleeper = sleeper;
    this.template = loadTemplate();
  }

  /** 주소와 키. 테스트도 같은 설정을 쓰도록 나눴다 */
  static RestClient.Builder openAi(RestClient.Builder builder, String apiKey) {
    return builder
        .baseUrl("https://api.openai.com/v1")
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
  }

  /** 분류 결과. 모델이 없거나 끝내 실패하면 비어서 돌아온다. */
  public Optional<ImportedPost.Ai> classify(CollectedPost post) {
    if (model == null || model.isBlank()) {
      log.warn("분류 모델이 설정되지 않아 분류 없이 저장합니다: {}", post.url());
      return Optional.empty();
    }
    String prompt = prompt(post);
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        return parse(call(prompt));
      } catch (HttpClientErrorException.TooManyRequests | HttpServerErrorException e) {
        log.warn("분류 호출 한도 · 일시 오류, 쉬었다가 다시 부릅니다 ({}/{})", attempt, MAX_ATTEMPTS);
        if (!sleep(RATE_LIMIT_WAIT_MILLIS)) {
          return Optional.empty();
        }
      } catch (Exception e) {
        log.warn("분류 실패, 분류 없이 저장합니다: {} ({})", post.url(), e.getClass().getSimpleName());
        return Optional.empty();
      }
    }
    log.warn("분류 재시도 끝, 분류 없이 저장합니다: {}", post.url());
    return Optional.empty();
  }

  String prompt(CollectedPost post) {
    String body = post.bodyText() == null ? "" : post.bodyText();
    if (body.length() > bodyChars) {
      body = body.substring(0, bodyChars);
    }
    return template
        .replace("{{title}}", nullToEmpty(post.title()))
        .replace("{{company}}", nullToEmpty(post.company()))
        .replace("{{bodyChars}}", String.valueOf(bodyChars))
        .replace("{{body}}", body);
  }

  /** 모델 답을 표 칸 값으로 바꾼다. 허용값 밖이면 그 칸만 비운다. */
  Optional<ImportedPost.Ai> parse(String text) throws IOException {
    JsonNode node = mapper.readTree(text);
    if (node.isArray() && !node.isEmpty()) {
      node = node.get(0);
    }
    if (!node.isObject()) {
      return Optional.empty();
    }
    Boolean isTech = node.path("is_tech").isBoolean() ? node.path("is_tech").asBoolean() : null;
    String field = allowed(node.path("field").asText(null), FIELDS);
    String level = allowed(node.path("level").asText(null), LEVELS);

    List<String> techs = new ArrayList<>();
    for (JsonNode tech : node.path("core_techs")) {
      String name = tech.asText("").trim();
      if (!name.isEmpty() && techs.size() < MAX_TECHS && !techs.contains(name)) {
        techs.add(name);
      }
    }
    String summary = node.path("summary").asText(null);
    if (summary != null && summary.isBlank()) {
      summary = null;
    }
    return Optional.of(new ImportedPost.Ai(isTech, techs, field, level, summary));
  }

  private String call(String prompt) {
    JsonNode response =
        restClient
            .post()
            .uri("/chat/completions")
            .body(
                Map.of(
                    "model",
                    model,
                    "messages",
                    List.of(Map.of("role", "user", "content", prompt)),
                    // 지시문에 "JSON"이라는 말이 있어야 이 형식을 쓸 수 있다 (prompts/classify-v1.txt)
                    "response_format",
                    Map.of("type", "json_object"),
                    "temperature",
                    temperature))
            .retrieve()
            .body(JsonNode.class);
    String text =
        response == null
            ? null
            : response.path("choices").path(0).path("message").path("content").textValue();
    if (text == null) {
      throw new IllegalStateException("빈 응답");
    }
    return text;
  }

  private String allowed(String value, Set<String> values) {
    if (value == null) {
      return null;
    }
    String v = value.trim();
    return values.contains(v) ? v : null;
  }

  private boolean sleep(long millis) {
    try {
      sleeper.sleep(millis);
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  private static String loadTemplate() {
    try {
      return new ClassPathResource("prompts/classify-v1.txt")
          .getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("분류 지시문을 읽지 못했습니다", e);
    }
  }

  @FunctionalInterface
  interface Sleeper {
    void sleep(long millis) throws InterruptedException;
  }
}
