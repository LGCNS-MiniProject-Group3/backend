package com.recu.content.features.company.service;

import com.recu.common.web.error.BusinessException;
import com.recu.content.client.ContentBookmarkLookup;
import com.recu.content.client.ContentUserPreference;
import com.recu.content.client.dto.UserPreferenceResponseDTO;
import com.recu.content.error.ContentErrorCode;
import com.recu.content.features.company.domain.dto.CollectionCompanyResponseDTO;
import com.recu.content.features.company.domain.dto.CompanyListResponseDTO;
import com.recu.content.features.company.domain.dto.CompanyPostListResponseDTO;
import com.recu.content.features.company.domain.dto.CompanyPostListResponseDTO.Filter;
import com.recu.content.features.company.domain.dto.CompanyPostListResponseDTO.Post;
import com.recu.content.features.company.domain.dto.CompanyPostQueryDTO;
import com.recu.content.features.company.domain.dto.CompanyResponseDTO;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CompanyService {
  private final CompanyQueryService queries;
  private final ContentUserPreference userPreference;
  private final ContentBookmarkLookup bookmarkLookup;

  public List<CollectionCompanyResponseDTO> getCollectionCompanies() {
    return queries.getCollectionCompanies();
  }

  public CompanyListResponseDTO getCompanyList(Long userId) {
    UserPreferenceResponseDTO preference = userPreference.get(userId);
    if (preference.profileVersion() == 0) {
      throw new BusinessException(ContentErrorCode.PROFILE_REQUIRED);
    }
    return queries.getCompanyList(preference.wantSkillNames());
  }

  public CompanyResponseDTO getCompanyDetail(Long companyId) {
    return queries.getCompanyDetail(companyId);
  }

  public CompanyPostListResponseDTO getCompanyPostList(
      Long companyId, boolean onlyMySkills, Long userId) {
    // 엔티티 대신 조회 데이터를 받아 외부 응답을 기다리는 동안 DB 연결을 점유하지 않는다.
    List<CompanyPostQueryDTO> posts = queries.getCompanyPosts(companyId, userId);
    if (posts.isEmpty()) {
      return new CompanyPostListResponseDTO(new Filter(onlyMySkills, 0, 0), List.of());
    }
    UserPreferenceResponseDTO preference = userPreference.get(userId);
    List<Long> postIds = posts.stream().map(CompanyPostQueryDTO::postId).toList();
    Set<Long> bookmarks = bookmarkLookup.get(userId, postIds);
    List<PostMatch> matches =
        posts.stream()
            .map(
                post -> {
                  List<String> matchedSkills =
                      matchedSkills(post.skills(), preference.wantSkillNames());
                  boolean fieldMatches =
                      "General".equals(post.field())
                          || (post.field() != null
                              && preference.jobFields().contains(post.field()));
                  Post response =
                      new Post(
                          post.postId(),
                          post.title(),
                          post.publishedAt(),
                          post.field() == null ? List.of() : List.of(post.field()),
                          post.level(),
                          post.summary(),
                          post.skills(),
                          matchedSkills,
                          post.charCount(),
                          post.sectionCount(),
                          post.originalUrl(),
                          bookmarks.contains(post.postId()),
                          post.hasGuide());
                  return new PostMatch(response, fieldMatches && !matchedSkills.isEmpty());
                })
            .toList();
    List<Post> personalized =
        matches.stream()
            .filter(PostMatch::personalized)
            .map(PostMatch::post)
            .sorted(
                Comparator.comparingInt((Post post) -> post.matchedSkills().size())
                    .reversed()
                    .thenComparing(Post::publishedAt, Comparator.reverseOrder()))
            .toList();
    List<Post> result =
        onlyMySkills
            ? personalized
            : matches.stream()
                .map(PostMatch::post)
                .sorted(Comparator.comparing(Post::publishedAt).reversed())
                .toList();
    return new CompanyPostListResponseDTO(
        new Filter(onlyMySkills, personalized.size(), posts.size()), result);
  }

  private List<String> matchedSkills(List<String> postSkills, List<String> wantSkills) {
    if (wantSkills.isEmpty()) {
      return List.of();
    }
    List<Pattern> patterns =
        wantSkills.stream()
            .map(
                skill ->
                    Pattern.compile(
                        "(?<![\\p{L}\\p{N}])" + Pattern.quote(skill) + "(?![\\p{L}\\p{N}])",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
            .toList();
    return postSkills.stream()
        .filter(skill -> patterns.stream().anyMatch(pattern -> pattern.matcher(skill).find()))
        .toList();
  }

  private record PostMatch(Post post, boolean personalized) {}
}
