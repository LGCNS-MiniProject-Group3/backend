package com.recu.collector.features.collect.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** 컬리 · LY 글 페이지에서 본문이 제대로 잡히는지 본다. 주소는 목록에서 받아 쓴다(지어내면 없는 글을 부른다). */
@Tag("network")
class ArticleExtractorTest {

  private static final String AGENT = "mini8-collector/0.1";
  private static final int PER_SITE = 3;

  private final ArticleExtractor extractor = new ArticleExtractor();

  @Test
  void 컬리_글_페이지에서_본문을_고른다() throws Exception {
    Document feed = xml("https://helloworld.kurly.com/rss.xml");
    List<String> urls =
        feed.select("item > link").stream().map(Element::text).limit(PER_SITE).toList();

    assertThat(urls).hasSize(PER_SITE);
    urls.forEach(url -> 본문을_고른다("컬리", url));
  }

  @Test
  void 라인_글_페이지에서_본문을_고른다() throws Exception {
    Document sitemap = xml("https://techblog.lycorp.co.jp/sitemap-0.xml");
    List<String> all =
        sitemap.select("loc").stream()
            .map(Element::text)
            .filter(u -> u.contains("/ko/") && !u.contains("/tag/") && !u.contains("/page/"))
            .toList();
    // 사이트맵이 오래된 순이라 앞쪽은 2023년 개설 공지다. 뒤에서 뽑아 최근 글로 본다.
    List<String> urls = all.subList(all.size() - PER_SITE, all.size());

    assertThat(urls).hasSize(PER_SITE);
    urls.forEach(url -> 본문을_고른다("LY", url));
  }

  @Test
  void 본문이_너무_짧으면_건너뛴다() {
    // LY 블로그 개설 인사말. 본문이 251자라 글로 저장하지 않는다.
    assertThat(extractor.extract(page("https://techblog.lycorp.co.jp/ko/20231001a"))).isEmpty();
  }

  private void 본문을_고른다(String company, String url) {
    Document page = page(url);
    Optional<Element> body = extractor.extract(page);

    System.out.printf(
        "%-4s | 페이지 %,7d자 | 본문 %s | 소제목 %2d개 | %s%n",
        company,
        page.text().length(),
        body.map(e -> String.format("%,6d자", e.text().length())).orElse("  못 고름"),
        body.map(e -> e.select("h2, h3").size()).orElse(0),
        url.substring(url.lastIndexOf('/') + 1));

    assertThat(body).isPresent();
    assertThat(body.get().text().length()).isGreaterThan(500);
    assertThat(body.get().text().length()).isLessThan(page.text().length());
  }

  private Document page(String url) {
    try {
      return Jsoup.connect(url).userAgent(AGENT).timeout(60_000).maxBodySize(0).get();
    } catch (Exception e) {
      throw new IllegalStateException(url + " 를 못 받았다: " + e.getMessage(), e);
    }
  }

  private Document xml(String url) throws Exception {
    String body =
        Jsoup.connect(url).userAgent(AGENT).timeout(60_000).maxBodySize(0).execute().body();
    return Jsoup.parse(body, url, Parser.xmlParser());
  }
}
