package com.recu.content.features.ingest.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recu.content.database.repository.BlogPostCategoryRepository;
import com.recu.content.database.repository.BlogPostRepository;
import com.recu.content.database.repository.BlogPostSectionRepository;
import com.recu.content.database.repository.BlogPostTagRepository;
import com.recu.content.database.repository.CompanyRepository;
import com.recu.content.database.repository.TechTagRepository;
import com.recu.content.features.ingest.domain.dto.ImportedPost;
import com.recu.content.features.ingest.domain.dto.TechDictionary;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** 글을 표에 넣는 전 과정. 표본 두 편으로 여섯 표가 모두 채워지는지 본다. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PostIngestServiceTest {

  @Autowired PostIngestService service;
  @Autowired CompanyRepository companies;
  @Autowired TechTagRepository techTags;
  @Autowired BlogPostRepository posts;
  @Autowired BlogPostSectionRepository sections;
  @Autowired BlogPostCategoryRepository categories;
  @Autowired BlogPostTagRepository tags;

  /** 수집 단계가 만든 사전과 같은 모양. K8s 를 Kubernetes 로 묶은 것까지 재현한다. */
  private TechDictionary dictionary() {
    return new TechDictionary(
        Map.of("Kubernetes", 13, "Redis", 12, "Spring", 5, "AI", 35),
        Map.of("Kubernetes", 13, "Redis", 12, "Spring", 5),
        Map.of("K8s", "Kubernetes"),
        Map.of("kubernete", "Kubernetes", "redi", "Redis"),
        List.of("AI"));
  }

  private ImportedPost post(String url, String company, List<String> techs) {
    return new ImportedPost(
        company,
        "ext-" + url,
        "제목 " + url,
        url,
        "2026-09-18",
        120,
        true,
        List.of("AI", "Backend", "AI"),
        List.of(
            new ImportedPost.Heading(1, "h2", "들어가며"), new ImportedPost.Heading(2, "h2", "문제 상황")),
        "<h2>들어가며</h2>",
        "들어가며 첫 문단입니다. 문제 상황 두 번째 문단입니다.",
        new ImportedPost.Ai(true, techs, "Backend", "중급", "한 줄 요지"));
  }

  @Test
  @DisplayName("표 여섯 개가 한 번에 채워진다")
  void 표본_두편이_모두_들어간다() {
    PostIngestService.IngestResult result =
        service.ingest(
            List.of(
                post("https://a.com/1", "우아한형제들", List.of("K8s", "Redis")),
                post("https://a.com/2", "토스", List.of("AI", "Spring"))),
            dictionary());

    assertThat(result.companyCount()).isEqualTo(8);
    assertThat(result.newPosts()).isEqualTo(2);
    assertThat(result.skipped()).isZero();

    assertThat(companies.count()).isEqualTo(8);
    assertThat(posts.count()).isEqualTo(2);
    // 글마다 구간 2개
    assertThat(sections.count()).isEqualTo(4);
    // 분류어는 같은 이름을 한 번만 (AI·Backend·AI → 2개)
    assertThat(categories.count()).isEqualTo(4);
  }

  @Test
  @DisplayName("K8s 는 Kubernetes 로 바뀌어 붙고 포괄어 AI 도 표에는 들어간다")
  void 기술_이름이_사전_이름으로_바뀐다() {
    service.ingest(
        List.of(post("https://a.com/1", "우아한형제들", List.of("K8s", "Redis"))), dictionary());

    assertThat(techTags.findByName("Kubernetes")).isPresent();
    assertThat(techTags.findByName("K8s")).isEmpty();

    // 포괄어도 표에는 있으나 화면에서 고를 수 없다
    assertThat(techTags.findByName("AI")).isPresent();
    assertThat(techTags.findByName("AI").orElseThrow().getSelectable()).isFalse();

    // 20종은 고를 수 있고 계열이 붙어 있다
    assertThat(techTags.findByName("Kubernetes").orElseThrow().getSelectable()).isTrue();
    assertThat(techTags.findByName("Kubernetes").orElseThrow().getField()).isEqualTo("Infra");

    assertThat(tags.count()).isEqualTo(2);
  }

  @Test
  @DisplayName("같은 글을 두 번 넣어도 행이 늘지 않는다")
  void 다시_넣어도_안_늘어난다() {
    List<ImportedPost> rows = List.of(post("https://a.com/1", "토스", List.of("Redis")));

    service.ingest(rows, dictionary());
    long after1 = posts.count();

    PostIngestService.IngestResult second = service.ingest(rows, dictionary());

    assertThat(after1).isEqualTo(1);
    assertThat(posts.count()).isEqualTo(1);
    assertThat(second.newPosts()).isZero();
    assertThat(second.skipped()).isEqualTo(1);
  }

  @Test
  @DisplayName("모르는 기업의 글은 건너뛴다")
  void 모르는_기업은_건너뛴다() {
    PostIngestService.IngestResult result =
        service.ingest(List.of(post("https://a.com/9", "없는회사", List.of("Redis"))), dictionary());

    assertThat(result.newPosts()).isZero();
    assertThat(result.skipped()).isEqualTo(1);
    assertThat(posts.count()).isZero();
  }
}
