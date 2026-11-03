package com.recu.guide.features.guide.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.recu.common.web.error.BusinessException;
import com.recu.guide.error.GuideErrorCode;
import java.time.Duration;
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

  public String generate(String prompt) {
    if (model.isBlank()) {
      throw new BusinessException(GuideErrorCode.AI_UNAVAILABLE);
    }

    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        return callOpenAi(prompt);
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

  private String callOpenAi(String prompt) {
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
                    // 지시문에 "JSON"이라는 말이 있어야 이 형식을 쓸 수 있다 (prompts/guide-v1.txt)
                    "response_format",
                    Map.of("type", "json_object")))
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
