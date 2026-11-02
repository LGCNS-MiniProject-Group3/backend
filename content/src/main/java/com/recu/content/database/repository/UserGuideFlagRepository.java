package com.recu.content.database.repository;

import com.recu.content.database.entity.UserGuideFlagEntity;
import com.recu.content.database.entity.UserGuideFlagPk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserGuideFlagRepository
    extends JpaRepository<UserGuideFlagEntity, UserGuideFlagPk> {
  // 먼저 존재 여부를 조회하면 동시 수신 사이에 경쟁이 생기므로 복합 PK로 중복을 처리한다.
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
  List<Long> findGuidedPostIds(@Param("userId") Long userId, @Param("postIds") List<Long> postIds);
}
