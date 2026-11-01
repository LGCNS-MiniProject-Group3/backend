package com.recu.collector.features.collect.service;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

/**
 * 글 페이지에서 본문 블록을 고른다. 컬리와 LY 처럼 목록에 본문이 안 실려 오는 곳에 쓴다.
 *
 * <p>고르는 규칙은 402편을 받을 때 쓴 것과 같다(결정 2-38). <b>소제목을 가장 많이 품으면서 가장 작은 블록</b>을 고른다. 바깥으로 갈수록 메뉴와 꼬리말이
 * 딸려 오고, 안으로 들어갈수록 소제목을 놓친다.
 */
@Component
public class ArticleExtractor {

  /** 이보다 짧으면 엉뚱한 블록을 잡은 것으로 본다. 실측 본문 최소가 1,240자다(SK플래닛 세미나 공지). */
  private static final int MIN_CHARS = 500;

  private static final String NOISE =
      "script, style, nav, header, footer, aside, form, noscript, iframe";

  /** 본문 블록. 못 고르면 비어서 돌아온다. 부르는 쪽이 그 글을 건너뛰고 기록을 남긴다. */
  public Optional<Element> extract(Document page) {
    Document copy = page.clone();
    copy.select(NOISE).remove();

    // 먼저 본문 영역으로 범위를 좁힌다. LY 는 꼬리말 메뉴를 footer 가 아니라 div 로 만들어서
    // 문서 전체에서 소제목을 세면 「Development resources」 같은 메뉴 제목이 본문으로 뽑힌다(09-21 실측).
    Element scope = copy.selectFirst("main");
    if (scope == null) {
      scope = copy.selectFirst("article");
    }
    if (scope == null) {
      scope = copy.body();
    }

    // 소제목이 아예 없는 글이 402편 중 42편이다. 그때는 범위 자체를 본문으로 본다.
    Element best = blockHoldingMostHeadings(scope);
    if (best == null) {
      best = scope;
    }
    if (best.text().length() < MIN_CHARS) {
      return Optional.empty();
    }
    return Optional.of(best);
  }

  /** 소제목마다 조상을 거슬러 올라가며 표를 매긴다. 표를 가장 많이 받은 블록이 본문을 감싼 블록이다. */
  private Element blockHoldingMostHeadings(Element scope) {
    Elements headings = scope.select("h2, h3");
    if (headings.isEmpty()) {
      return null;
    }

    Map<Element, Integer> votes = new IdentityHashMap<>();
    for (Element heading : headings) {
      for (Element parent = heading.parent(); parent != null; parent = parent.parent()) {
        votes.merge(parent, 1, Integer::sum);
      }
    }

    int most = votes.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    return votes.entrySet().stream()
        .filter(entry -> entry.getValue() == most)
        .map(Map.Entry::getKey)
        .min(Comparator.comparingInt(element -> element.text().length()))
        .orElse(null);
  }
}
