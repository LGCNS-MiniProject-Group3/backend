package com.recu.bookmark.features.bookmark.service;

import com.recu.bookmark.database.entity.BookmarkEntity;
import com.recu.bookmark.error.BookmarkErrorCode;
import com.recu.bookmark.features.bookmark.domain.dto.BookmarkResponseDTO;
import com.recu.bookmark.features.bookmark.repository.BookmarkRepository;
import com.recu.common.web.error.BusinessException;
import com.recu.common.web.error.CommonErrorCode;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BookmarkService {

  private final BookmarkRepository bookmarkRepository;

  @Transactional
  public BookmarkResponseDTO addBookmark(Long userId, Long postId) {

    // TODO(⑦): 원본은 user·blog_post 표를 직접 읽어 회원·글이 있는지 확인했다(없으면 404).
    //  회원은 Gateway가 확인했고, 글은 content의 ⑦(POST /internal/posts/summaries)로 확인한다.
    //  원본: userRepository.findById(userId), blogPostRepository.findById(postId) → NOT_FOUND
    if (bookmarkRepository.existsBookmark(userId, postId)) {
      throw new BusinessException(BookmarkErrorCode.ALREADY_BOOKMARKED);
    }

    BookmarkEntity bookmark =
        BookmarkEntity.builder()
            .userId(userId)
            .blogPostId(postId)
            .created_at(LocalDateTime.now())
            .build();

    BookmarkEntity saved = bookmarkRepository.save(bookmark);
    return BookmarkResponseDTO.builder()
        .bookmarkId(saved.getBookmark_id())
        .postId(saved.getBlogPostId())
        .createdAt(saved.getCreated_at())
        .build();
  }

  public Map<String, Object> getBookmark(Long userId) {

    List<BookmarkEntity> bookmarkEntities = bookmarkRepository.findBookmarksByUser(userId);

    // 북마크 ai가이드 존재여부 판단해서 has값 수정하는 코드

    List<Long> postIds = bookmarkEntities.stream().map(BookmarkEntity::getBlogPostId).toList();

    // TODO(⑦, C): 카드 정보(제목·기업·계열·요지·기술·발행일)는 content의 ⑦로, "가이드 있음"은 bookmark의
    //  user_guide_flag 사본(메시지 C·B 수신)으로 채운다. 원본은 blog_post·user·ai_guide 표를 직접 읽었다.
    //  원본 코드는 아래 주석. BookmarkResponseDTO 필드와 순서는 그대로 맞춘다.
    if (!postIds.isEmpty()) {
      throw new UnsupportedOperationException("TODO(⑦, C): 북마크 목록 카드 정보 조회 미구현");
    }
    List<BookmarkResponseDTO> bookmarks = List.of();
    /* 원본
    // List<Long> postIds =
    //     bookmarkEntities.stream()
    //         .map(bookmark -> bookmark.getBlogPost().getBlog_post_id())
    //         .toList();
    // // 최신 사용자의 프로필 버전 가져오기
    // Integer profileVersion = userRepository.findById(userId).orElseThrow().getProfile_version();
    // // 현재 프로필 버전으로 만들어진 ai가이드 있는 게시글 ID 조회
    // List<Long> guidedPostIds =
    //     postIds.isEmpty()
    //         ? List.of()
    //         : companyPostQueryRepository.findGuidedPostIds(userId, profileVersion, postIds);

    // // 글별 기술 칩. 04 글 목록(CompanyService)과 같은 조회라 순서도 같다
    // Map<Long, List<String>> skillsByPost =
    //     postIds.isEmpty()
    //         ? Map.of()
    //         : companyPostQueryRepository.findTags(postIds).stream()
    //             .collect(
    //                 Collectors.groupingBy(
    //                     tag -> tag.getId().getBlog_post_id(),
    //                     LinkedHashMap::new,
    //                     Collectors.mapping(
    //                         tag -> tag.getTechTag().getName(), Collectors.toList())));

    // List<BookmarkResponseDTO> bookmarks =
    //     bookmarkEntities.stream()
    //         .map(
    //             bookmark -> {
    //               BlogPostEntity post = bookmark.getBlogPost();

    //               Long postId = post.getBlog_post_id();

    //               List<String> categories =
    //                   post.getField() == null || post.getField().isBlank()
    //                       ? List.of()
    //                       : List.of(post.getField());

    //               OffsetDateTime savedAt =
    //                   bookmark.getCreated_at().atOffset(ZoneOffset.of("+09:00"));

    //               return BookmarkResponseDTO.builder()
    //                   .bookmarkId(bookmark.getBookmark_id())
    //                   .postId(postId)
    //                   .companyId(post.getCompany().getCompany_id())
    //                   .title(post.getTitle())
    //                   .companyName(post.getCompany().getName())
    //                   .categories(categories)
    //                   .summary(post.getSummary())
    //                   .skills(skillsByPost.getOrDefault(postId, List.of()))
    //                   .publishedAt(post.getPublished_at().toLocalDate())
    //                   .savedAt(savedAt)
    //                   .hasGuide(guidedPostIds.contains(postId))
    //                   .build();
    //             })
    //         .toList();
    */

    System.out.println("DTO 변환 완료");

    return Map.of("bookmarks", bookmarks, "count", bookmarks.size());
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
