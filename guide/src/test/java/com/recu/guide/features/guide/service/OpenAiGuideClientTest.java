package com.recu.guide.features.guide.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.recu.common.web.error.BusinessException;
import com.recu.guide.error.GuideErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** OpenAI 요청 모양, 응답에서 답을 꺼내는 곳, 재시도. 실제 OpenAI는 부르지 않는다. */
class OpenAiGuideClientTest {

  private static final String URL = "https://api.openai.com/v1/chat/completions";
  private static final String ANSWER = "{\\\"badges\\\":{\\\"1\\\":\\\"KEY\\\"}}";
  private static final String OK_BODY =
      "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"" + ANSWER + "\"}}]}";

  private MockRestServiceServer openAi;
  private OpenAiGuideClient client;
  private final List<Long> sleeps = new ArrayList<>();

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = OpenAiGuideClient.openAi(RestClient.builder(), "test-key");
    openAi = MockRestServiceServer.bindTo(builder).build();
    client = new OpenAiGuideClient(builder.build(), "gpt-4o-mini", sleeps::add);
  }

  @Test
  void 키를_Bearer로_싣고_지시문은_system_글은_user로_보내_답을_꺼낸다() {
    openAi
        .expect(requestTo(URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer test-key"))
        .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
        .andExpect(jsonPath("$.messages[0].role").value("system"))
        .andExpect(jsonPath("$.messages[0].content").value("지시문"))
        .andExpect(jsonPath("$.messages[1].role").value("user"))
        .andExpect(jsonPath("$.messages[1].content").value("글"))
        .andExpect(jsonPath("$.response_format.type").value("json_schema"))
        .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

    assertThat(client.generate("지시문", "글", 2)).isEqualTo("{\"badges\":{\"1\":\"KEY\"}}");
    openAi.verify();
  }

  @Test
  void 구간_수만큼_배지를_필수로_하는_응답_스키마로_요청한다() {
    String badges = "$.response_format.json_schema.schema.properties.badges";
    openAi
        .expect(requestTo(URL))
        .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
        .andExpect(jsonPath(badges + ".required", contains("1", "2")))
        .andExpect(jsonPath(badges + ".additionalProperties").value(false))
        .andExpect(
            jsonPath(badges + ".properties['2'].enum", contains("KEY", "LIGHT", "REF", "SKIP")))
        .andExpect(
            jsonPath(
                "$.response_format.json_schema.schema.required",
                contains("badges", "lookFor", "readingOrder", "nextStudy")))
        .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

    client.generate("지시문", "글", 2);
    openAi.verify();
  }

  @Test
  void 구간이_90개를_넘으면_스키마_없이_json_object로_요청한다() {
    openAi
        .expect(requestTo(URL))
        .andExpect(jsonPath("$.response_format.type").value("json_object"))
        .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

    client.generate("지시문", "글", 91);
    openAi.verify();
  }

  @Test
  void 호출_한도에_걸리면_쉬었다가_다시_부른다() {
    openAi.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
    openAi.expect(requestTo(URL)).andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

    assertThat(client.generate("지시문", "글", 2)).isEqualTo("{\"badges\":{\"1\":\"KEY\"}}");
    assertThat(sleeps).containsExactly(500L);
    openAi.verify();
  }

  @Test
  void 서버_오류가_세_번_이어지면_AI_UNAVAILABLE() {
    openAi.expect(times(3), requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

    assertThatThrownBy(() -> client.generate("지시문", "글", 2))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(GuideErrorCode.AI_UNAVAILABLE);
    assertThat(sleeps).containsExactly(500L, 1000L);
    openAi.verify();
  }

  @Test
  void 키가_틀리면_다시_부르지_않고_AI_UNAVAILABLE() {
    openAi.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    assertThatThrownBy(() -> client.generate("지시문", "글", 2))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(GuideErrorCode.AI_UNAVAILABLE);
    assertThat(sleeps).isEmpty();
    openAi.verify();
  }

  @Test
  void 답이_비어_있으면_AI_UNAVAILABLE() {
    openAi
        .expect(requestTo(URL))
        .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.generate("지시문", "글", 2))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(GuideErrorCode.AI_UNAVAILABLE);
  }

  @Test
  void 모델이_설정되지_않으면_부르지_않는다() {
    OpenAiGuideClient noModel =
        new OpenAiGuideClient(RestClient.builder().build(), "", sleeps::add);

    assertThatThrownBy(() -> noModel.generate("지시문", "글", 2))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(GuideErrorCode.AI_UNAVAILABLE);
  }
}
