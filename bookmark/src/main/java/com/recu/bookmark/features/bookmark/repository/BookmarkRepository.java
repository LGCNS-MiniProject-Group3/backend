package com.recu.bookmark.features.bookmark.repository;

import com.recu.bookmark.database.entity.BookmarkEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookmarkRepository extends JpaRepository<BookmarkEntity, Long> {

  @Query(
      """
        SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END
        FROM BookmarkEntity b
        WHERE b.userId = :userId
          AND b.blogPostId = :postId
    """)
  boolean existsBookmark(@Param("userId") Long userId, @Param("postId") Long postId);

  @Query(
      """
        SELECT b
        FROM BookmarkEntity b
        WHERE b.userId = :userId
        ORDER BY b.created_at DESC
    """)
  List<BookmarkEntity> findBookmarksByUser(@Param("userId") Long userId);

  // ② lookup: 글 번호 중 이 회원이 저장한 것만. 원본 findBookmarkedPostIds와 같은 조회
  @Query(
      """
        SELECT b.blogPostId
        FROM BookmarkEntity b
        WHERE b.userId = :userId
          AND b.blogPostId IN :postIds
    """)
  List<Long> findBookmarkedPostIds(
      @Param("userId") Long userId, @Param("postIds") Collection<Long> postIds);

  // 자우는 코드
  @Query(
      """
    SELECT b
    FROM BookmarkEntity b
    WHERE b.userId = :userId
      AND b.blogPostId = :postId
        """)
  Optional<BookmarkEntity> findBookmark(@Param("userId") Long userId, @Param("postId") Long postId);
}
