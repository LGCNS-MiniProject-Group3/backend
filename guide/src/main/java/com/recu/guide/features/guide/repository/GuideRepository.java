package com.recu.guide.features.guide.repository;

import com.recu.guide.database.entity.AiGuideEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuideRepository extends JpaRepository<AiGuideEntity, Long> {

  @Query(
      "select guide from AiGuideEntity guide "
          + "where guide.userId = :userId "
          + "and guide.blogPostId = :postId "
          + "and guide.profile_version = :profileVersion "
          + "and guide.prompt_version = :promptVersion")
  Optional<AiGuideEntity> findCached(
      @Param("userId") Long userId,
      @Param("postId") Long postId,
      @Param("profileVersion") Integer profileVersion,
      @Param("promptVersion") String promptVersion);
}
