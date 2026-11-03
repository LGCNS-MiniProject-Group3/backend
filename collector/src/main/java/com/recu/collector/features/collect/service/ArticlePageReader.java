package com.recu.collector.features.collect.service;

import com.recu.collector.features.collect.domain.dto.CollectedPost;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * 글 페이지 하나를 읽어 글 한 편으로 만든다. 목록에 본문이 안 실려 오는 컬리 · LY 가 쓴다.
 *
 * <p>제목과 발행 시각은 페이지가 스스로 밝힌 값(`og:title` · `article:published_time`)을 쓴다. 본문은 {@link
 * ArticleExtractor} 가 고른다.
 */
@Component
public class ArticlePageReader {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final PageFetcher fetcher;
  private final ArticleExtractor extractor;
  private final HeadingFinder headingFinder;

  public ArticlePageReader(
      PageFetcher fetcher, ArticleExtractor extractor, HeadingFinder headingFinder) {
    this.fetcher = fetcher;
    this.extractor = extractor;
    this.headingFinder = headingFinder;
  }

  /** 못 읽거나 본문을 못 고르면 비어서 돌아온다. 부르는 쪽이 그 글만 건너뛴다. */
  public Optional<CollectedPost> read(String company, String url) {
    Document page;
    try {
      page = fetcher.fetch(url);
    } catch (Exception e) {
      return Optional.empty();
    }

    Optional<Element> body = extractor.extract(page);
    LocalDateTime publishedAt = publishedAt(page);
    if (body.isEmpty() || publishedAt == null) {
      return Optional.empty();
    }

    String bodyHtml = body.get().html();
    String bodyText = body.get().text();

    return Optional.of(
        new CollectedPost(
            company,
            externalId(url),
            title(page),
            url,
            publishedAt,
            bodyHtml,
            bodyText,
            bodyText.length(),
            !body.get().select("pre, code").isEmpty(),
            headingFinder.find(body.get()),
            List.of()));
  }

  /** `og:title` 이 사이트 이름이 안 붙은 제목이다. 없으면 문서 제목을 쓴다. */
  private String title(Document page) {
    Element og = page.selectFirst("meta[property=og:title]");
    if (og != null && !og.attr("content").isBlank()) {
      return og.attr("content").trim();
    }
    return page.title().trim();
  }

  /** 컬리 · LY 모두 ISO 8601 로 준다. 읽어서 한국 시간으로 옮긴다(2-54). */
  private LocalDateTime publishedAt(Document page) {
    Element meta = page.selectFirst("meta[property=article:published_time]");
    if (meta == null || meta.attr("content").isBlank()) {
      return null;
    }
    try {
      return ZonedDateTime.parse(meta.attr("content")).withZoneSameInstant(KST).toLocalDateTime();
    } catch (Exception e) {
      return null;
    }
  }

  /** 주소 끝 조각. 둘 다 글 번호를 주지 않는다. */
  private String externalId(String url) {
    String path = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    int slash = path.lastIndexOf('/');
    return slash < 0 ? path : path.substring(slash + 1);
  }
}
