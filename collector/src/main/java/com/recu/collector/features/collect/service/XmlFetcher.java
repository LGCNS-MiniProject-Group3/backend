package com.recu.collector.features.collect.service;

import java.io.IOException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

/**
 * 피드 · 사이트맵을 받아 XML 로 파싱한다.
 *
 * <p>받기와 파싱을 나눈 이유가 있다. Jsoup 의 get() 은 읽으면서 파싱하는데 그 경로에서 maxBodySize 가 듣지 않아 올리브영 10.7MB 피드가 204편
 * 중 31편에서 잘렸다(09-21 실측).
 */
@Component
public class XmlFetcher {

  static final String AGENT = "mini8-collector/0.1";
  static final int TIMEOUT_MS = 60_000;

  public Document fetch(String url) throws IOException {
    String body =
        Jsoup.connect(url).userAgent(AGENT).timeout(TIMEOUT_MS).maxBodySize(0).execute().body();
    return Jsoup.parse(body, url, Parser.xmlParser());
  }
}
