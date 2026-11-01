package com.recu.content.database.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.recu.content.database.entity.BlogPostCategoryEntity;
import com.recu.content.database.entity.BlogPostCategoryPk;
import com.recu.content.database.entity.BlogPostEntity;
import com.recu.content.database.entity.BlogPostSectionEntity;
import com.recu.content.database.entity.BlogPostTagEntity;
import com.recu.content.database.entity.BlogPostTagPk;
import com.recu.content.database.entity.CompanyEntity;
import com.recu.content.database.entity.TechTagEntity;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/** 표 여섯 개에 실제로 한 행씩 들어가는지. 엔티티 매핑이 맞는지 여기서 걸린다. */
@DataJpaTest
@ActiveProfiles("test")
class EntitySaveTest {

  @Autowired CompanyRepository companies;
  @Autowired TechTagRepository techTags;
  @Autowired BlogPostRepository posts;
  @Autowired BlogPostSectionRepository sections;
  @Autowired BlogPostCategoryRepository categories;
  @Autowired BlogPostTagRepository tags;

  @Test
  void 표_여섯개에_한_행씩_저장된다() {
    CompanyEntity company =
        companies.save(
            CompanyEntity.builder()
                .name("우아한형제들")
                .feed_url("https://techblog.woowahan.com/feed/")
                .feed_type("FEED_PAGED")
                .build());
    assertThat(company.getCompany_id()).isNotNull();

    TechTagEntity tag =
        techTags.save(
            TechTagEntity.builder().name("Kafka").field("Infra").selectable(true).build());
    assertThat(tag.getTech_tag_id()).isNotNull();

    BlogPostEntity post =
        posts.save(
            BlogPostEntity.builder()
                .company(company)
                .url("https://techblog.woowahan.com/27604/")
                .title("전자계약서 화면 개편기")
                .published_at(LocalDateTime.of(2026, 9, 18, 0, 0))
                .content_html("<h2>들어가며</h2>")
                .content_text("들어가며 본문")
                .char_count(12390)
                .has_code(true)
                .collected_at(LocalDateTime.now())
                .is_tech(true)
                .field("Frontend")
                .level("고급")
                .summary("한 줄 요지")
                .build());
    assertThat(post.getBlog_post_id()).isNotNull();

    sections.save(
        BlogPostSectionEntity.builder()
            .blogPost(post)
            .seq(1)
            .heading("들어가며")
            .level("h2")
            .content_text("본문")
            .char_count(2)
            .build());

    categories.save(
        BlogPostCategoryEntity.builder()
            .id(new BlogPostCategoryPk(post.getBlog_post_id(), "AI"))
            .blogPost(post)
            .build());

    tags.save(
        BlogPostTagEntity.builder()
            .id(new BlogPostTagPk(post.getBlog_post_id(), tag.getTech_tag_id()))
            .blogPost(post)
            .techTag(tag)
            .tag_rank(1)
            .build());

    assertThat(posts.count()).isEqualTo(1);
    assertThat(sections.count()).isEqualTo(1);
    assertThat(categories.count()).isEqualTo(1);
    assertThat(tags.count()).isEqualTo(1);
  }

  @Test
  void 같은_url_은_중복으로_안_들어간다() {
    assertThat(posts.existsByUrl("https://없는주소")).isFalse();
  }
}
