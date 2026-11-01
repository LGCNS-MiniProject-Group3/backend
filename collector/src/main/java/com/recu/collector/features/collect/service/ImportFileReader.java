package com.recu.collector.features.collect.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recu.collector.features.collect.domain.dto.ImportedPost;
import com.recu.collector.features.collect.domain.dto.TechDictionary;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/**
 * 수집 단계에서 만든 파일을 읽는다.
 *
 * <p>분류 파일에는 AI 결과가, 본문 파일에는 HTML·텍스트·소제목이 들어 있다. 같은 글이 양쪽에 한 번씩 있고 url 이 같다. 둘을 합쳐 글 한 편을 온전한 모양으로
 * 만든다.
 */
@Service
public class ImportFileReader {

  private static final String CLASSIFIED = "classified.jsonl";
  private static final List<String> BODY_FILES =
      List.of("posts_12m.jsonl", "posts_12m_crawl.jsonl", "posts_12m_toss.jsonl");
  private static final String DICTIONARY = "tech_dict.json";

  private final ObjectMapper mapper;

  public ImportFileReader(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /** 분류 파일과 본문 파일을 url 로 합쳐 글 목록을 만든다. 본문이 없는 글은 버린다. */
  public List<ImportedPost> readPosts(Path dir) {
    Map<String, ImportedPost> bodies = new LinkedHashMap<>();
    for (String name : BODY_FILES) {
      Path p = dir.resolve(name);
      if (Files.exists(p)) {
        readJsonl(p).forEach(row -> bodies.put(row.url(), row));
      }
    }

    List<ImportedPost> merged = new ArrayList<>();
    for (ImportedPost c : readJsonl(dir.resolve(CLASSIFIED))) {
      ImportedPost b = bodies.get(c.url());
      if (b == null) {
        continue; // 본문이 없으면 표에 넣을 수 없다
      }
      merged.add(
          new ImportedPost(
              c.company(),
              c.external_id(),
              c.title(),
              c.url(),
              c.published_at(),
              c.char_count(),
              c.has_code(),
              c.source_categories(),
              b.headings(),
              b.body_html(),
              b.body_text(),
              c.ai()));
    }
    return merged;
  }

  public TechDictionary readDictionary(Path dir) {
    try {
      return mapper.readValue(dir.resolve(DICTIONARY).toFile(), TechDictionary.class);
    } catch (IOException e) {
      throw new UncheckedIOException("기술 사전을 읽지 못했습니다: " + dir.resolve(DICTIONARY), e);
    }
  }

  /** 한 줄에 글 하나가 담긴 파일(JSON Lines)을 읽는다. */
  private List<ImportedPost> readJsonl(Path path) {
    try (Stream<String> lines = Files.lines(path)) {
      return lines
          .map(String::trim)
          .filter(line -> !line.isEmpty())
          .map(
              line -> {
                try {
                  return mapper.readValue(line, ImportedPost.class);
                } catch (IOException e) {
                  throw new UncheckedIOException("줄을 읽지 못했습니다: " + path, e);
                }
              })
          .filter(row -> row.url() != null)
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("파일을 읽지 못했습니다: " + path, e);
    }
  }
}
