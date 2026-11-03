package com.recu.guide.features.guide.ctrl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recu.common.kafka.KafkaEventPublisher;
import com.recu.common.kafka.event.GuideCreatedEvent;
import com.recu.guide.client.ContentClient;
import com.recu.guide.client.UserClient;
import com.recu.guide.client.dto.GuideInputResponseDTO;
import com.recu.guide.client.dto.GuideInputResponseDTO.Section;
import com.recu.guide.client.dto.UserProfileResponseDTO;
import com.recu.guide.database.entity.AiGuideEntity;
import com.recu.guide.features.guide.repository.GuideRepository;
import com.recu.guide.features.guide.service.OpenAiGuideClient;
import feign.FeignException;
import feign.Request;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** POST /api/posts/{id}/guide를 실제로 불러, ④⑤ 결과에 따른 응답을 확인한다. ④⑤와 AI는 가짜로 둔다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GuideControllerTest {

  private static final long USER_ID = 13L;
  private static final long POST_ID = 128L;

  // AI가 돌려줄 정상 답: 구간 2개 중 2번이 KEY
  private static final String AI_ANSWER =
      "{\"badges\":{\"1\":\"LIGHT\",\"2\":\"KEY\"},"
          + "\"lookFor\":\"볼 부분\",\"readingOrder\":\"읽는 순서\",\"nextStudy\":\"이어서\"}";

  @Autowired MockMvc mockMvc;
  @Autowired GuideRepository guideRepository;
  @Autowired Environment environment;
  @MockitoBean UserClient userClient;
  @MockitoBean ContentClient contentClient;
  @MockitoBean OpenAiGuideClient aiClient;
  @MockitoBean KafkaEventPublisher kafkaEventPublisher;

  @BeforeEach
  void setUp() {
    guideRepository.deleteAll();
    // 진짜 Kafka 대신 보내기가 바로 끝난 것으로 둔다
    when(kafkaEventPublisher.publishGuideCreated(any()))
        .thenReturn(CompletableFuture.completedFuture(null));
  }

  @Test
  @DisplayName("처음 만들면 ④·⑤·AI를 차례로 불러 저장하고 cached=false, C를 1번 발행")
  void createsNewGuide() throws Exception {
    when(userClient.getProfile(USER_ID)).thenReturn(profile(3));
    when(contentClient.getGuideInput(POST_ID)).thenReturn(postInput());
    when(aiClient.generate(anyString(), anyString())).thenReturn(AI_ANSWER);

    requestGuide()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cached").value(false))
        .andExpect(jsonPath("$.basis.careerYears").value(3))
        .andExpect(jsonPath("$.focusSections[0]").value(2));
    assertThat(guideRepository.count()).isEqualTo(1);
    verify(kafkaEventPublisher).publishGuideCreated(new GuideCreatedEvent(USER_ID, POST_ID));
  }

  @Test
  @DisplayName("AI 답의 badges와 문단 3개를 응답 모양으로 바꾸고, KEY 목록은 배지에서 만든다")
  void convertsAnswer() throws Exception {
    givenNewGuide();

    requestGuide()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.focusSections.length()").value(1))
        .andExpect(jsonPath("$.focusSections[0]").value(2))
        .andExpect(jsonPath("$.sectionBadges[0].seq").value(1))
        .andExpect(jsonPath("$.sectionBadges[0].badge").value("LIGHT"))
        .andExpect(jsonPath("$.sectionBadges[1].seq").value(2))
        .andExpect(jsonPath("$.sectionBadges[1].badge").value("KEY"))
        .andExpect(jsonPath("$.section1Text").value("볼 부분"))
        .andExpect(jsonPath("$.section2Text").value("읽는 순서"))
        .andExpect(jsonPath("$.section3Text").value("이어서"));
  }

  @Test
  @DisplayName("AI가 KEY 목록을 따로 보내도 쓰지 않고 배지의 KEY로 만든다")
  void ignoresFocusSectionsFromAi() throws Exception {
    givenNewGuide(
        "{\"focusSections\":[1,2],\"badges\":{\"1\":\"LIGHT\",\"2\":\"KEY\"},"
            + "\"lookFor\":\"볼 부분\",\"readingOrder\":\"읽는 순서\",\"nextStudy\":\"이어서\"}");

    requestGuide()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.focusSections.length()").value(1))
        .andExpect(jsonPath("$.focusSections[0]").value(2));
  }

  @Test
  @DisplayName("배지가 구간 수와 다르면 저장하지 않고 502 AI_UNAVAILABLE")
  void badgeCountMismatch() throws Exception {
    givenNewGuide(
        "{\"badges\":{\"1\":\"KEY\"},"
            + "\"lookFor\":\"볼 부분\",\"readingOrder\":\"읽는 순서\",\"nextStudy\":\"이어서\"}");

    requestGuide()
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));
    assertThat(guideRepository.count()).isZero();
    verifyNoInteractions(kafkaEventPublisher);
  }

  @Test
  @DisplayName("모든 구간이 KEY인 답도 저장하고 200, KEY 목록은 모든 구간 번호")
  void allKeyAnswer() throws Exception {
    givenNewGuide(
        "{\"badges\":{\"1\":\"KEY\",\"2\":\"KEY\"},"
            + "\"lookFor\":\"볼 부분\",\"readingOrder\":\"읽는 순서\",\"nextStudy\":\"이어서\"}");

    requestGuide()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.focusSections.length()").value(2))
        .andExpect(jsonPath("$.focusSections[0]").value(1))
        .andExpect(jsonPath("$.focusSections[1]").value(2));
    assertThat(guideRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("AI에게 구간 수, 구간 태그, 빈칸 틀을 담아 보낸다")
  void sendsSectionCountAndTags() throws Exception {
    givenNewGuide();

    requestGuide().andExpect(status().isOk());
    ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
    verify(aiClient).generate(anyString(), input.capture());
    assertThat(input.getValue())
        .contains("total=\"2\"")
        .contains("<section seq=\"1\" heading=\"들어가며\">\n내용 1\n</section>")
        .contains("<section seq=\"2\" heading=\"핵심\">\n내용 2\n</section>")
        .contains("{\"badges\": {\"1\": \"\", \"2\": \"\"}");
  }

  @Test
  @DisplayName("같은 사람·글·프로필 버전의 저장본이 있으면 ⑤·AI·C 발행 없이 cached=true")
  void reusesSavedGuide() throws Exception {
    when(userClient.getProfile(USER_ID)).thenReturn(profile(3));
    guideRepository.save(savedGuide(3));

    requestGuide().andExpect(status().isOk()).andExpect(jsonPath("$.cached").value(true));
    verifyNoInteractions(contentClient, aiClient, kafkaEventPublisher);
  }

  @Test
  @DisplayName("프로필을 등록하지 않았으면(경력 없음) 400 PROFILE_REQUIRED")
  void profileRequired() throws Exception {
    when(userClient.getProfile(USER_ID))
        .thenReturn(new UserProfileResponseDTO(USER_ID, null, null, null, null, 0));

    requestGuide()
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PROFILE_REQUIRED"));
    verifyNoInteractions(contentClient, aiClient, kafkaEventPublisher);
  }

  @Test
  @DisplayName("없는 글이면(⑤ 404) 404 NOT_FOUND")
  void postNotFound() throws Exception {
    when(userClient.getProfile(USER_ID)).thenReturn(profile(3));
    when(contentClient.getGuideInput(POST_ID))
        .thenThrow(new FeignException.NotFound("없는 글", request(), null, Map.of()));

    requestGuide()
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    verifyNoInteractions(aiClient, kafkaEventPublisher);
  }

  @Test
  @DisplayName("user 호출이 실패하면(④ 오류) 502 UPSTREAM_ERROR")
  void userServiceFails() throws Exception {
    when(userClient.getProfile(USER_ID))
        .thenThrow(new FeignException.ServiceUnavailable("user 꺼짐", request(), null, Map.of()));

    requestGuide()
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"));
    verifyNoInteractions(contentClient, aiClient, kafkaEventPublisher);
  }

  @Test
  @DisplayName("④ 응답에 profileVersion이 없으면 502 UPSTREAM_ERROR")
  void brokenProfileResponse() throws Exception {
    when(userClient.getProfile(USER_ID))
        .thenReturn(new UserProfileResponseDTO(USER_ID, 3, List.of(), List.of(), List.of(), null));

    requestGuide()
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"));
    verifyNoInteractions(contentClient, aiClient, kafkaEventPublisher);
  }

  @Test
  @DisplayName("C 발행이 나중에 실패해도 가이드는 저장되고 200")
  void publishFailsLater() throws Exception {
    givenNewGuide();
    when(kafkaEventPublisher.publishGuideCreated(any()))
        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka 꺼짐")));

    requestGuide().andExpect(status().isOk()).andExpect(jsonPath("$.cached").value(false));
    assertThat(guideRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("C 발행을 시작하자마자 실패해도 가이드는 저장되고 200")
  void publishFailsImmediately() throws Exception {
    givenNewGuide();
    when(kafkaEventPublisher.publishGuideCreated(any()))
        .thenThrow(new IllegalStateException("직렬화 실패"));

    requestGuide().andExpect(status().isOk()).andExpect(jsonPath("$.cached").value(false));
    assertThat(guideRepository.count()).isEqualTo(1);
  }

  // ④⑤·AI가 모두 정상이라 새 가이드가 만들어지는 상황
  private void givenNewGuide() {
    givenNewGuide(AI_ANSWER);
  }

  private void givenNewGuide(String aiAnswer) {
    when(userClient.getProfile(USER_ID)).thenReturn(profile(3));
    when(contentClient.getGuideInput(POST_ID)).thenReturn(postInput());
    when(aiClient.generate(anyString(), anyString())).thenReturn(aiAnswer);
  }

  private ResultActions requestGuide() throws Exception {
    return mockMvc.perform(post("/api/posts/{id}/guide", POST_ID).header("X-User-Id", USER_ID));
  }

  private static UserProfileResponseDTO profile(int profileVersion) {
    return new UserProfileResponseDTO(
        USER_ID, 3, List.of("Backend"), List.of("Spring"), List.of("Kafka"), profileVersion);
  }

  private static GuideInputResponseDTO postInput() {
    return new GuideInputResponseDTO(
        POST_ID,
        "제목",
        "Backend",
        "중급",
        List.of(new Section(1, "들어가며", "내용 1"), new Section(2, "핵심", "내용 2")));
  }

  private AiGuideEntity savedGuide(int profileVersion) {
    return AiGuideEntity.builder()
        .userId(USER_ID)
        .blogPostId(POST_ID)
        .profile_version(profileVersion)
        .prompt_version(environment.getProperty("guide.prompt-version"))
        .focus_sections("[2]")
        .section_badges("[{\"seq\":1,\"badge\":\"LIGHT\"},{\"seq\":2,\"badge\":\"KEY\"}]")
        .section1_text("볼 부분")
        .section2_text("읽는 순서")
        .section3_text("이어서")
        .created_at(LocalDateTime.now())
        .build();
  }

  private static Request request() {
    return Request.create(
        Request.HttpMethod.GET, "/internal", Map.of(), null, StandardCharsets.UTF_8, null);
  }
}
