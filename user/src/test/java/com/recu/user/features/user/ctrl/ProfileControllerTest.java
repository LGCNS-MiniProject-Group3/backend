package com.recu.user.features.user.ctrl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recu.user.client.ContentClient;
import com.recu.user.client.dto.TechTagResponseDTO;
import com.recu.user.database.entity.UserEntity;
import com.recu.user.database.repository.UserJobFieldRepository;
import com.recu.user.database.repository.UserRepository;
import com.recu.user.database.repository.UserSkillRepository;
import feign.FeignException;
import feign.Request;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 프로필 저장·조회가 content ③으로 기술 번호를 확인하고 이름을 채우는지. content는 가짜로 둔다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileControllerTest {

  // content에 있는 기술이라고 치는 번호와 이름
  private static final Map<Long, String> TECH_TAGS =
      Map.of(1L, "Java", 2L, "Spring", 3L, "Kafka", 4L, "Redis");

  @Autowired MockMvc mockMvc;
  @Autowired UserRepository users;
  @Autowired UserJobFieldRepository jobFields;
  @Autowired UserSkillRepository skills;
  @MockitoBean ContentClient contentClient;

  private Long userId;

  @BeforeEach
  void setUp() {
    skills.deleteAll();
    jobFields.deleteAll();
    users.deleteAll();
    userId =
        users
            .save(
                UserEntity.builder()
                    .email("test@example.com")
                    .password_hash("test-password-hash")
                    .name("테스트")
                    .created_at(LocalDate.now())
                    .updated_at(LocalDate.now())
                    .build())
            .getUser_id();
    // ③처럼 있는 번호만, 같은 번호는 한 번만 돌려준다
    when(contentClient.getTechTags(anyString()))
        .thenAnswer(
            call ->
                Arrays.stream(((String) call.getArgument(0)).split(","))
                    .map(Long::valueOf)
                    .distinct()
                    .filter(TECH_TAGS::containsKey)
                    .map(id -> new TechTagResponseDTO(id, TECH_TAGS.get(id)))
                    .toList());
  }

  @Test
  @DisplayName("있는 기술이면 저장하고 응답에 기술 이름을 채운다")
  void saveFillsSkillNames() throws Exception {
    mockMvc
        .perform(profile(post("/api/profile"), "[1, 2]", "[3, 4]"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.profileVersion").value(1))
        .andExpect(jsonPath("$.haveSkills[*].name", containsInAnyOrder("Java", "Spring")))
        .andExpect(jsonPath("$.wantSkills[*].name", containsInAnyOrder("Kafka", "Redis")));
    assertThat(skills.count()).isEqualTo(4);
  }

  @Test
  @DisplayName("보유 기술이 비어 있으면 ③을 그 목록으로는 부르지 않는다")
  void emptyHaveSkillsSkipsCall() throws Exception {
    mockMvc
        .perform(profile(post("/api/profile"), "[]", "[3]"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.haveSkills").isEmpty())
        .andExpect(jsonPath("$.wantSkills[0].name").value("Kafka"));
    verify(contentClient, never()).getTechTags("");
  }

  @Test
  @DisplayName("없는 기술 번호가 섞이면 400 INVALID_INPUT, 저장하지 않는다")
  void unknownSkillId() throws Exception {
    mockMvc
        .perform(profile(post("/api/profile"), "[1]", "[3, 999]"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.field").value("wantSkillIds"));
    assertThat(skills.count()).isZero();
    assertThat(users.findById(userId).orElseThrow().getProfile_version()).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1"})
  @DisplayName("0 이하 기술 번호는 ③을 부르지 않고 400 INVALID_INPUT")
  void nonPositiveSkillId(String techTagId) throws Exception {
    mockMvc
        .perform(profile(post("/api/profile"), "[" + techTagId + "]", "[3]"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.field").value("haveSkillIds"));
    verify(contentClient, never()).getTechTags(anyString());
    assertThat(skills.count()).isZero();
  }

  @Test
  @DisplayName("content가 응답하지 않으면 502 UPSTREAM_ERROR")
  void contentDown() throws Exception {
    when(contentClient.getTechTags(any())).thenThrow(feignError(503));

    mockMvc
        .perform(profile(post("/api/profile"), "[1]", "[3]"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"));
    assertThat(skills.count()).isZero();
  }

  @Test
  @DisplayName("조회도 ③으로 기술 이름을 채운다")
  void getFillsSkillNames() throws Exception {
    mockMvc.perform(profile(post("/api/profile"), "[1]", "[3]")).andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/profile").header("X-User-Id", userId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jobFields[0]").value("Backend"))
        .andExpect(jsonPath("$.haveSkills[0].name").value("Java"))
        .andExpect(jsonPath("$.wantSkills[0].name").value("Kafka"));
  }

  private MockHttpServletRequestBuilder profile(
      MockHttpServletRequestBuilder request, String haveSkillIds, String wantSkillIds) {
    return request
        .header("X-User-Id", userId)
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"jobFields": ["Backend"], "careerYears": 3, "haveSkillIds": %s, "wantSkillIds": %s}
            """
                .formatted(haveSkillIds, wantSkillIds));
  }

  private static FeignException feignError(int status) {
    Request request =
        Request.create(
            Request.HttpMethod.GET,
            "http://content/internal/tech-tags",
            Map.of(),
            null,
            StandardCharsets.UTF_8,
            null);
    return FeignException.errorStatus(
        "ContentClient#getTechTags",
        feign.Response.builder()
            .status(status)
            .reason("x")
            .request(request)
            .headers(Map.of())
            .build());
  }
}
