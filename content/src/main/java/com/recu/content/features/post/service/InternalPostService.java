package com.recu.content.features.post.service;

import com.recu.common.web.error.BusinessException;
import com.recu.common.web.error.CommonErrorCode;
import com.recu.content.database.entity.BlogPostEntity;
import com.recu.content.database.repository.BlogPostRepository;
import com.recu.content.database.repository.CompanyRepository;
import com.recu.content.features.company.repository.CompanyPostQueryRepository;
import com.recu.content.features.post.domain.dto.InternalPostResponseDTO.GuideInput;
import com.recu.content.features.post.domain.dto.InternalPostResponseDTO.Section;
import com.recu.content.features.post.domain.dto.InternalPostResponseDTO.Summary;
import com.recu.content.features.post.repository.PostSectionRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalPostService {
  private final BlogPostRepository posts;
  private final CompanyRepository companies;
  private final PostSectionRepository sections;
  private final CompanyPostQueryRepository queries;

  public GuideInput getGuideInput(Long postId) {
    BlogPostEntity post =
        posts.findById(postId).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    List<Section> result =
        sections.findByPostId(postId).stream()
            .sorted(Comparator.comparing(s -> s.getSeq()))
            .map(s -> new Section(s.getSeq(), s.getHeading(), s.getContent_text()))
            .toList();
    return new GuideInput(postId, post.getTitle(), post.getField(), post.getLevel(), result);
  }

  public List<String> getCollectedUrls(Long companyId) {
    if (!companies.existsById(companyId)) throw new BusinessException(CommonErrorCode.NOT_FOUND);
    // Already collected non-tech rows must not be reclassified either.
    return posts.findUrlsByCompanyId(companyId);
  }

  public List<Summary> getSummaries(List<Long> requestedIds) {
    List<Long> ids = requestedIds.stream().distinct().toList();
    if (ids.isEmpty()) return List.of();
    Map<Long, BlogPostEntity> found =
        posts.findSummariesWithCompany(ids).stream()
            .collect(Collectors.toMap(BlogPostEntity::getBlog_post_id, Function.identity()));
    if (found.size() != ids.size()) throw new BusinessException(CommonErrorCode.NOT_FOUND);
    Map<Long, List<String>> skills = new HashMap<>();
    queries
        .findTags(ids)
        .forEach(
            t ->
                skills
                    .computeIfAbsent(t.getId().getBlog_post_id(), ignored -> new ArrayList<>())
                    .add(t.getTechTag().getName()));
    return ids.stream()
        .map(
            id -> {
              BlogPostEntity post = found.get(id);
              return new Summary(
                  id,
                  post.getCompany().getCompany_id(),
                  post.getCompany().getName(),
                  post.getTitle(),
                  post.getField() == null || post.getField().isBlank()
                      ? List.of()
                      : List.of(post.getField()),
                  post.getSummary(),
                  skills.getOrDefault(id, List.of()),
                  post.getPublished_at().toLocalDate());
            })
        .toList();
  }
}
