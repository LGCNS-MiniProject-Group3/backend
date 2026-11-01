package com.recu.collector.features.collect.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recu.collector.features.collect.domain.BlogSource;
import com.recu.collector.features.collect.domain.SourceType;
import com.recu.collector.features.collect.domain.dto.CollectedPost;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.Test;

/**
 * 받아 둔 파일로 도는 테스트. 인터넷 없이 돌고 값이 늘 같다.
 *
 * <p>실제 여덟 곳에 요청하는 테스트는 `network` 표가 붙어 있어 평소에는 건너뛴다. 사이트가 개편됐는지 보려면 `./gradlew test -Pnetwork` 로
 * 돌린다. 이 파일은 「내가 코드를 망가뜨렸나」만 본다.
 */
class CollectFromSamplesTest {

  private final HeadingFinder headingFinder = new HeadingFinder();

  @Test
  void 피드에_본문까지_실려_오는_곳() throws Exception {
    RssReader reader = new RssReader(xmlFrom("oliveyoung-rss.xml"), headingFinder);

    List<CollectedPost> posts = reader.read(new BlogSource("올리브영", SourceType.FEED_FULL, "sample"));

    assertThat(posts).hasSize(3);
    CollectedPost first = posts.get(0);
    assertThat(first.company()).isEqualTo("올리브영");
    assertThat(first.externalId()).isEqualTo("oy_sllm_alignment");
    assertThat(first.title()).contains("sLLM");
    assertThat(first.url()).isEqualTo("https://oliveyoung.tech/2026-09-18/oy_sllm_alignment/");
    // 피드가 18일 15시 GMT 로 주는 글. 한국 시간으로 옮기면 19일 0시다(결정 2-54).
    assertThat(first.publishedAt()).hasToString("2026-09-19T00:00");
    assertThat(first.charCount()).isEqualTo(first.bodyText().length());
    assertThat(first.hasCode()).isTrue();
    assertThat(first.headings()).isNotEmpty();
    assertThat(first.headings().get(0).idx()).isEqualTo(1);
    assertThat(first.sourceCategories()).isEmpty();
  }

  @Test
  void 사이트_분류어를_주는_곳() throws Exception {
    RssReader reader = new RssReader(xmlFrom("woowahan-rss.xml"), headingFinder);

    List<CollectedPost> posts =
        reader.read(new BlogSource("우아한형제들", SourceType.FEED_FULL, "sample"));

    assertThat(posts).hasSize(3);
    assertThat(posts).allMatch(post -> !post.sourceCategories().isEmpty());
    // 이 블로그만 guid 가 진짜 글 번호다. 주소 끝 조각이 아니다.
    assertThat(posts.get(0).externalId()).contains("?p=");
  }

  @Test
  void 글_페이지에서_본문을_고르는_곳() {
    ArticlePageReader reader = pageReaderFrom("kurly-article.html");

    Optional<CollectedPost> post =
        reader.read("컬리", "https://helloworld.kurly.com/blog/2026-delivery-domain-rag/");

    assertThat(post).isPresent();
    assertThat(post.get().externalId()).isEqualTo("2026-delivery-domain-rag");
    assertThat(post.get().charCount()).isGreaterThan(5_000);
    assertThat(post.get().headings()).hasSizeGreaterThan(10);
    assertThat(post.get().publishedAt()).hasToString("2026-05-21T00:00");
  }

  @Test
  void 꼬리말_메뉴를_본문으로_잡지_않는다() {
    // LY 는 꼬리말을 footer 가 아니라 div 로 만든다. 문서 전체에서 소제목을 세면
    // 「Development resources」 같은 메뉴 제목이 본문으로 뽑힌다(09-21 실측).
    ArticlePageReader reader = pageReaderFrom("line-article.html");

    Optional<CollectedPost> post =
        reader.read(
            "LY", "https://techblog.lycorp.co.jp/ko/how-to-measure-voice-quality-in-line-app");

    assertThat(post).isPresent();
    assertThat(post.get().charCount()).isGreaterThan(20_000);
    assertThat(post.get().headings()).hasSizeGreaterThan(15);
    assertThat(post.get().headings())
        .noneMatch(heading -> heading.title().contains("Development resources"));
  }

  @Test
  void 본문이_너무_짧으면_건너뛴다() {
    // LY 블로그 개설 인사말. 본문이 251자라 글로 저장하지 않는다.
    ArticlePageReader reader = pageReaderFrom("line-notice.html");

    assertThat(reader.read("LY", "https://techblog.lycorp.co.jp/ko/20231001a")).isEmpty();
  }

  @Test
  void 목록이_JSON_인_곳() throws Exception {
    NaverD2Reader reader = new NaverD2Reader(jsonFromSamples(), headingFinder);

    List<CollectedPost> posts =
        reader.read(new BlogSource("네이버 D2", SourceType.JSON_API, "sample"));

    assertThat(posts).isNotEmpty();
    CollectedPost first = posts.get(0);
    assertThat(first.externalId()).isEqualTo("2541696");
    assertThat(first.url()).startsWith("https://d2.naver.com/helloworld/");
    // epoch 밀리초로 오는 값을 한국 시간으로 옮긴다.
    assertThat(first.publishedAt()).hasToString("2026-07-06T19:10:17");
    assertThat(first.sourceCategories()).contains("hello world");
  }

  private XmlFetcher xmlFrom(String sample) {
    return new XmlFetcher() {
      @Override
      public Document fetch(String url) throws IOException {
        return Jsoup.parse(read(sample), "https://sample.test", Parser.xmlParser());
      }
    };
  }

  private ArticlePageReader pageReaderFrom(String sample) {
    PageFetcher fetcher =
        new PageFetcher() {
          @Override
          public Document fetch(String url) throws IOException {
            return Jsoup.parse(read(sample), url);
          }
        };
    return new ArticlePageReader(fetcher, new ArticleExtractor(), headingFinder);
  }

  /** 목록과 상세를 구분해 돌려준다. D2 는 글마다 상세를 한 번 더 부른다. */
  private NaverD2Reader.JsonFetcher jsonFromSamples() {
    return new NaverD2Reader.JsonFetcher() {
      private final ObjectMapper mapper = new ObjectMapper();

      @Override
      public JsonNode fetch(String url) throws Exception {
        boolean detail = url.contains("/contents/");
        if (url.contains("page=2") || url.contains("page=3")) {
          return mapper.readTree("{\"content\":[]}");
        }
        return mapper.readTree(read(detail ? "d2-detail.json" : "d2-list.json"));
      }
    };
  }

  private String read(String sample) {
    try (InputStream in = getClass().getResourceAsStream("/samples/" + sample)) {
      if (in == null) {
        throw new IllegalStateException("샘플이 없다: " + sample);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(sample + " 를 못 읽었다", e);
    }
  }
}
