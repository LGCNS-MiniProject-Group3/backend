package com.recu.collector.features.collect.service;

import com.recu.collector.features.collect.domain.dto.CollectedPost.Heading;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * 본문 HTML 에서 소제목을 뽑는다. 글 하나가 구간 여러 개로 나뉘고, AI 가이드가 「몇 번째 구간을 먼저 읽어라」로 이 번호를 가리킨다.
 *
 * <p>실측상 h2 · h3 만 나온다(h2 2,177개 · h3 2,576개). h1 은 글 제목이라 본문에 없고 h4 이하는 한 건도 없었다.
 */
@Component
public class HeadingFinder {

  public List<Heading> find(String bodyHtml) {
    if (bodyHtml == null || bodyHtml.isBlank()) {
      return List.of();
    }
    return find(Jsoup.parseBodyFragment(bodyHtml).body());
  }

  /** 이미 파싱해 둔 블록에서 찾는다. 컬리 · LY 는 글 페이지를 파싱한 결과를 그대로 넘긴다. */
  public List<Heading> find(Element body) {
    List<Heading> headings = new ArrayList<>();
    int idx = 1;
    for (Element element : body.select("h2, h3")) {
      String title = element.text().trim();
      if (title.isEmpty()) {
        continue; // 이미지만 든 소제목이 있다. 번호를 주지 않는다
      }
      headings.add(new Heading(idx++, element.tagName(), title));
    }
    return headings;
  }
}
