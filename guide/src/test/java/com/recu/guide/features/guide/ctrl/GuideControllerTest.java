package com.recu.guide.features.guide.ctrl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
      "{\"focusSections\":[2],"
          + "\"sectionBadges\":[{\"seq\":1,\"badge\":\"LIGHT\"},{\"seq\":2,\"badge\":\"KEY\"}],"
          + "\"section1Text\":\"볼 부분\",\"section2Text\":\"읽는 순서\",\"section3Text\":\"이어서\"}";

  @Autowired MockMvc mockMvc;
  @Autowired GuideRepository guideRepository;
  @Autowired Environment environment;
  @MockitoBean UserClient userClient;
  @MockitoBean ContentClient contentClient;
  @MockitoBean OpenAiGuideClient aiClient;

  @BeforeEach
  void setUp() {
    guideRepository.deleteAll();
  }

  @Test
  @DisplayName("처음 만들면 ④·⑤·AI를 차례로 불러 저장하고 cached=false")
  void createsNewGuide() throws Exception {
    when(userClient.getProfile(USER_ID)).thenReturn(profile(3));
    when(contentClient.getGuideInput(POST_ID)).thenReturn(postInput());
    when(aiClient.generate(anyString())).thenReturn(AI_ANSWER);

    requestGuide()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cached").value(false))
        .andExpect(jsonPath("$.basis.careerYears").value(3))
        .andExpect(jsonPath("$.focusSections[0]").value(2));
    assertThat(guideRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("같은 사람·글·프로필 버전의 저장본이 있으면 ⑤와 AI를 부르지 않고 cached=true")
  void reusesSavedGuide() throws Exception {
    when(userClient.getProfile(USER_ID)).thenReturn(profile(3));
    guideRepository.save(savedGuide(3));

    requestGuide().andExpect(status().isOk()).andExpect(jsonPath("$.cached").value(true));
    verifyNoInteractions(contentClient, aiClient);
  }

  @Test
  @DisplayName("프로필을 등록하지 않았으면(경력 없음) 400 PROFILE_REQUIRED")
  void profileRequired() throws Exception {
    when(userClient.getProfile(USER_ID))
        .thenReturn(new UserProfileResponseDTO(USER_ID, null, null, null, null, 0));

    requestGuide()
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PROFILE_REQUIRED"));
    verifyNoInteractions(contentClient, aiClient);
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
    verifyNoInteractions(aiClient);
  }

  @Test
  @DisplayName("user 호출이 실패하면(④ 오류) 502 UPSTREAM_ERROR")
  void userServiceFails() throws Exception {
    when(userClient.getProfile(USER_ID))
        .thenThrow(new FeignException.ServiceUnavailable("user 꺼짐", request(), null, Map.of()));

    requestGuide()
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"));
    verifyNoInteractions(contentClient, aiClient);
  }

  @Test
  @DisplayName("④ 응답에 profileVersion이 없으면 502 UPSTREAM_ERROR")
  void brokenProfileResponse() throws Exception {
    when(userClient.getProfile(USER_ID))
        .thenReturn(new UserProfileResponseDTO(USER_ID, 3, List.of(), List.of(), List.of(), null));

    requestGuide()
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"));
    verifyNoInteractions(contentClient, aiClient);
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
