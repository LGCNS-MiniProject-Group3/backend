package com.recu.guide.features.guide.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recu.common.web.error.BusinessException;
import com.recu.guide.database.entity.AiGuideEntity;
import com.recu.guide.error.GuideErrorCode;
import com.recu.guide.features.guide.domain.dto.GuideResponseDTO;
import com.recu.guide.features.guide.domain.dto.GuideResponseDTO.Basis;
import com.recu.guide.features.guide.domain.dto.GuideResponseDTO.SectionBadge;
import com.recu.guide.features.guide.repository.GuideRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class GuideService {

  private static final Set<String> OUTPUT_KEYS =
      Set.of("focusSections", "sectionBadges", "section1Text", "section2Text", "section3Text");
  private static final Set<String> BADGE_KEYS = Set.of("seq", "badge");
  private static final Set<String> BADGES = Set.of("KEY", "LIGHT", "SKIP", "REF");

  private final GuideRepository guideRepository;
  private final OpenAiGuideClient aiClient;
  private final ObjectMapper objectMapper;
  private final String promptVersion;

  public GuideService(
      GuideRepository guideRepository,
      OpenAiGuideClient aiClient,
      ObjectMapper objectMapper,
      @Value("${guide.prompt-version}") String promptVersion) {
    this.guideRepository = guideRepository;
    this.aiClient = aiClient;
    this.objectMapper = objectMapper;
    this.promptVersion = promptVersion;
  }

  public GuideResponseDTO generateGuide(Long userId, Long postId) {
    Profile profile = profile(userId);
    if (profile.careerYears() == null) {
      throw new BusinessException(GuideErrorCode.PROFILE_REQUIRED);
    }

    GuideInput post = guideInput(postId);

    return guideRepository
        .findCached(userId, postId, profile.profileVersion(), promptVersion)
        .map(guide -> response(guide, profile, true))
        .orElseGet(() -> generate(profile, post));
  }

  private GuideResponseDTO generate(Profile profile, GuideInput post) {
    List<Section> sections = post.sections();
    GeneratedGuide generated =
        parseAndValidate(callAi(buildPrompt(profile, post, sections)), sections.size());

    AiGuideEntity guide =
        AiGuideEntity.builder()
            .userId(profile.userId())
            .blogPostId(post.postId())
            .profile_version(profile.profileVersion())
            .prompt_version(promptVersion)
            .focus_sections(writeJson(generated.focusSections()))
            .section_badges(writeJson(generated.sectionBadges()))
            .section1_text(generated.section1Text())
            .section2_text(generated.section2Text())
            .section3_text(generated.section3Text())
            .created_at(LocalDateTime.now())
            .build();

    try {
      return response(guideRepository.save(guide), profile, false);
    } catch (DataIntegrityViolationException exception) {
      return guideRepository
          .findCached(profile.userId(), post.postId(), profile.profileVersion(), promptVersion)
          .map(saved -> response(saved, profile, true))
          .orElseThrow(this::aiUnavailable);
    }
  }

  private String callAi(String prompt) {
    try {
      return aiClient.generate(prompt);
    } catch (Exception exception) {
      throw aiUnavailable();
    }
  }

  private GeneratedGuide parseAndValidate(String raw, int sectionCount) {
    try {
      JsonNode root = objectMapper.readTree(raw);
      if (!root.isObject() || !fieldNames(root).equals(OUTPUT_KEYS)) {
        throw aiUnavailable();
      }

      JsonNode badges = root.get("sectionBadges");
      if (!badges.isArray()) {
        throw aiUnavailable();
      }
      badges.forEach(
          badge -> {
            if (!badge.isObject() || !fieldNames(badge).equals(BADGE_KEYS)) {
              throw aiUnavailable();
            }
          });

      GeneratedGuide generated = objectMapper.treeToValue(root, GeneratedGuide.class);
      validate(generated, sectionCount);
      return generated;
    } catch (BusinessException exception) {
      throw exception;
    } catch (Exception exception) {
      throw aiUnavailable();
    }
  }

  private void validate(GeneratedGuide generated, int sectionCount) {
    if (generated.focusSections() == null
        || generated.sectionBadges() == null
        || generated.sectionBadges().size() != sectionCount
        || generated.section1Text() == null
        || generated.section2Text() == null
        || generated.section3Text() == null) {
      throw aiUnavailable();
    }

    List<Integer> keySections = new ArrayList<>();
    for (int index = 0; index < generated.sectionBadges().size(); index++) {
      SectionBadge sectionBadge = generated.sectionBadges().get(index);
      if (sectionBadge == null
          || sectionBadge.seq() != index + 1
          || !BADGES.contains(sectionBadge.badge())) {
        throw aiUnavailable();
      }
      if ("KEY".equals(sectionBadge.badge())) {
        keySections.add(sectionBadge.seq());
      }
    }

    if (sectionCount > 1 && keySections.size() == sectionCount) {
      throw aiUnavailable();
    }

    if (generated.focusSections().stream()
            .anyMatch(seq -> seq == null || seq < 1 || seq > sectionCount)
        || new LinkedHashSet<>(generated.focusSections()).size() != generated.focusSections().size()
        || !generated.focusSections().equals(keySections)) {
      throw aiUnavailable();
    }
  }

  private GuideResponseDTO response(AiGuideEntity guide, Profile profile, boolean cached) {
    return new GuideResponseDTO(
        guide.getAi_guide_id(),
        cached,
        basis(profile),
        readJson(guide.getFocus_sections(), new TypeReference<List<Integer>>() {}),
        readJson(guide.getSection_badges(), new TypeReference<List<SectionBadge>>() {}),
        guide.getSection1_text(),
        guide.getSection2_text(),
        guide.getSection3_text());
  }

  private Basis basis(Profile profile) {
    return new Basis(profile.careerYears(), profile.haveSkills(), profile.wantSkills());
  }

  private String buildPrompt(Profile profile, GuideInput post, List<Section> sections) {
    StringBuilder input =
        new StringBuilder(loadPrompt())
            .append("\n\n[프로필]\n경력 연차: ")
            .append(profile.careerYears())
            .append("\n희망 직무: ")
            .append(String.join(", ", profile.jobFields()))
            .append("\n보유 기술: ")
            .append(String.join(", ", profile.haveSkills()))
            .append("\n관심 기술: ")
            .append(String.join(", ", profile.wantSkills()))
            .append("\n\n[제목]\n")
            .append(post.title())
            .append("\n\n[계열]\n")
            .append(post.field())
            .append("\n\n[난이도]\n")
            .append(post.level())
            .append("\n\n[본문 구간]");

    for (int index = 0; index < sections.size(); index++) {
      Section section = sections.get(index);
      input
          .append("\n\n[")
          .append(index + 1)
          .append("]\n제목: ")
          .append(section.heading())
          .append("\n내용: ")
          .append(section.contentText());
    }
    return input.toString();
  }

  private String loadPrompt() {
    try {
      return new ClassPathResource("prompts/guide-v1.txt")
          .getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw aiUnavailable();
    }
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw aiUnavailable();
    }
  }

  private <T> T readJson(String value, TypeReference<T> type) {
    try {
      return objectMapper.readValue(value, type);
    } catch (JsonProcessingException exception) {
      throw aiUnavailable();
    }
  }

  private Set<String> fieldNames(JsonNode node) {
    Set<String> fields = new LinkedHashSet<>();
    node.fieldNames().forEachRemaining(fields::add);
    return fields;
  }

  private BusinessException aiUnavailable() {
    return new BusinessException(GuideErrorCode.AI_UNAVAILABLE);
  }

  private record GeneratedGuide(
      List<Integer> focusSections,
      List<SectionBadge> sectionBadges,
      String section1Text,
      String section2Text,
      String section3Text) {}

  // ---- 분리 이식 때 둔 대역. 담당 작업에서 Feign으로 바꾼다 ----

  /** ④ 응답 모양 (docs/design.md 5장): 경력, 보유·관심 기술, 희망 직무, 프로필 버전. */
  record Profile(
      Long userId,
      Integer careerYears,
      List<String> jobFields,
      List<String> haveSkills,
      List<String> wantSkills,
      Integer profileVersion) {}

  /** ⑤ 응답 모양 (docs/design.md 5장): 제목, 계열, 난이도, 구간 목록. */
  record GuideInput(
      Long postId, String title, String field, String level, List<Section> sections) {}

  record Section(String heading, String contentText) {}

  // TODO(④): user의 GET /internal/users/{userId}/profile 로 묻는다.
  //  원본: profileService.getProfile(userId) (같은 서버의 user 기능 직접 호출)
  private Profile profile(Long userId) {
    throw new UnsupportedOperationException("TODO(④): 프로필 조회 미구현");
  }

  // TODO(⑤): content의 GET /internal/posts/{postId}/guide-input 으로 묻는다. 없는 글이면 404 NOT_FOUND.
  //  원본: postRepository.findById(postId), sectionRepository.findByPostId(postId)
  // (blog_post·blog_post_section 표 직접 조회)
  private GuideInput guideInput(Long postId) {
    throw new UnsupportedOperationException("TODO(⑤): 글 정보 조회 미구현");
  }
}
