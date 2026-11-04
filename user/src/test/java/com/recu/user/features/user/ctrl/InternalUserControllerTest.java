package com.recu.user.features.user.ctrl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recu.user.client.ContentClient;
import com.recu.user.client.dto.TechTagResponseDTO;
import com.recu.user.database.entity.UserEntity;
import com.recu.user.database.entity.UserJobFieldEntity;
import com.recu.user.database.entity.UserSkillEntity;
import com.recu.user.database.entity.UserSkillPk;
import com.recu.user.database.repository.UserJobFieldRepository;
import com.recu.user.database.repository.UserRepository;
import com.recu.user.database.repository.UserSkillRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** ①·④가 부르는 쪽(content·guide)이 정한 필드 이름과 값으로 응답하는지. content ③은 가짜로 둔다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalUserControllerTest {

  private static final Map<Long, String> TECH_TAGS = Map.of(1L, "Java", 3L, "Kafka");

  @Autowired MockMvc mockMvc;
  @Autowired UserRepository users;
  @Autowired UserJobFieldRepository jobFields;
  @Autowired UserSkillRepository skills;
  @MockitoBean ContentClient contentClient;

  // ③이 불릴 때마다 트랜잭션 안이었는지 남긴다 (#115)
  private final List<Boolean> inTransaction = new ArrayList<>();

  @BeforeEach
  void setUp() {
    skills.deleteAll();
    jobFields.deleteAll();
    users.deleteAll();
    inTransaction.clear();
    when(contentClient.getTechTags(anyString()))
        .thenAnswer(
            call -> {
              inTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
              return Arrays.stream(((String) call.getArgument(0)).split(","))
                  .map(Long::valueOf)
                  .filter(TECH_TAGS::containsKey)
                  .map(id -> new TechTagResponseDTO(id, TECH_TAGS.get(id)))
                  .toList();
            });
  }

  @Test
  @DisplayName("① 프로필을 등록한 회원은 희망 직무·관심 기술 이름·버전")
  void preference() throws Exception {
    Long userId = registered(3, 2);

    mockMvc
        .perform(get("/internal/users/{userId}/preference", userId))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    {"jobFields": ["Backend"], "wantSkillNames": ["Kafka"], "profileVersion": 2}
                    """,
                    true));
  }

  @Test
  @DisplayName("④ 프로필을 등록한 회원은 경력·보유·관심 기술 이름·희망 직무·버전")
  void profile() throws Exception {
    Long userId = registered(3, 2);

    mockMvc
        .perform(get("/internal/users/{userId}/profile", userId))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    {"userId": %d, "careerYears": 3, "jobFields": ["Backend"],
                     "haveSkills": ["Java"], "wantSkills": ["Kafka"], "profileVersion": 2}
                    """
                        .formatted(userId),
                    true));
  }

  @Test
  @DisplayName("④ 경력을 비워 두었으면 careerYears는 null")
  void profileWithoutCareer() throws Exception {
    Long userId = registered(null, 1);

    mockMvc
        .perform(get("/internal/users/{userId}/profile", userId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.careerYears").value(nullValue()))
        .andExpect(jsonPath("$.profileVersion").value(1));
  }

  @Test
  @DisplayName("프로필을 등록하지 않은 회원은 profileVersion 0, 빈 목록, ③을 부르지 않는다")
  void notRegistered() throws Exception {
    Long userId = user(null, 0).getUser_id();

    mockMvc
        .perform(get("/internal/users/{userId}/preference", userId))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    {"jobFields": [], "wantSkillNames": [], "profileVersion": 0}
                    """,
                    true));
    mockMvc
        .perform(get("/internal/users/{userId}/profile", userId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.careerYears").value(nullValue()))
        .andExpect(jsonPath("$.haveSkills").isEmpty())
        .andExpect(jsonPath("$.wantSkills").isEmpty())
        .andExpect(jsonPath("$.profileVersion").value(0));
    verify(contentClient, never()).getTechTags(anyString());
  }

  @Test
  @DisplayName("①·④는 content ③을 트랜잭션 밖에서 불러, ③을 기다리는 동안 DB 연결을 쥐지 않는다")
  void callsContentOutsideTransaction() throws Exception {
    Long userId = registered(3, 2);

    mockMvc.perform(get("/internal/users/{userId}/preference", userId)).andExpect(status().isOk());
    mockMvc.perform(get("/internal/users/{userId}/profile", userId)).andExpect(status().isOk());

    assertThat(inTransaction).isNotEmpty().containsOnly(false);
  }

  @Test
  @DisplayName("없는 회원은 ①·④ 모두 404 NOT_FOUND")
  void missingUser() throws Exception {
    mockMvc
        .perform(get("/internal/users/{userId}/preference", 9999))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    mockMvc
        .perform(get("/internal/users/{userId}/profile", 9999))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  // 보유 1번(Java), 관심 3번(Kafka), 희망 직무 Backend로 프로필을 등록한 회원
  private Long registered(Integer careerYears, int profileVersion) {
    UserEntity user = user(careerYears, profileVersion);
    jobFields.save(UserJobFieldEntity.builder().user(user).jobField("Backend").build());
    skills.save(skill(user, 1L, "HAVE"));
    skills.save(skill(user, 3L, "WANT"));
    return user.getUser_id();
  }

  private UserEntity user(Integer careerYears, int profileVersion) {
    return users.save(
        UserEntity.builder()
            .email("test-" + java.util.UUID.randomUUID() + "@example.com")
            .password_hash("test-password-hash")
            .name("테스트")
            .career_years(careerYears)
            .profile_version(profileVersion)
            .created_at(LocalDate.now())
            .updated_at(LocalDate.now())
            .build());
  }

  private UserSkillEntity skill(UserEntity user, Long techTagId, String type) {
    return UserSkillEntity.builder()
        .id(new UserSkillPk(user.getUser_id(), techTagId))
        .user(user)
        .skill_type(type)
        .build();
  }
}
