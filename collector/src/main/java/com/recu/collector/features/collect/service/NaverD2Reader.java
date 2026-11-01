package com.recu.collector.features.collect.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recu.collector.features.collect.domain.BlogSource;
import com.recu.collector.features.collect.domain.SourceType;
import com.recu.collector.features.collect.domain.dto.CollectedPost;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * 목록이 JSON 인 곳. 네이버 D2 하나다(결정 2-55).
 *
 * <p>목록의 `postHtml` 은 256자로 잘려 오므로 글마다 상세를 한 번 더 부른다. 호출 수가 다른 기업의 열 배라 쪽 수를 제한한다.
 */
@Component
public class NaverD2Reader implements SourceReader {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final String DOMAIN = "https://d2.naver.com";

  /** 목록 쪽 안전 상한. 보통은 받을 범위보다 오래된 글이 나오는 곳에서 먼저 멈춘다. 쪽당 10편. */
  private static final int MAX_PAGES = 30;

  private static final int PAGE_SIZE = 10;

  private final JsonFetcher fetcher;
  private final HeadingFinder headingFinder;

  public NaverD2Reader(JsonFetcher fetcher, HeadingFinder headingFinder) {
    this.fetcher = fetcher;
    this.headingFinder = headingFinder;
  }

  @Override
  public boolean supports(SourceType type) {
    return type == SourceType.JSON_API;
  }

  @Override
  public List<CollectedPost> read(BlogSource source) throws Exception {
    List<CollectedPost> posts = new ArrayList<>();

    for (int page = 1; page <= MAX_PAGES; page++) {
      JsonNode list = fetcher.fetch(source.url() + "?page=" + page + "&size=" + PAGE_SIZE);
      JsonNode content = list.path("content");
      if (!content.isArray() || content.isEmpty()) {
        break;
      }
      for (JsonNode item : content) {
        java.util.Optional<CollectedPost> post =
            toPost(source.company(), item.path("url").asText());
        if (post.isEmpty()) {
          continue;
        }
        // 목록이 최신 순이라 범위 밖 글이 하나 나오면 뒤는 볼 필요가 없다
        if (!source.covers(post.get().publishedAt())) {
          return posts;
        }
        posts.add(post.get());
      }
    }
    return posts;
  }

  /** 상세를 불러 글 한 편을 만든다. `/helloworld/2541696` 의 번호가 글 고유값이다. */
  private java.util.Optional<CollectedPost> toPost(String company, String path) {
    if (path.isBlank()) {
      return java.util.Optional.empty();
    }
    String id = path.substring(path.lastIndexOf('/') + 1);

    JsonNode detail;
    try {
      detail = fetcher.fetch(DOMAIN + "/api/v1/contents/" + id);
    } catch (Exception e) {
      return java.util.Optional.empty();
    }

    String bodyHtml = detail.path("postHtml").asText("");
    if (bodyHtml.isBlank()) {
      return java.util.Optional.empty();
    }
    Element parsedBody = Jsoup.parseBodyFragment(bodyHtml).body();
    String bodyText = parsedBody.text();

    return java.util.Optional.of(
        new CollectedPost(
            company,
            id,
            detail.path("postTitle").asText(""),
            DOMAIN + path,
            publishedAt(detail.path("postPublishedAt").asLong()),
            bodyHtml,
            bodyText,
            bodyText.length(),
            !parsedBody.select("pre, code").isEmpty(),
            headingFinder.find(parsedBody),
            categories(detail)));
  }

  /** 분류어를 주는 두 곳 중 하나다. 갈래 이름과 글 태그를 함께 담는다. */
  private List<String> categories(JsonNode detail) {
    List<String> names = new ArrayList<>();
    String category = detail.path("categoryName").asText("");
    if (!category.isBlank()) {
      names.add(category);
    }
    for (JsonNode tag : detail.path("postTags")) {
      String name = tag.isTextual() ? tag.asText() : tag.path("name").asText("");
      if (!name.isBlank()) {
        names.add(name);
      }
    }
    return names;
  }

  /** epoch 밀리초로 온다. 한국 시간으로 옮긴다(2-54). */
  private LocalDateTime publishedAt(long epochMillis) {
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), KST);
  }

  /** JSON 을 받아 파싱한다. */
  @Component
  public static class JsonFetcher {

    private final ObjectMapper mapper = new ObjectMapper();

    public JsonNode fetch(String url) throws Exception {
      String body =
          org.jsoup.Jsoup.connect(url)
              .userAgent(XmlFetcher.AGENT)
              .timeout(XmlFetcher.TIMEOUT_MS)
              .maxBodySize(0)
              .ignoreContentType(true)
              .execute()
              .body();
      return mapper.readTree(body);
    }
  }
}
