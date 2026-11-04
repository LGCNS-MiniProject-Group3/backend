package com.recu.user.features.user.ctrl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recu.common.kafka.KafkaEventPublisher;
import com.recu.common.kafka.event.ProfileUpdatedEvent;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 프로필 저장·조회가 content ③으로 기술 번호를 확인하고 이름을 채우는지, 저장이 끝나면 B를 보내는지. content와 Kafka 발행은 가짜로 둔다. */
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
  @MockitoBean KafkaEventPublisher kafka;

  // ③이 불릴 때마다 트랜잭션 안이었는지 남긴다 (#115)
  private final List<Boolean> inTransaction = new ArrayList<>();

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
    inTransaction.clear();
    // ③처럼 있는 번호만, 같은 번호는 한 번만 돌려준다
    when(contentClient.getTechTags(anyString()))
        .thenAnswer(
            call -> {
              inTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
              return Arrays.stream(((String) call.getArgument(0)).split(","))
                  .map(Long::valueOf)
                  .distinct()
                  .filter(TECH_TAGS::containsKey)
                  .map(id -> new TechTagResponseDTO(id, TECH_TAGS.get(id)))
                  .toList();
            });
    when(kafka.publishProfileUpdated(any())).thenReturn(new CompletableFuture<>());
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

  @Test
  @DisplayName("조회는 content ③을 트랜잭션 밖에서 불러, ③을 기다리는 동안 DB 연결을 쥐지 않는다")
  void getCallsContentOutsideTransaction() throws Exception {
    mockMvc.perform(profile(post("/api/profile"), "[1]", "[3]")).andExpect(status().isCreated());
    inTransaction.clear();

    mockMvc.perform(get("/api/profile").header("X-User-Id", userId)).andExpect(status().isOk());

    assertThat(inTransaction).isNotEmpty().containsOnly(false);
  }

  // ---- B recu.profile.updated 발행 (#61) ----

  @Test
  @DisplayName("프로필을 등록하면 커밋 뒤 B를 userId로 1번 보낸다")
  void saveSendsProfileUpdated() throws Exception {
    mockMvc.perform(profile(post("/api/profile"), "[1]", "[3]")).andExpect(status().isCreated());

    ArgumentCaptor<ProfileUpdatedEvent> sent = ArgumentCaptor.forClass(ProfileUpdatedEvent.class);
    verify(kafka, times(1)).publishProfileUpdated(sent.capture());
    assertThat(sent.getValue().userId()).isEqualTo(userId);
  }

  @Test
  @DisplayName("프로필을 수정해도 B를 1번 보낸다")
  void editSendsProfileUpdated() throws Exception {
    mockMvc.perform(profile(post("/api/profile"), "[1]", "[3]")).andExpect(status().isCreated());
    clearInvocations(kafka);

    mockMvc
        .perform(profile(put("/api/profile"), "[2]", "[4]"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profileVersion").value(2));

    verify(kafka, times(1)).publishProfileUpdated(new ProfileUpdatedEvent(userId));
  }

  @Test
  @DisplayName("입력이 틀리거나 content가 응답하지 않아 저장하지 못하면 B를 보내지 않는다")
  void failedSaveSendsNothing() throws Exception {
    mockMvc
        .perform(profile(post("/api/profile"), "[1]", "[3, 999]"))
        .andExpect(status().isBadRequest());
    when(contentClient.getTechTags(any())).thenThrow(feignError(503));
    mockMvc.perform(profile(post("/api/profile"), "[1]", "[3]")).andExpect(status().isBadGateway());

    verify(kafka, never()).publishProfileUpdated(any());
  }

  @Test
  @DisplayName("B를 보내다 바로 실패해도 프로필은 저장되고 응답은 그대로다")
  void publishFailureKeepsResponse() throws Exception {
    when(kafka.publishProfileUpdated(any())).thenThrow(new IllegalStateException("Kafka 없음"));

    mockMvc.perform(profile(post("/api/profile"), "[1]", "[3]")).andExpect(status().isCreated());

    assertThat(users.findById(userId).orElseThrow().getProfile_version()).isEqualTo(1);
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
