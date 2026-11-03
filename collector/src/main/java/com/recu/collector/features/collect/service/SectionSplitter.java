package com.recu.collector.features.collect.service;

import com.recu.collector.features.collect.domain.dto.ImportedPost;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 글 본문을 소제목으로 잘라 구간을 만든다.
 *
 * <p>본문은 통짜 텍스트고 소제목은 따로 목록으로 온다. 본문에서 소제목 위치를 앞에서부터 순서대로 찾아 그 사이를 구간으로 삼는다. 이미 찾은 자리 다음에서만 다음 소제목을
 * 찾으므로 같은 문구가 여러 번 나와도 순서가 꼬이지 않는다.
 *
 * <p>첫 소제목 앞의 글은 버린다. 머리말이나 목차 부스러기다. 소제목이 없는 글은 구간을 만들지 않는다.
 */
@Service
public class SectionSplitter {

  /** 잘라낸 구간 하나. seq 는 1부터. */
  public record Section(int seq, String heading, String level, String contentText) {}

  public List<Section> split(ImportedPost post) {
    List<ImportedPost.Heading> headings = post.headings();
    String body = post.body_text();
    if (headings == null || headings.isEmpty() || body == null || body.isBlank()) {
      return List.of();
    }

    // 1) 본문에서 각 소제목이 시작하는 자리를 순서대로 찾는다
    List<ImportedPost.Heading> found = new ArrayList<>();
    List<Integer> starts = new ArrayList<>();
    int cursor = 0;
    for (ImportedPost.Heading h : headings) {
      String title = h.title() == null ? "" : h.title().trim();
      if (title.isEmpty()) {
        continue;
      }
      int at = body.indexOf(title, cursor);
      if (at < 0) {
        continue; // 본문에서 못 찾은 소제목은 건너뛴다
      }
      found.add(h);
      starts.add(at);
      cursor = at + title.length();
    }

    // 2) 이번 소제목 끝부터 다음 소제목 시작까지가 그 구간의 본문
    List<Section> sections = new ArrayList<>();
    for (int i = 0; i < found.size(); i++) {
      ImportedPost.Heading h = found.get(i);
      int from = starts.get(i) + h.title().trim().length();
      int to = (i + 1 < starts.size()) ? starts.get(i + 1) : body.length();
      String text = body.substring(from, Math.max(from, to)).trim();
      sections.add(new Section(i + 1, cut(h.title().trim(), 300), h.level(), text));
    }
    return sections;
  }

  private String cut(String s, int max) {
    return s.length() <= max ? s : s.substring(0, max);
  }
}
