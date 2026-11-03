package com.recu.content.features.ingest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recu.common.kafka.event.PostCollectedEvent;
import com.recu.common.kafka.event.PostCollectedEvent.Section;
import com.recu.common.kafka.event.PostCollectedEvent.Tag;
import com.recu.common.web.error.BusinessException;
import com.recu.content.database.entity.CompanyEntity;
import com.recu.content.database.entity.TechTagEntity;
import com.recu.content.database.repository.BlogPostCategoryRepository;
import com.recu.content.database.repository.BlogPostRepository;
import com.recu.content.database.repository.BlogPostSectionRepository;
import com.recu.content.database.repository.BlogPostTagRepository;
import com.recu.content.database.repository.CompanyRepository;
import com.recu.content.database.repository.TechTagRepository;
import com.recu.content.features.post.repository.PostRepository;
import com.recu.content.features.post.repository.PostSectionRepository;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostCollectedIngestService.class)
@ActiveProfiles("test")
// 테스트 자체의 자동 롤백 대신 서비스 트랜잭션의 실제 커밋·롤백을 검증한다.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PostCollectedIngestServiceTest {
  @Autowired PostCollectedIngestService service;
  @Autowired CompanyRepository companies;
  @Autowired BlogPostRepository posts;
  @Autowired BlogPostSectionRepository sections;
  @Autowired BlogPostCategoryRepository categories;
  @Autowired BlogPostTagRepository postTags;
  @Autowired TechTagRepository techTags;
  @Autowired JdbcTemplate jdbc;
  @Autowired PostRepository postQueries;
  @Autowired PostSectionRepository sectionQueries;

  private Long companyId;

  @BeforeEach
  void 준비() {
    정리();
    companyId =
        companies
            .save(
                CompanyEntity.builder()
                    .name("테스트 기업")
                    .feed_url("https://example.com/feed")
                    .feed_type("FEED_FULL")
                    .build())
            .getCompany_id();
  }

  @AfterEach
  void 정리() {
    // 데이터 삭제가 있으므로 이 테스트의 외부 실행은 반드시 전용 테스트 DB를 사용한다.
    sections.deleteAllInBatch();
    categories.deleteAllInBatch();
    postTags.deleteAllInBatch();
    posts.deleteAllInBatch();
    techTags.deleteAllInBatch();
    companies.deleteAllInBatch();
  }

  @Test
  void 본문과_분류를_보존하고_기존_기술을_재사용하며_새_기술을_추가한다() {
    var existing =
        techTags.save(TechTagEntity.builder().name("Java").aliases("자바").selectable(true).build());
    var event = event(companyId, "https://example.com/post", defaultSections());

    assertThat(service.ingest(event)).isTrue();

    var post = posts.findByUrl(event.url()).orElseThrow();
    assertThat(post.getExternal_id()).isEqualTo(event.externalId());
    assertThat(post.getTitle()).isEqualTo(event.title());
    assertThat(post.getContent_html()).isEqualTo(event.contentHtml());
    assertThat(post.getContent_text()).isEqualTo(event.contentText());
    assertThat(post.getChar_count()).isEqualTo(event.contentText().length());
    assertThat(post.getHas_code()).isTrue();
    assertThat(post.getIs_tech()).isTrue();
    assertThat(post.getField()).isEqualTo("Backend");
    assertThat(post.getLevel()).isEqualTo("중급");
    assertThat(post.getSummary()).isEqualTo("요약");
    assertThat(post.getPublished_at()).isEqualTo(LocalDateTime.of(2026, 11, 3, 1, 30));
    assertThat(post.getCollected_at()).isNotNull();
    assertThat(jdbc.queryForObject("select company_id from blog_post", Long.class))
        .isEqualTo(companyId);
    assertThat(jdbc.queryForList("select name from blog_post_category", String.class))
        .containsExactlyInAnyOrder("Backend", "Engineering");
    assertThat(techTags.count()).isEqualTo(2);
    var reused = techTags.findByName("Java").orElseThrow();
    assertThat(reused.getTech_tag_id()).isEqualTo(existing.getTech_tag_id());
    assertThat(reused.getAliases()).isEqualTo("자바");
    assertThat(reused.getSelectable()).isTrue();
    var added = techTags.findByName("NewTech").orElseThrow();
    assertThat(added.getSelectable()).isFalse();
    assertThat(added.getPost_count()).isZero();
    assertThat(added.getAliases()).isNull();
    assertThat(added.getField()).isNull();
    assertThat(
            jdbc.queryForList(
                "select tag_rank from blog_post_tag order by tag_rank", Integer.class))
        .containsExactly(3, 7);
    assertThat(postQueries.findSkillNamesByPostId(post.getBlog_post_id()))
        .containsExactly("NewTech", "Java");
  }

  @Test
  void 구간을_재분할하거나_순번을_다시_매기지_않는다() {
    var event = event(companyId, "https://example.com/sections", defaultSections());
    service.ingest(event);

    var stored = posts.findByUrl(event.url()).orElseThrow();
    assertThat(sectionQueries.findByPostId(stored.getBlog_post_id()))
        .extracting(s -> new Section(s.getSeq(), s.getHeading(), s.getLevel(), s.getContent_text()))
        .containsExactlyInAnyOrderElementsOf(event.sections());
    assertThat(sections.findAll())
        .allSatisfy(s -> assertThat(s.getChar_count()).isEqualTo(s.getContent_text().length()));
  }

  @Test
  void 동일_URL을_다시_처리해도_기존_데이터를_수정하거나_추가하지_않는다() {
    var event = event(companyId, "https://example.com/repeat", defaultSections());
    service.ingest(event);
    var before = counts();

    // 중복이면 기업 조회·잘못된 구간 처리에 들어가지 않고 건너뛴다.
    assertThat(service.ingest(event(-1L, event.url(), List.of(new Section(1, null, null, "x")))))
        .isFalse();
    assertThat(counts()).isEqualTo(before);
    assertThat(posts.findByUrl(event.url()).orElseThrow().getContent_text())
        .isEqualTo(event.contentText());
  }

  @Test
  void 없는_기업이면_기업을_만들지_않고_부분_저장도_하지_않는다() {
    var before = counts();
    assertThatThrownBy(
            () -> service.ingest(event(-1L, "https://example.com/missing", defaultSections())))
        .isInstanceOf(BusinessException.class);
    assertThat(counts()).isEqualTo(before);
  }

  @Test
  void 저장_중_구간_제약조건이_실패하면_새_기술까지_모두_롤백한다() {
    techTags.save(TechTagEntity.builder().name("Java").selectable(true).build());
    var before = counts();
    var invalid = List.of(new Section(1, "첫 구간", "h2", "본문"), new Section(1, "중복 순번", "h3", "실패"));

    assertThatThrownBy(() -> service.ingest(event(companyId, "https://example.com/fail", invalid)))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

    assertThat(counts()).isEqualTo(before);
    assertThat(techTags.findByName("NewTech")).isEmpty();
    assertThat(techTags.findByName("Java")).isPresent();
  }

  @Test
  void 선택_목록이_비어_있어도_글은_저장한다() {
    var e = event(companyId, "https://example.com/empty", List.of());
    var empty =
        new PostCollectedEvent(
            e.companyId(),
            e.url(),
            null,
            e.title(),
            e.publishedAt(),
            e.contentHtml(),
            e.contentText(),
            false,
            null,
            null,
            null,
            null,
            List.of(),
            List.of(),
            List.of());

    assertThat(service.ingest(empty)).isTrue();
    assertThat(counts()).containsExactly(1L, 1L, 0L, 0L, 0L, 0L);
  }

  @ParameterizedTest
  @NullAndEmptySource
  void 분류어_목록이_null이거나_비어도_글과_관련_데이터를_저장한다(List<String> values) {
    var event = withCategoriesAndSummary(values, "요약");

    assertThat(service.ingest(event)).isTrue();

    assertThat(posts.findByUrl(event.url())).isPresent();
    assertThat(categories.count()).isZero();
    assertThat(sections.count()).isEqualTo(2);
    assertThat(postTags.count()).isEqualTo(2);
  }

  @Test
  void 분류어의_빈_항목을_제외하고_공백_정리_후_중복을_제거한다() {
    service.ingest(
        withCategoriesAndSummary(
            Arrays.asList(null, "", " \t\n ", " Backend ", "Backend", "Engineering"), "요약"));

    assertThat(jdbc.queryForList("select name from blog_post_category", String.class))
        .containsExactlyInAnyOrder("Backend", "Engineering");
  }

  @ParameterizedTest
  @CsvSource({"99,99", "100,100", "101,100", "256,100"})
  void 분류어는_공백_정리_후_원본의_100자_제한으로_저장한다(int length, int expectedLength) {
    service.ingest(withCategoriesAndSummary(List.of(" \t" + "분".repeat(length) + " \n"), "요약"));

    assertThat(jdbc.queryForList("select name from blog_post_category", String.class))
        .containsExactly("분".repeat(expectedLength));
  }

  @Test
  void 서로_다른_분류어도_100자_절단_후_같아지면_한_건만_저장한다() {
    String prefix = "분".repeat(100);
    service.ingest(withCategoriesAndSummary(List.of(" " + prefix + "첫번째 ", prefix + "두번째"), "요약"));

    assertThat(jdbc.queryForList("select name from blog_post_category", String.class))
        .containsExactly(prefix);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " \t\n "})
  void 요약의_null은_유지하고_빈_값은_원본처럼_빈_문자열로_저장한다(String summary) {
    var event = withCategoriesAndSummary(List.of(), summary);
    service.ingest(event);

    assertThat(posts.findByUrl(event.url()).orElseThrow().getSummary())
        .isEqualTo(summary == null ? null : "");
  }

  @Test
  void 짧은_요약도_앞뒤_공백을_제거해_저장한다() {
    var event = withCategoriesAndSummary(List.of(), " \t요약 문장입니다. \n");
    service.ingest(event);

    assertThat(posts.findByUrl(event.url()).orElseThrow().getSummary()).isEqualTo("요약 문장입니다.");
  }

  @ParameterizedTest
  @CsvSource({"299,299", "300,300", "301,300", "600,300"})
  void 요약은_공백_정리_후_원본의_300자_제한으로_저장한다(int length, int expectedLength) {
    var event = withCategoriesAndSummary(List.of(), " \t" + "요".repeat(length) + " \n");
    service.ingest(event);

    assertThat(posts.findByUrl(event.url()).orElseThrow().getSummary())
        .isEqualTo("요".repeat(expectedLength));
  }

  private PostCollectedEvent withCategoriesAndSummary(List<String> values, String summary) {
    var e = event(companyId, "https://example.com/normalization", defaultSections());
    return new PostCollectedEvent(
        e.companyId(),
        e.url(),
        e.externalId(),
        e.title(),
        e.publishedAt(),
        e.contentHtml(),
        e.contentText(),
        e.hasCode(),
        e.isTech(),
        e.field(),
        e.level(),
        summary,
        values,
        e.tags(),
        e.sections());
  }

  private List<Long> counts() {
    return List.of(
        companies.count(),
        posts.count(),
        sections.count(),
        categories.count(),
        postTags.count(),
        techTags.count());
  }

  private List<Section> defaultSections() {
    return List.of(
        new Section(9, "두 번째", "h3", "  둘째 본문\n"), new Section(2, "첫 번째", "h2", "첫 본문\n줄바꿈"));
  }

  private PostCollectedEvent event(Long company, String url, List<Section> parts) {
    return new PostCollectedEvent(
        company,
        url,
        "external-68",
        " 원문 제목 ",
        OffsetDateTime.parse("2026-11-02T16:30:00Z"),
        "<h2>첫 번째</h2>\n<p> 원문 본문 </p>",
        " 원문 본문\n그대로 ",
        true,
        true,
        "Backend",
        "중급",
        "요약",
        List.of("Backend", "Engineering", "Backend"),
        List.of(new Tag("Java", 7), new Tag("NewTech", 3)),
        parts);
  }
}
