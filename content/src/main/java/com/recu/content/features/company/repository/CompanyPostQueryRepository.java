package com.recu.content.features.company.repository;

import com.recu.content.database.entity.BlogPostEntity;
import com.recu.content.database.entity.BlogPostTagEntity;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface CompanyPostQueryRepository extends Repository<BlogPostEntity, Long> {

  // 기업 글 목록 · 상세가 쓰는 칸만 읽는다. 글 본문(content_html · content_text)은 읽지 않는다 (#117)
  @Query(
      """
      select p.blog_post_id as postId, p.title as title, p.published_at as publishedAt,
        p.field as field, p.level as level, p.summary as summary,
        p.char_count as charCount, p.url as url
      from BlogPostEntity p
      where p.company.company_id = :companyId
        and (p.is_tech = true or p.is_tech is null)
      order by p.published_at desc
      """)
  List<EligiblePost> findEligiblePosts(@Param("companyId") Long companyId);

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

  // 기업 목록 (#117): 글 본문을 읽지 않고 DB에서 센다. 기술 글 = is_tech가 true 또는 null

  @Query(
      """
      select p.company.company_id as companyId, count(p) as postCount
      from BlogPostEntity p
      where p.is_tech = true or p.is_tech is null
      group by p.company.company_id
      """)
  List<CompanyPostCount> countTechPostsByCompany();

  // 관심 기술이 하나라도 붙은 기술 글 수. 한 글에 관심 기술이 여러 개여도 1편
  @Query(
      """
      select p.company.company_id as companyId, count(distinct p.blog_post_id) as postCount
      from BlogPostTagEntity t join t.blogPost p
      where (p.is_tech = true or p.is_tech is null) and t.techTag.name in :skillNames
      group by p.company.company_id
      """)
  List<CompanyPostCount> countMatchedTechPostsByCompany(
      @Param("skillNames") Collection<String> skillNames);

  // 관심 기술별 기술 글 수
  @Query(
      """
      select p.company.company_id as companyId, t.techTag.name as skillName,
        count(distinct p.blog_post_id) as postCount
      from BlogPostTagEntity t join t.blogPost p
      where (p.is_tech = true or p.is_tech is null) and t.techTag.name in :skillNames
      group by p.company.company_id, t.techTag.name
      """)
  List<CompanySkillPostCount> countMatchedTechPostsBySkill(
      @Param("skillNames") Collection<String> skillNames);

  interface EligiblePost {
    Long getPostId();

    String getTitle();

    LocalDateTime getPublishedAt();

    String getField();

    String getLevel();

    String getSummary();

    Integer getCharCount();

    String getUrl();
  }

  interface SectionCount {
    Long getPostId();

    Long getSectionCount();
  }

  interface CompanyPostCount {
    Long getCompanyId();

    Long getPostCount();
  }

  interface CompanySkillPostCount {
    Long getCompanyId();

    String getSkillName();

    Long getPostCount();
  }
}
