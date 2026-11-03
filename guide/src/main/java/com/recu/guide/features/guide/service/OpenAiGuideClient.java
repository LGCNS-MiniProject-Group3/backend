package com.recu.guide.features.guide.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.recu.common.web.error.BusinessException;
import com.recu.guide.error.GuideErrorCode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

/** 가이드 생성 AI 호출. OpenAI Chat Completions를 부른다 (design.md 9장 AI 호출 설정). */
@Slf4j
@Component
public class OpenAiGuideClient {

  private static final int MAX_ATTEMPTS = 3;
  private static final long BACKOFF_MILLIS = 500;
  // 응답 스키마는 객체 속성 수에 한도가 있다. 구간이 이보다 많으면 스키마 없이 json_object로 부른다
  private static final int SCHEMA_MAX_SECTIONS = 90;
  private static final List<String> BADGES = List.of("KEY", "LIGHT", "REF", "SKIP");

  private final RestClient restClient;
  private final String model;
  private final Sleeper sleeper;

  @Autowired
  public OpenAiGuideClient(
      RestClient.Builder builder,
      @Value("${openai.api-key}") String apiKey,
      @Value("${guide.model:}") String model) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(5));
    requestFactory.setReadTimeout(Duration.ofSeconds(30));
    this.restClient = openAi(builder.clone(), apiKey).requestFactory(requestFactory).build();
    this.model = model;
    this.sleeper = Thread::sleep;
  }

  OpenAiGuideClient(RestClient restClient, String model, Sleeper sleeper) {
    this.restClient = restClient;
    this.model = model;
    this.sleeper = sleeper;
  }

  /** 주소와 키. 테스트도 같은 설정을 쓰도록 나눴다 */
  static RestClient.Builder openAi(RestClient.Builder builder, String apiKey) {
    return builder
        .baseUrl("https://api.openai.com/v1")
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
  }

  /** instruction: 지시문(system), input: 프로필과 글(user), sectionCount: 응답 스키마에 넣을 구간 수 */
  public String generate(String instruction, String input, int sectionCount) {
    if (model.isBlank()) {
      throw new BusinessException(GuideErrorCode.AI_UNAVAILABLE);
    }

    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        return callOpenAi(instruction, input, sectionCount);
      } catch (HttpClientErrorException.TooManyRequests | HttpServerErrorException exception) {
        // 429(호출 한도)와 5xx는 잠깐 뒤 다시 부르면 되는 경우가 많다
        if (attempt == MAX_ATTEMPTS) {
          log.error("OpenAI guide request failed after {} attempts", MAX_ATTEMPTS);
          throw new BusinessException(GuideErrorCode.AI_UNAVAILABLE);
        }
        log.warn(
            "OpenAI guide request returned {}. retry={}/{}",
            exception.getStatusCode().value(),
            attempt + 1,
            MAX_ATTEMPTS);
        sleep(BACKOFF_MILLIS * attempt);
      } catch (BusinessException exception) {
        throw exception;
      } catch (Exception exception) {
        log.error("OpenAI guide request failed. type={}", exception.getClass().getName());
        throw new BusinessException(GuideErrorCode.AI_UNAVAILABLE);
      }
    }
    throw new BusinessException(GuideErrorCode.AI_UNAVAILABLE);
  }

  private String callOpenAi(String instruction, String input, int sectionCount) {
    JsonNode response =
        restClient
            .post()
            .uri("/chat/completions")
            .body(
                Map.of(
                    "model",
                    model,
                    "messages",
                    List.of(
                        Map.of("role", "system", "content", instruction),
                        Map.of("role", "user", "content", input)),
                    "response_format",
                    responseFormat(sectionCount)))
            .retrieve()
            .body(JsonNode.class);

    String text =
        response == null
            ? null
            : response.path("choices").path(0).path("message").path("content").textValue();
    if (text == null) {
      throw new BusinessException(GuideErrorCode.AI_UNAVAILABLE);
    }
    return text;
  }

  /**
   * 답의 모양을 구간 수에 맞춰 강제한다. badges는 "1"~"N"을 모두 필수로, 값은 배지 4개 중 하나로. 구간이 너무 많으면 스키마 한도 때문에
   * json_object로 둔다. 이때는 지시문에 "JSON"이라는 말이 있어야 한다 (prompts/guide-v1.txt).
   */
  static Map<String, Object> responseFormat(int sectionCount) {
    if (sectionCount < 1 || sectionCount > SCHEMA_MAX_SECTIONS) {
      return Map.of("type", "json_object");
    }

    Map<String, Object> badgeProperties = new LinkedHashMap<>();
    List<String> badgeRequired = new ArrayList<>();
    for (int seq = 1; seq <= sectionCount; seq++) {
      badgeProperties.put(String.valueOf(seq), Map.of("type", "string", "enum", BADGES));
      badgeRequired.add(String.valueOf(seq));
    }
    Map<String, Object> badges = new LinkedHashMap<>();
    badges.put("type", "object");
    badges.put("properties", badgeProperties);
    badges.put("required", badgeRequired);
    badges.put("additionalProperties", false);

    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("badges", badges);
    properties.put("lookFor", Map.of("type", "string"));
    properties.put("readingOrder", Map.of("type", "string"));
    properties.put("nextStudy", Map.of("type", "string"));

    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("properties", properties);
    schema.put("required", List.of("badges", "lookFor", "readingOrder", "nextStudy"));
    schema.put("additionalProperties", false);

    return Map.of(
        "type",
        "json_schema",
        "json_schema",
        Map.of("name", "reading_guide", "strict", true, "schema", schema));
  }

  private void sleep(long millis) {
    try {
      sleeper.sleep(millis);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new BusinessException(GuideErrorCode.AI_UNAVAILABLE);
    }
  }

  @FunctionalInterface
  interface Sleeper {
    void sleep(long millis) throws InterruptedException;
  }
}
