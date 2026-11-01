package com.recu.collector.features.collect.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recu.collector.features.collect.domain.BlogSource;
import com.recu.collector.features.collect.domain.SourceType;
import com.recu.collector.features.collect.domain.dto.CollectedPost;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** 실제 피드를 받아 값이 채워지는지 확인한다. 네트워크를 타므로 저장소에 올리기 전에 어떻게 할지 정한다. */
@Tag("network")
class RssReaderTest {

  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final RssReader reader = new RssReader(new XmlFetcher(), new HeadingFinder());

  private record Feed(String company, String url, int expectedAtLeast) {}

  @Test
  void 피드_네_곳을_읽는다() throws Exception {
    List<Feed> feeds =
        List.of(
            new Feed("올리브영", "https://oliveyoung.tech/rss.xml", 200),
            new Feed("인프랩", "https://tech.inflab.com/rss.xml", 40),
            new Feed("SK플래닛", "https://techtopic.skplanet.com/rss.xml", 55),
            new Feed("토스", "https://toss.tech/rss.xml", 15));

    for (Feed feed : feeds) {
      List<CollectedPost> posts =
          reader.read(new BlogSource(feed.company(), SourceType.FEED_FULL, feed.url()));
      print(feed.company(), posts);

      assertThat(posts).hasSizeGreaterThanOrEqualTo(feed.expectedAtLeast());
      assertThat(posts).allSatisfy(RssReaderTest::글하나가_제대로_채워졌다);
    }
  }

  /** 기업 요약 한 줄 다음에 글 세 편을 각각 보여 준다. 날짜 · 글자 수 · 코드 여부는 글마다 다르다. */
  private void print(String company, List<CollectedPost> posts) {
    String oldest =
        posts.stream()
            .map(CollectedPost::publishedAt)
            .min(Comparator.naturalOrder())
            .orElseThrow()
            .format(DAY);
    String newest =
        posts.stream()
            .map(CollectedPost::publishedAt)
            .max(Comparator.naturalOrder())
            .orElseThrow()
            .format(DAY);
    long withCode = posts.stream().filter(CollectedPost::hasCode).count();
    int avgChars = (int) posts.stream().mapToInt(CollectedPost::charCount).average().orElse(0);

    System.out.printf(
        "%n=== %s | %d편 | %s ~ %s | 평균 %,d자 | 코드 있는 글 %d편%n",
        company, posts.size(), oldest, newest, avgChars, withCode);

    posts.stream()
        .limit(3)
        .forEach(
            p ->
                System.out.printf(
                    "  %s | %,6d자 | 코드 %s | id=%s%n    %s%n",
                    p.publishedAt(),
                    p.charCount(),
                    p.hasCode() ? "O" : "X",
                    p.externalId(),
                    p.title()));
  }

  private static void 글하나가_제대로_채워졌다(CollectedPost post) {
    assertThat(post.title()).isNotBlank();
    assertThat(post.url()).startsWith("http");
    assertThat(post.externalId()).isNotBlank();
    assertThat(post.publishedAt()).isNotNull();
    assertThat(post.bodyHtml()).isNotBlank();
    assertThat(post.charCount()).isPositive();
    // 소제목은 이제 받는 단계에서 채운다. 없는 글도 있어 개수는 확인하지 않는다.
    assertThat(post.headings()).isNotNull();
  }
}
