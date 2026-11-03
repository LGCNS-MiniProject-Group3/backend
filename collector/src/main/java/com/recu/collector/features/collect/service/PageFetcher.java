package com.recu.collector.features.collect.service;

import java.io.IOException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

/** 글 페이지 하나를 받아 온다. 테스트가 받아 둔 파일로 바꿔 끼울 수 있게 따로 뒀다. */
@Component
public class PageFetcher {

  public Document fetch(String url) throws IOException {
    return Jsoup.connect(url)
        .userAgent(XmlFetcher.AGENT)
        .timeout(XmlFetcher.TIMEOUT_MS)
        .maxBodySize(0)
        .get();
  }
}
