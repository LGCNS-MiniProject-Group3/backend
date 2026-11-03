package com.recu.guide.features.guide.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recu.common.web.error.BusinessException;
import com.recu.common.web.error.CommonErrorCode;
import com.recu.guide.client.ContentClient;
import com.recu.guide.client.UserClient;
import com.recu.guide.client.dto.GuideInputResponseDTO;
import com.recu.guide.client.dto.GuideInputResponseDTO.Section;
import com.recu.guide.client.dto.UserProfileResponseDTO;
import com.recu.guide.database.entity.AiGuideEntity;
import com.recu.guide.error.GuideErrorCode;
import com.recu.guide.features.guide.domain.dto.GuideResponseDTO;
import com.recu.guide.features.guide.domain.dto.GuideResponseDTO.Basis;
import com.recu.guide.features.guide.domain.dto.GuideResponseDTO.SectionBadge;
import com.recu.guide.features.guide.repository.GuideRepository;
import com.recu.guide.messaging.GuideCreatedPublisher;
import feign.FeignException;
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
  private final UserClient userClient;
  private final ContentClient contentClient;
  private final OpenAiGuideClient aiClient;
  private final GuideCreatedPublisher guideCreatedPublisher;
  private final ObjectMapper objectMapper;
  private final String promptVersion;

  public GuideService(
      GuideRepository guideRepository,
      UserClient userClient,
      ContentClient contentClient,
      OpenAiGuideClient aiClient,
      GuideCreatedPublisher guideCreatedPublisher,
      ObjectMapper objectMapper,
      @Value("${guide.prompt-version}") String promptVersion) {
    this.guideRepository = guideRepository;
    this.userClient = userClient;
    this.contentClient = contentClient;
    this.aiClient = aiClient;
    this.guideCreatedPublisher = guideCreatedPublisher;
    this.objectMapper = objectMapper;
    this.promptVersion = promptVersion;
  }

  public GuideResponseDTO generateGuide(Long userId, Long postId) {
    UserProfileResponseDTO profile = profile(userId);
    if (profile.careerYears() == null) {
      throw new BusinessException(GuideErrorCode.PROFILE_REQUIRED);
    }

    // 저장된 가이드가 있으면 글 정보(⑤)와 AI 없이 돌려준다 (design.md 7장)
    return guideRepository
        .findCached(userId, postId, profile.profileVersion(), promptVersion)
        .map(guide -> response(guide, profile, true))
        .orElseGet(() -> generate(userId, postId, profile));
  }

  private GuideResponseDTO generate(Long userId, Long postId, UserProfileResponseDTO profile) {
    GuideInputResponseDTO post = guideInput(postId);
    List<Section> sections = post.sections();
    GeneratedGuide generated =
        parseAndValidate(callAi(buildPrompt(profile, post, sections)), sections.size());

    AiGuideEntity guide =
        AiGuideEntity.builder()
            .userId(userId)
            .blogPostId(postId)
            .profile_version(profile.profileVersion())
            .prompt_version(promptVersion)
            .focus_sections(writeJson(generated.focusSections()))
            .section_badges(writeJson(generated.sectionBadges()))
            .section1_text(generated.section1Text())
            .section2_text(generated.section2Text())
            .section3_text(generated.section3Text())
            .created_at(LocalDateTime.now())
            .build();

    AiGuideEntity saved;
    try {
      saved = guideRepository.save(guide);
    } catch (DataIntegrityViolationException exception) {
      // 같은 가이드를 동시에 저장하려다 진 쪽. C는 먼저 저장한 요청이 보내므로 여기서는 보내지 않는다
      return guideRepository
          .findCached(userId, postId, profile.profileVersion(), promptVersion)
          .map(existing -> response(existing, profile, true))
          .orElseThrow(this::aiUnavailable);
    }

    // 저장이 확정된 뒤에 보낸다 (design.md 6장).
    // 이 서비스는 트랜잭션을 따로 열지 않아 save()가 끝나면 이미 커밋된 상태다
    guideCreatedPublisher.publish(userId, postId);
    return response(saved, profile, false);
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

  private GuideResponseDTO response(
      AiGuideEntity guide, UserProfileResponseDTO profile, boolean cached) {
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

  private Basis basis(UserProfileResponseDTO profile) {
    return new Basis(profile.careerYears(), profile.haveSkills(), profile.wantSkills());
  }

  private String buildPrompt(
      UserProfileResponseDTO profile, GuideInputResponseDTO post, List<Section> sections) {
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

  // ④ 응답이 약속과 다르면 user 쪽 문제로 보고 502 (content가 ①을 받을 때와 같은 처리).
  // careerYears가 null이면(미등록이거나 경력을 비워 둔 경우) 여기서 막지 않는다 (호출한 쪽에서 400)
  private UserProfileResponseDTO profile(Long userId) {
    UserProfileResponseDTO profile = userClient.getProfile(userId);
    if (profile == null || profile.profileVersion() == null) {
      throw new BusinessException(CommonErrorCode.UPSTREAM_ERROR);
    }
    if (profile.careerYears() != null && hasMissingList(profile)) {
      throw new BusinessException(CommonErrorCode.UPSTREAM_ERROR);
    }
    return profile;
  }

  // 프로필을 등록한 사람인데 직무·기술 목록이 비어(null) 오면 AI 입력을 만들 수 없다
  private static boolean hasMissingList(UserProfileResponseDTO profile) {
    return profile.jobFields() == null
        || profile.haveSkills() == null
        || profile.wantSkills() == null;
  }

  // common-web은 모든 Feign 실패를 502로 바꾼다. 없는 글(⑤ 404)만 여기서 404로 바꿔 원본 API와 맞춘다
  private GuideInputResponseDTO guideInput(Long postId) {
    GuideInputResponseDTO post;
    try {
      post = contentClient.getGuideInput(postId);
    } catch (FeignException.NotFound exception) {
      throw new BusinessException(CommonErrorCode.NOT_FOUND);
    }
    if (post == null || post.sections() == null) {
      throw new BusinessException(CommonErrorCode.UPSTREAM_ERROR);
    }
    return post;
  }
}
