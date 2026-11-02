package com.recu.content.database.repository;

import com.recu.content.database.entity.UserGuideFlagEntity;
import com.recu.content.database.entity.UserGuideFlagPk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserGuideFlagRepository
    extends JpaRepository<UserGuideFlagEntity, UserGuideFlagPk> {
  @Query(
      "select f.id.blogPostId from UserGuideFlagEntity f "
          + "where f.id.userId = :userId and f.id.blogPostId in :postIds")
  List<Long> findGuidedPostIds(@Param("userId") Long userId, @Param("postIds") List<Long> postIds);
}
