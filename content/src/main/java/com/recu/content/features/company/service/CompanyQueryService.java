package com.recu.content.features.company.service;

import com.recu.common.web.error.BusinessException;
import com.recu.common.web.error.CommonErrorCode;
import com.recu.content.database.entity.BlogPostTagEntity;
import com.recu.content.database.entity.CompanyEntity;
import com.recu.content.database.repository.CompanyRepository;
import com.recu.content.database.repository.UserGuideFlagRepository;
import com.recu.content.features.company.domain.dto.CollectionCompanyResponseDTO;
import com.recu.content.features.company.domain.dto.CompanyListResponseDTO;
import com.recu.content.features.company.domain.dto.CompanyListResponseDTO.CompanyDTO;
import com.recu.content.features.company.domain.dto.CompanyListResponseDTO.SkillResponseDTO;
import com.recu.content.features.company.domain.dto.CompanyPostQueryDTO;
import com.recu.content.features.company.domain.dto.CompanyResponseDTO;
import com.recu.content.features.company.domain.dto.CompanyStatsDTO;
import com.recu.content.features.company.repository.CompanyPostQueryRepository;
import com.recu.content.features.company.repository.CompanyPostQueryRepository.CompanyPostCount;
import com.recu.content.features.company.repository.CompanyPostQueryRepository.CompanySkillPostCount;
import com.recu.content.features.company.repository.CompanyPostQueryRepository.EligiblePost;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanyQueryService {
  private final CompanyRepository companyRepository;
  private final CompanyPostQueryRepository companyPostQueryRepository;
  private final UserGuideFlagRepository guideFlags;

  @Transactional(readOnly = true)
  public List<CollectionCompanyResponseDTO> getCollectionCompanies() {
    return companyRepository.findAll().stream()
        .sorted(Comparator.comparing(CompanyEntity::getCompany_id))
        .map(
            c ->
                new CollectionCompanyResponseDTO(
                    c.getCompany_id(), c.getName(), c.getFeed_url(), c.getFeed_type()))
        .toList();
  }

  @Transactional(readOnly = true)
  public CompanyListResponseDTO getCompanyList(List<String> wantSkills) {
    Set<String> wantSkillNames = new HashSet<>(wantSkills);

    // 글 본문 없이 DB에서 센 값만 가져온다. 관심 기술이 없으면 매칭 집계는 건너뛴다 (#117)
    Map<Long, Long> postsByCompany =
        postCounts(companyPostQueryRepository.countTechPostsByCompany());
    Map<Long, Long> matchedPostsByCompany =
        wantSkillNames.isEmpty()
            ? Map.of()
            : postCounts(companyPostQueryRepository.countMatchedTechPostsByCompany(wantSkillNames));
    Map<Long, List<SkillResponseDTO>> skillsByCompany =
        wantSkillNames.isEmpty() ? Map.of() : matchedSkillsByCompany(wantSkillNames);

    List<CompanyDTO> companies =
        companyRepository.findAll().stream()
            .map(
                company -> {
                  Long companyId = company.getCompany_id();
                  List<SkillResponseDTO> matchedSkills =
                      skillsByCompany.getOrDefault(companyId, List.of());

                  return new CompanyDTO(
                      companyId,
                      company.getName(),
                      company.getSummary(),
                      company.getLogo_url(),
                      matchedSkills.size(),
                      wantSkillNames.size(),
                      matchedPostsByCompany.getOrDefault(companyId, 0L).intValue(),
                      postsByCompany.getOrDefault(companyId, 0L).intValue(),
                      matchedSkills,
                      false);
                })
            .sorted(
                Comparator.comparingInt(CompanyDTO::matchedSkillCount)
                    .reversed()
                    .thenComparing(
                        Comparator.comparingInt(CompanyDTO::matchedPostCount).reversed()))
            .collect(Collectors.toCollection(ArrayList::new));

    if (!companies.isEmpty()) {
      CompanyDTO first = companies.get(0);

      companies.set(
          0,
          new CompanyDTO(
              first.companyId(),
              first.name(),
              first.summary(),
              first.logoUrl(),
              first.matchedSkillCount(),
              first.totalSkillCount(),
              first.matchedPostCount(),
              first.totalPostCount(),
              first.matchedSkills(),
              true));
    }

    return CompanyListResponseDTO.builder().companies(companies).build();
  }

  private static Map<Long, Long> postCounts(List<CompanyPostCount> counts) {
    return counts.stream()
        .collect(Collectors.toMap(CompanyPostCount::getCompanyId, CompanyPostCount::getPostCount));
  }

  private Map<Long, List<SkillResponseDTO>> matchedSkillsByCompany(Set<String> wantSkillNames) {
    return companyPostQueryRepository.countMatchedTechPostsBySkill(wantSkillNames).stream()
        .collect(
            Collectors.groupingBy(
                CompanySkillPostCount::getCompanyId,
                Collectors.mapping(
                    count ->
                        new SkillResponseDTO(count.getSkillName(), count.getPostCount().intValue()),
                    Collectors.toList())));
  }

  @Transactional(readOnly = true)
  public List<CompanyPostQueryDTO> getCompanyPosts(Long companyId, Long userId) {
    if (!companyRepository.existsById(companyId)) {
      throw new BusinessException(CommonErrorCode.NOT_FOUND);
    }
    List<EligiblePost> posts = companyPostQueryRepository.findEligiblePosts(companyId);
    if (posts.isEmpty()) return List.of();

    List<Long> postIds = posts.stream().map(EligiblePost::getPostId).toList();
    Map<Long, List<String>> skills = skills(postIds);
    Map<Long, Long> sectionCounts = sectionCounts(postIds);
    Set<Long> guidedPostIds = new HashSet<>(guideFlags.findGuidedPostIds(userId, postIds));
    return posts.stream()
        .map(
            post ->
                new CompanyPostQueryDTO(
                    post.getPostId(),
                    post.getTitle(),
                    post.getPublishedAt().toLocalDate(),
                    post.getField(),
                    post.getLevel(),
                    post.getSummary(),
                    skills.getOrDefault(post.getPostId(), List.of()),
                    post.getCharCount(),
                    sectionCounts.getOrDefault(post.getPostId(), 0L).intValue(),
                    post.getUrl(),
                    guidedPostIds.contains(post.getPostId())))
        .toList();
  }

  @Transactional(readOnly = true)
  public CompanyResponseDTO getCompanyDetail(Long id) {

    Long companyId = id;

    CompanyEntity company =
        companyRepository
            .findById(companyId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

    List<EligiblePost> eligiblePosts = companyPostQueryRepository.findEligiblePosts(companyId);
    long postCount = eligiblePosts.size();

    List<Long> postIds = eligiblePosts.stream().map(EligiblePost::getPostId).toList();

    List<Map<String, Object>> topCategories =
        eligiblePosts.stream()
            .map(EligiblePost::getField)
            .filter(Objects::nonNull)
            .filter(field -> !field.isBlank())
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
            .entrySet()
            .stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .map(
                entry -> {
                  Map<String, Object> map = new HashMap<>();
                  map.put("name", entry.getKey());
                  map.put("count", entry.getValue().intValue());
                  return map;
                })
            .toList();

    List<Map<String, Object>> topSkills =
        (postIds.isEmpty()
                ? List.<BlogPostTagEntity>of()
                : companyPostQueryRepository.findTags(postIds))
            .stream()
                .map(tag -> tag.getTechTag().getName())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(
                    entry -> {
                      Map<String, Object> map = new HashMap<>();
                      map.put("name", entry.getKey());
                      map.put("count", entry.getValue().intValue());
                      return map;
                    })
                .toList();

    CompanyStatsDTO stats =
        CompanyStatsDTO.builder()
            .postCount((int) postCount)
            .firstPublishedAt(
                eligiblePosts.isEmpty()
                    ? null
                    : eligiblePosts.get(eligiblePosts.size() - 1).getPublishedAt().toLocalDate())
            .lastPublishedAt(
                eligiblePosts.isEmpty()
                    ? null
                    : eligiblePosts.get(0).getPublishedAt().toLocalDate())
            .topCategories(topCategories)
            .topSkills(topSkills)
            .build();

    return CompanyResponseDTO.builder()
        .companyId(company.getCompany_id())
        .name(company.getName())
        .logoUrl(company.getLogo_url())
        .summary(company.getSummary())
        .mainBusiness(company.getMain_business())
        .sourceUrl(company.getSource_url())
        .checkedAt(company.getChecked_at() != null ? company.getChecked_at().toLocalDate() : null)
        .stats(stats)
        .build();
  }

  private Map<Long, List<String>> skills(List<Long> postIds) {
    return companyPostQueryRepository.findTags(postIds).stream()
        .collect(
            Collectors.groupingBy(
                tag -> tag.getId().getBlog_post_id(),
                LinkedHashMap::new,
                Collectors.mapping(tag -> tag.getTechTag().getName(), Collectors.toList())));
  }

  private Map<Long, Long> sectionCounts(List<Long> postIds) {
    return companyPostQueryRepository.findSectionCounts(postIds).stream()
        .collect(
            Collectors.toMap(
                CompanyPostQueryRepository.SectionCount::getPostId,
                CompanyPostQueryRepository.SectionCount::getSectionCount));
  }
}
