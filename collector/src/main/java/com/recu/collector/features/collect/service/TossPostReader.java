package com.recu.collector.features.collect.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.recu.collector.features.collect.domain.BlogSource;
import com.recu.collector.features.collect.domain.SourceType;
import com.recu.collector.features.collect.domain.dto.CollectedPost;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * 토스. 피드가 최근 20편만 줘서 12개월치를 받으려면 목록 API 를 써야 한다.
 *
 * <p>카테고리를 가리지 않고 다 받는다(Engineering · Design). 다른 기업처럼 기술 글인지는 AI 분류가 가른다.
 *
 * <p>목록 API 는 제목 · 주소 조각(key) · 발행 시각 · 분류를 주고 본문은 소제목 없는 평문만 준다. 구간을 나누려면 소제목이 있어야 해서 본문은 글
 * 페이지(`/article/{key}`)에서 읽는다. 글 페이지에는 발행 시각 메타가 없어 시각은 목록 값을 쓴다.
 */
@Component
public class TossPostReader implements SourceReader {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final String ARTICLE = "https://toss.tech/article/";
  private static final int PAGE_SIZE = 20;

  /** 목록 쪽 안전 상한. 보통은 한 쪽이 통째로 범위 밖이 되는 곳에서 먼저 멈춘다. */
  private static final int MAX_PAGES = 30;

  private final NaverD2Reader.JsonFetcher jsonFetcher;
  private final PageFetcher pageFetcher;
  private final ArticleExtractor extractor;
  private final HeadingFinder headingFinder;

  public TossPostReader(
      NaverD2Reader.JsonFetcher jsonFetcher,
      PageFetcher pageFetcher,
      ArticleExtractor extractor,
      HeadingFinder headingFinder) {
    this.jsonFetcher = jsonFetcher;
    this.pageFetcher = pageFetcher;
    this.extractor = extractor;
    this.headingFinder = headingFinder;
  }

  @Override
  public boolean supports(SourceType type) {
    return type == SourceType.JSON_LIST_PAGE;
  }

  /** 목록 한 줄. 본문을 열기 전에 범위부터 거른다. */
  record Listing(String key, String title, LocalDateTime publishedAt, List<String> categories) {}

  @Override
  public List<CollectedPost> read(BlogSource source) throws Exception {
    List<CollectedPost> posts = new ArrayList<>();
    for (Listing listing : listings(source)) {
      page(source.company(), listing).ifPresent(posts::add);
    }
    return posts;
  }

  /** 범위 안 목록. 정렬이 발행 시각 순이 아니라서 한 쪽이 통째로 범위 밖일 때만 멈춘다. */
  List<Listing> listings(BlogSource source) throws Exception {
    List<Listing> inWindow = new ArrayList<>();
    for (int page = 1; page <= MAX_PAGES; page++) {
      JsonNode body = jsonFetcher.fetch(source.url() + "?page=" + page + "&size=" + PAGE_SIZE);
      JsonNode data = body.path("success");
      JsonNode results = data.path("results");
      if (!results.isArray() || results.isEmpty()) {
        break;
      }
      boolean anyInWindow = false;
      for (JsonNode item : results) {
        Listing listing = toListing(item);
        if (listing != null && source.covers(listing.publishedAt())) {
          inWindow.add(listing);
          anyInWindow = true;
        }
      }
      if (!anyInWindow || data.path("next").isNull()) {
        break;
      }
    }
    return inWindow;
  }

  private Listing toListing(JsonNode item) {
    String key = item.path("key").asText("");
    String published = item.path("publishedTime").asText("");
    if (key.isBlank() || published.isBlank()) {
      return null;
    }
    List<String> categories = new ArrayList<>();
    for (JsonNode category : item.path("categories")) {
      String name = category.path("name").asText("");
      if (!name.isBlank()) {
        categories.add(name);
      }
    }
    LocalDateTime publishedAt =
        OffsetDateTime.parse(published).atZoneSameInstant(KST).toLocalDateTime();
    return new Listing(key, item.path("title").asText(""), publishedAt, categories);
  }

  /** 글 페이지에서 본문을 고른다. 못 읽거나 본문을 못 고르면 그 글만 건너뛴다. */
  private Optional<CollectedPost> page(String company, Listing listing) {
    String url = ARTICLE + listing.key();
    Document page;
    try {
      page = pageFetcher.fetch(url);
    } catch (Exception e) {
      return Optional.empty();
    }
    Optional<Element> body = extractor.extract(page);
    if (body.isEmpty()) {
      return Optional.empty();
    }
    String bodyText = body.get().text();
    return Optional.of(
        new CollectedPost(
            company,
            listing.key(),
            listing.title(),
            url,
            listing.publishedAt(),
            body.get().html(),
            bodyText,
            bodyText.length(),
            !body.get().select("pre, code").isEmpty(),
            headingFinder.find(body.get()),
            listing.categories()));
  }
}
