package com.recu.bookmark.features.guideflag.repository;

import com.recu.bookmark.database.entity.UserGuideFlagEntity;
import com.recu.bookmark.database.entity.UserGuideFlagPk;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserGuideFlagRepository
    extends JpaRepository<UserGuideFlagEntity, UserGuideFlagPk> {

  // 같은 C를 두 번 받아도 한 행만 남게 복합 PK로 처리한다 (content와 같은 방식)
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO user_guide_flag (user_id, blog_post_id)
          VALUES (:userId, :postId)
          ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)
          """,
      nativeQuery = true)
  void insertIfAbsent(@Param("userId") Long userId, @Param("postId") Long postId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from UserGuideFlagEntity f where f.id.userId = :userId")
  int deleteAllByUserId(@Param("userId") Long userId);

  @Query(
      "select f.id.blogPostId from UserGuideFlagEntity f "
          + "where f.id.userId = :userId and f.id.blogPostId in :postIds")
  List<Long> findGuidedPostIds(
      @Param("userId") Long userId, @Param("postIds") Collection<Long> postIds);
}
