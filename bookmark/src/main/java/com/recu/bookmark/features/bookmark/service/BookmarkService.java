package com.recu.bookmark.features.bookmark.service;

import com.recu.bookmark.client.ContentClient;
import com.recu.bookmark.client.dto.PostSummariesRequestDTO;
import com.recu.bookmark.client.dto.PostSummaryResponseDTO;
import com.recu.bookmark.database.entity.BookmarkEntity;
import com.recu.bookmark.error.BookmarkErrorCode;
import com.recu.bookmark.features.bookmark.domain.dto.BookmarkResponseDTO;
import com.recu.bookmark.features.bookmark.repository.BookmarkRepository;
import com.recu.bookmark.features.guideflag.service.UserGuideFlagService;
import com.recu.common.web.error.BusinessException;
import com.recu.common.web.error.CommonErrorCode;
import feign.FeignException;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BookmarkService {

  private static final ZoneOffset KST = ZoneOffset.of("+09:00");

  private final BookmarkRepository bookmarkRepository;
  private final ContentClient contentClient;
  private final UserGuideFlagService guideFlags;

  @Transactional
  public BookmarkResponseDTO addBookmark(Long userId, Long postId) {
    // 회원은 Gateway가 확인했고, 글은 content의 ⑦로 확인한다 (원본은 blog_post 표를 직접 읽었다)
    requirePostExists(postId);
    if (bookmarkRepository.existsBookmark(userId, postId)) {
      throw new BusinessException(BookmarkErrorCode.ALREADY_BOOKMARKED);
    }

    BookmarkEntity bookmark =
        BookmarkEntity.builder()
            .userId(userId)
            .blogPostId(postId)
            // DB(DATETIME)는 초까지만 저장해서, 응답도 같은 값이 되게 초 아래를 버린다
            .created_at(LocalDateTime.now().withNano(0))
            .build();

    BookmarkEntity saved = bookmarkRepository.save(bookmark);
    return BookmarkResponseDTO.builder()
        .bookmarkId(saved.getBookmark_id())
        .postId(saved.getBlogPostId())
        .createdAt(saved.getCreated_at().atOffset(KST))
        .build();
  }

  public Map<String, Object> getBookmark(Long userId) {
    // 최근 저장 순
    List<BookmarkEntity> bookmarkEntities = bookmarkRepository.findBookmarksByUser(userId);
    if (bookmarkEntities.isEmpty()) {
      return Map.of("bookmarks", List.of(), "count", 0);
    }

    // 카드 정보는 content의 ⑦로 한 번에 받는다. 원본은 blog_post·tech_tag 표를 직접 읽었다
    List<Long> postIds = bookmarkEntities.stream().map(BookmarkEntity::getBlogPostId).toList();
    Map<Long, PostSummaryResponseDTO> summaries =
        contentClient.getSummaries(new PostSummariesRequestDTO(postIds)).stream()
            .collect(Collectors.toMap(PostSummaryResponseDTO::postId, Function.identity()));
    // "가이드 있음"은 B·C 수신으로 채운 bookmark의 사본에서 읽는다 (원본은 ai_guide 표를 직접 읽었다)
    Set<Long> guided = guideFlags.guidedPostIds(userId, postIds);

    List<BookmarkResponseDTO> bookmarks =
        bookmarkEntities.stream()
            .map(
                bookmark ->
                    card(
                        bookmark,
                        summaries.get(bookmark.getBlogPostId()),
                        guided.contains(bookmark.getBlogPostId())))
            .toList();
    return Map.of("bookmarks", bookmarks, "count", bookmarks.size());
  }

  private BookmarkResponseDTO card(
      BookmarkEntity bookmark, PostSummaryResponseDTO post, boolean hasGuide) {
    return BookmarkResponseDTO.builder()
        .bookmarkId(bookmark.getBookmark_id())
        .postId(bookmark.getBlogPostId())
        .companyId(post.companyId())
        .title(post.title())
        .companyName(post.companyName())
        .categories(post.categories())
        .summary(post.summary())
        .skills(post.skills())
        .publishedAt(post.publishedAt())
        .savedAt(bookmark.getCreated_at().atOffset(KST))
        .hasGuide(hasGuide)
        .build();
  }

  // ⑦은 없는 글이 섞이면 404로 응답한다
  private void requirePostExists(Long postId) {
    try {
      contentClient.getSummaries(new PostSummariesRequestDTO(List.of(postId)));
    } catch (FeignException.NotFound exception) {
      throw new BusinessException(CommonErrorCode.NOT_FOUND, "postId");
    }
  }

  @Transactional
  public BookmarkResponseDTO deleteBookmark(Long userId, Long postId) {
    BookmarkEntity bookmark =
        bookmarkRepository
            .findBookmark(userId, postId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

    bookmarkRepository.delete(bookmark);

    return null;
  }
}
