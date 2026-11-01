package com.recu.collector.features.collect.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recu.collector.features.collect.domain.BlogSource;
import com.recu.collector.features.collect.domain.SourceType;
import com.recu.collector.features.collect.domain.dto.CollectedPost;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** 여덟 곳을 한 흐름으로 돌린다. 실제 요청을 보내므로 몇 분 걸린다. */
@Tag("network")
class CollectServiceTest {

  private static final List<BlogSource> SOURCES =
      List.of(
          new BlogSource("올리브영", SourceType.FEED_FULL, "https://oliveyoung.tech/rss.xml"),
          new BlogSource("인프랩", SourceType.FEED_FULL, "https://tech.inflab.com/rss.xml"),
          new BlogSource("SK플래닛", SourceType.FEED_FULL, "https://techtopic.skplanet.com/rss.xml"),
          new BlogSource("토스", SourceType.FEED_FULL, "https://toss.tech/rss.xml"),
          new BlogSource("우아한형제들", SourceType.FEED_PAGED, "https://techblog.woowahan.com/feed/"),
          new BlogSource("컬리", SourceType.FEED_LIST, "https://helloworld.kurly.com/rss.xml"),
          new BlogSource("네이버 D2", SourceType.JSON_API, "https://d2.naver.com/api/v1/contents"),
          new BlogSource("LY", SourceType.SITEMAP, "https://techblog.lycorp.co.jp/sitemap-0.xml"));

  private final CollectService service = service();

  @Test
  void 여덟_곳에서_글을_받는다() {
    List<CollectedPost> posts = service.collect(SOURCES);

    Map<String, List<CollectedPost>> byCompany =
        posts.stream().collect(Collectors.groupingBy(CollectedPost::company));

    System.out.printf("%n%-8s %5s %10s %8s %8s %8s%n", "기업", "편수", "평균 글자", "소제목", "코드", "분류어");
    for (BlogSource source : SOURCES) {
      List<CollectedPost> mine = byCompany.getOrDefault(source.company(), List.of());
      System.out.printf(
          "%-8s %5d %10s %8s %8d %8d%n",
          source.company(),
          mine.size(),
          mine.isEmpty()
              ? "-"
              : String.format(
                  "%,d",
                  (int) mine.stream().mapToInt(CollectedPost::charCount).average().orElse(0)),
          mine.isEmpty()
              ? "-"
              : String.format(
                  "%.1f", mine.stream().mapToInt(p -> p.headings().size()).average().orElse(0)),
          mine.stream().filter(CollectedPost::hasCode).count(),
          mine.stream().filter(p -> !p.sourceCategories().isEmpty()).count());
    }

    assertThat(byCompany.keySet()).hasSize(SOURCES.size());
    assertThat(posts).allSatisfy(CollectServiceTest::글하나가_제대로_채워졌다);
  }

  private static void 글하나가_제대로_채워졌다(CollectedPost post) {
    assertThat(post.company()).isNotBlank();
    assertThat(post.externalId()).isNotBlank();
    assertThat(post.title()).isNotBlank();
    assertThat(post.url()).startsWith("http");
    assertThat(post.publishedAt()).isNotNull();
    assertThat(post.bodyHtml()).isNotBlank();
    assertThat(post.charCount()).isPositive();
    assertThat(post.headings()).isNotNull();
    assertThat(post.sourceCategories()).isNotNull();
  }

  private static CollectService service() {
    XmlFetcher xml = new XmlFetcher();
    HeadingFinder headings = new HeadingFinder();
    ArticlePageReader pageReader =
        new ArticlePageReader(new PageFetcher(), new ArticleExtractor(), headings);
    return new CollectService(
        List.of(
            new RssReader(xml, headings),
            new FeedListReader(xml, pageReader),
            new SitemapReader(xml, pageReader),
            new NaverD2Reader(new NaverD2Reader.JsonFetcher(), headings)));
  }
}
