package com.recu.collector.features.collect.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.recu.collector.features.collect.domain.dto.CollectedPost;
import com.recu.collector.features.collect.domain.dto.ImportedPost;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** OpenAI 요청 모양과 재시도. 실제 OpenAI는 부르지 않는다. */
class PostClassifierCallTest {

  private static final String URL = "https://api.openai.com/v1/chat/completions";
  private static final String OK_BODY =
      """
      {"choices":[{"message":{"role":"assistant","content":
        "{\\"is_tech\\":true,\\"field\\":\\"Backend\\",\\"level\\":\\"중급\\",\\"core_techs\\":[\\"Kafka\\"],\\"summary\\":\\"요지\\"}"}}]}
      """;

  private static final CollectedPost POST =
      new CollectedPost(
          "토스",
          "1",
          "제목",
          "https://a.com/1",
          LocalDateTime.now(),
          "",
          "본문",
          2,
          false,
          List.of(),
          List.of());

  @Test
  void 키를_Bearer로_싣고_모델_온도_JSON_형식으로_요청해_분류값을_채운다() {
    RestClient.Builder builder = PostClassifier.openAi(RestClient.builder(), "test-key");
    MockRestServiceServer openAi = MockRestServiceServer.bindTo(builder).build();
    PostClassifier classifier =
        new PostClassifier(builder.build(), "gpt-4o-mini", 7000, 0, millis -> {});
    openAi
        .expect(requestTo(URL))
        .andExpect(header("Authorization", "Bearer test-key"))
        .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
        .andExpect(jsonPath("$.temperature").value(0.0))
        .andExpect(jsonPath("$.response_format.type").value("json_object"))
        .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

    ImportedPost.Ai ai = classifier.classify(POST).orElseThrow();

    assertThat(ai.is_tech()).isTrue();
    assertThat(ai.field()).isEqualTo("Backend");
    assertThat(ai.level()).isEqualTo("중급");
    assertThat(ai.core_techs()).containsExactly("Kafka");
    assertThat(ai.summary()).isEqualTo("요지");
    openAi.verify();
  }

  @Test
  void 호출_한도나_서버_오류면_쉬었다가_다시_부른다() {
    RestClient.Builder builder = PostClassifier.openAi(RestClient.builder(), "test-key");
    MockRestServiceServer openAi = MockRestServiceServer.bindTo(builder).build();
    List<Long> sleeps = new ArrayList<>();
    PostClassifier classifier =
        new PostClassifier(builder.build(), "gpt-4o-mini", 7000, 0, sleeps::add);
    openAi.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
    openAi.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
    openAi.expect(requestTo(URL)).andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

    assertThat(classifier.classify(POST)).isPresent();
    assertThat(sleeps).hasSize(2);
    openAi.verify();
  }

  @Test
  void 끝내_실패하면_분류_없이_돌려준다() {
    RestClient.Builder builder = PostClassifier.openAi(RestClient.builder(), "test-key");
    MockRestServiceServer openAi = MockRestServiceServer.bindTo(builder).build();
    PostClassifier classifier =
        new PostClassifier(builder.build(), "gpt-4o-mini", 7000, 0, millis -> {});
    openAi.expect(times(5), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

    Optional<ImportedPost.Ai> result = classifier.classify(POST);

    assertThat(result).isEmpty();
    openAi.verify();
  }
}
