package com.recu.collector.features.collect.service;

import com.recu.collector.features.collect.domain.BlogSource;
import com.recu.collector.features.collect.domain.SourceType;
import com.recu.collector.features.collect.domain.dto.CollectedPost;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * 피드에 본문까지 실려 오는 곳을 읽는다. 올리브영 · 인프랩 · SK플래닛 · 토스가 한 번에 오고, 우아한형제들은 쪽을 넘겨야 한다(결정 2-55).
 *
 * <p>목록만 오는 컬리, JSON 목록인 네이버 D2, 사이트맵인 LY 는 다른 구현이 맡는다.
 */
@Component
public class RssReader implements SourceReader {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  /** 쪽 넘김 안전 상한. 보통은 범위 밖 글이 나오는 쪽에서 먼저 멈춘다(우아한형제들 쪽당 10편). */
  private static final int MAX_PAGES = 30;

  private final XmlFetcher fetcher;
  private final HeadingFinder headingFinder;

  public RssReader(XmlFetcher fetcher, HeadingFinder headingFinder) {
    this.fetcher = fetcher;
    this.headingFinder = headingFinder;
  }

  @Override
  public boolean supports(SourceType type) {
    return type == SourceType.FEED_FULL || type == SourceType.FEED_PAGED;
  }

  @Override
  public List<CollectedPost> read(BlogSource source) throws Exception {
    if (source.type() == SourceType.FEED_PAGED) {
      return readAllPages(source);
    }
    return readOnePage(source.company(), source.url());
  }

  /** 쪽을 넘기며 읽는다. 새 주소가 없는 쪽이나 받을 범위보다 오래된 글이 나온 쪽에서 멈춘다. */
  private List<CollectedPost> readAllPages(BlogSource source) throws Exception {
    List<CollectedPost> all = new ArrayList<>();
    Set<String> seen = new HashSet<>();

    for (int page = 1; page <= MAX_PAGES; page++) {
      List<CollectedPost> posts = readOnePage(source.company(), pageUrl(source.url(), page));
      List<CollectedPost> fresh = posts.stream().filter(post -> seen.add(post.url())).toList();
      if (fresh.isEmpty()) {
        break;
      }
      all.addAll(fresh);
      if (fresh.stream().anyMatch(post -> !source.covers(post.publishedAt()))) {
        break;
      }
    }
    return all;
  }

  private String pageUrl(String feedUrl, int page) {
    if (page == 1) {
      return feedUrl;
    }
    return feedUrl + (feedUrl.contains("?") ? "&" : "?") + "paged=" + page;
  }

  private List<CollectedPost> readOnePage(String company, String url) throws Exception {
    Document doc = fetcher.fetch(url);
    List<CollectedPost> posts = new ArrayList<>();
    for (Element item : doc.select("item")) {
      posts.add(toPost(company, item));
    }
    return posts;
  }

  private CollectedPost toPost(String company, Element item) {
    String url = text(item, "link");
    String bodyHtml = text(item, "content|encoded");
    Element parsedBody = Jsoup.parseBodyFragment(bodyHtml).body();
    String bodyText = parsedBody.text();

    return new CollectedPost(
        company,
        externalId(item, url),
        text(item, "title"),
        url,
        publishedAt(text(item, "pubDate")),
        bodyHtml,
        bodyText,
        bodyText.length(),
        !parsedBody.select("pre, code").isEmpty(),
        headingFinder.find(parsedBody),
        item.select("category").stream().map(Element::text).toList());
  }

  /**
   * 글 고유값. 우아한형제들처럼 guid 가 주소와 다르면 그 값을 쓰고, 같으면(올리브영 · 인프랩 · SK플래닛 · 토스) 주소 끝 조각을 쓴다. 실측상 다섯 곳 중 넷이
   * 뒤쪽이다.
   */
  private String externalId(Element item, String url) {
    String guid = text(item, "guid");
    if (!guid.isEmpty() && !guid.equals(url)) {
      return guid;
    }
    String path = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    int slash = path.lastIndexOf('/');
    return slash < 0 ? path : path.substring(slash + 1);
  }

  /** 표기가 GMT 와 +0000 두 가지인데 둘 다 RFC 1123 이라 같은 형식기로 읽힌다. 읽은 뒤 한국 시간으로 옮기고 시간대를 뗀다(결정 2-54). */
  private LocalDateTime publishedAt(String raw) {
    ZonedDateTime parsed = ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME);
    return parsed.withZoneSameInstant(KST).toLocalDateTime();
  }

  private String text(Element item, String tag) {
    Element found = item.selectFirst(tag);
    return found == null ? "" : found.text();
  }
}
