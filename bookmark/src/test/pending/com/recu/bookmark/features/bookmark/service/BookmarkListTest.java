// 빌드에서 뺀 원본 테스트 (분리 이식 #7). content의 글·기술 표와 user 표를 직접 만들어 쓰는 테스트라 분리 후 컴파일되지 않는다. TODO(⑦, C): ⑦
// 클라이언트를 mock해서 다시 쓴다.
// 담당자가 고친 뒤 src/test/java 아래 같은 경로로 되돌린다.
package com.recu.bookmark.features.bookmark.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recu.bookmark.features.bookmark.domain.dto.BookmarkResponseDTO;
import com.recu.content.database.entity.BlogPostEntity;
import com.recu.content.database.entity.BlogPostTagEntity;
import com.recu.content.database.entity.BlogPostTagPk;
import com.recu.content.database.entity.CompanyEntity;
import com.recu.content.database.entity.TechTagEntity;
import com.recu.content.database.repository.BlogPostRepository;
import com.recu.content.database.repository.BlogPostTagRepository;
import com.recu.content.database.repository.CompanyRepository;
import com.recu.content.database.repository.TechTagRepository;
import com.recu.user.database.entity.UserEntity;
import com.recu.user.database.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** 북마크 목록이 04 글 카드와 같은 요지 · 기술 칩을 주는지. 06 카드가 04 카드를 그대로 쓴다. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BookmarkListTest {

  @Autowired BookmarkService bookmarkService;
  @Autowired UserRepository users;
  @Autowired CompanyRepository companies;
  @Autowired TechTagRepository techTags;
  @Autowired BlogPostRepository posts;
  @Autowired BlogPostTagRepository tags;

  @Test
  @SuppressWarnings("unchecked")
  void 북마크_목록은_글_요지와_기술_칩을_태그_순서대로_준다() {
    UserEntity user =
        users.save(
            UserEntity.builder()
                .google_sub("bookmark-test")
                .name("테스트")
                .created_at(LocalDate.now())
                .updated_at(LocalDate.now())
                .build());
    CompanyEntity company =
        companies.save(
            CompanyEntity.builder()
                .name("북마크 시험 기업")
                .feed_url("https://example.com/rss.xml")
                .feed_type("FEED_LIST")
                .build());
    BlogPostEntity post =
        posts.save(
            BlogPostEntity.builder()
                .company(company)
                .url("https://example.com/post/1")
                .title("시험 글")
                .published_at(LocalDateTime.of(2026, 9, 18, 0, 0))
                .content_html("<h2>들어가며</h2>")
                .content_text("들어가며 본문")
                .char_count(10)
                .has_code(false)
                .collected_at(LocalDateTime.now())
                .is_tech(true)
                .field("Backend")
                .level("중급")
                .summary("한 줄 요지")
                .build());
    TechTagEntity redis = techTags.save(TechTagEntity.builder().name("Redis").build());
    TechTagEntity kafka = techTags.save(TechTagEntity.builder().name("Kafka").build());
    tags.save(tag(post, kafka, 1));
    tags.save(tag(post, redis, 2));

    bookmarkService.addBookmark(user.getUser_id(), post.getBlog_post_id());
    Map<String, Object> result = bookmarkService.getBookmark(user.getUser_id());

    List<BookmarkResponseDTO> bookmarks = (List<BookmarkResponseDTO>) result.get("bookmarks");
    assertThat(bookmarks).hasSize(1);
    assertThat(bookmarks.get(0).getSummary()).isEqualTo("한 줄 요지");
    assertThat(bookmarks.get(0).getSkills()).containsExactly("Kafka", "Redis");
  }

  private BlogPostTagEntity tag(BlogPostEntity post, TechTagEntity techTag, int rank) {
    return BlogPostTagEntity.builder()
        .id(new BlogPostTagPk(post.getBlog_post_id(), techTag.getTech_tag_id()))
        .blogPost(post)
        .techTag(techTag)
        .tag_rank(rank)
        .build();
  }
}
