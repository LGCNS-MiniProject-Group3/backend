package com.recu.collector.features.collect.service;

import com.recu.collector.features.collect.domain.BlogSource;
import com.recu.collector.features.collect.domain.SourceType;
import com.recu.collector.features.collect.domain.dto.CollectedPost;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * 피드에 목록만 오는 곳. 컬리 하나다(결정 2-55).
 *
 * <p>주소만 피드에서 얻고 제목 · 발행 시각 · 본문은 글 페이지에서 읽는다. 피드에도 제목과 날짜가 있지만 한 군데서 읽는 편이 규칙이 하나로 유지된다.
 */
@Component
public class FeedListReader implements SourceReader {

  /** 한 번에 열어 볼 페이지 안전 상한. 보통은 받을 범위보다 오래된 글이 나오는 곳에서 먼저 멈춘다. */
  private static final int MAX_ARTICLES = 300;

  private final XmlFetcher fetcher;
  private final ArticlePageReader pageReader;

  public FeedListReader(XmlFetcher fetcher, ArticlePageReader pageReader) {
    this.fetcher = fetcher;
    this.pageReader = pageReader;
  }

  @Override
  public boolean supports(SourceType type) {
    return type == SourceType.FEED_LIST;
  }

  @Override
  public List<CollectedPost> read(BlogSource source) throws Exception {
    Document feed = fetcher.fetch(source.url());
    List<String> urls =
        feed.select("item > link").stream().map(Element::text).limit(MAX_ARTICLES).toList();

    // 피드가 최신순이라 범위 밖 글이 하나 나오면 뒤는 열지 않는다
    List<CollectedPost> posts = new ArrayList<>();
    for (String url : urls) {
      Optional<CollectedPost> post = pageReader.read(source.company(), url);
      if (post.isEmpty()) {
        continue;
      }
      if (!source.covers(post.get().publishedAt())) {
        break;
      }
      posts.add(post.get());
    }
    return posts;
  }
}
