package com.recu.content.features.company.repository;

import com.recu.content.database.entity.BlogPostEntity;
import com.recu.content.database.entity.BlogPostTagEntity;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface CompanyPostQueryRepository extends Repository<BlogPostEntity, Long> {

  @Query(
      """
      select p from BlogPostEntity p
      where p.company.company_id = :companyId
        and (p.is_tech = true or p.is_tech is null)
      order by p.published_at desc
      """)
  List<BlogPostEntity> findEligiblePosts(@Param("companyId") Long companyId);

  @Query(
      """
      select t from BlogPostTagEntity t join fetch t.techTag
      where t.id.blog_post_id in :postIds
      order by t.id.blog_post_id,
        case when t.tag_rank is null then 1 else 0 end,
        t.tag_rank,
        t.techTag.name
      """)
  List<BlogPostTagEntity> findTags(@Param("postIds") List<Long> postIds);

  @Query(
      """
      select s.blogPost.blog_post_id as postId, count(s) as sectionCount
      from BlogPostSectionEntity s
      where s.blogPost.blog_post_id in :postIds
      group by s.blogPost.blog_post_id
      """)
  List<SectionCount> findSectionCounts(@Param("postIds") List<Long> postIds);

  // TODO(①, ②, C): 원본에 있던 findJobFields·findWantSkillNames(user 표), findBookmarkedPostIds(bookmark
  // 표),
  //  findGuidedPostIds(ai_guide 표)는 다른 서비스 표라 지웠다. CompanyService의 대역 메서드 참고.

  interface SectionCount {
    Long getPostId();

    Long getSectionCount();
  }
}
