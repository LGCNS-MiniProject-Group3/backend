package com.recu.collector.features.collect.service;

import com.recu.collector.features.collect.domain.dto.ImportedPost;
import com.recu.common.kafka.event.PostCollectedEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** ImportedPost를 Kafka A(PostCollectedEvent)로 바꾼다. companyId는 호출하는 쪽이 넘긴다. */
@Component
public class PostCollectedEventMapper {

  private static final ZoneOffset KST = ZoneOffset.of("+09:00");

  private final SectionSplitter sectionSplitter;

  public PostCollectedEventMapper(SectionSplitter sectionSplitter) {
    this.sectionSplitter = sectionSplitter;
  }

  public PostCollectedEvent toEvent(ImportedPost row, Long companyId, TechNameResolver resolver) {
    ImportedPost.Ai ai = row.ai();
    return new PostCollectedEvent(
        companyId,
        row.url(),
        row.external_id(),
        row.title(),
        toPublishedAt(row.published_at()),
        row.body_html(),
        row.body_text(),
        row.has_code(),
        ai == null ? null : ai.is_tech(),
        ai == null ? null : ai.field(),
        ai == null ? null : ai.level(),
        ai == null ? null : ai.summary(),
        toCategories(row),
        toTags(ai, resolver),
        toSections(row));
  }

  static OffsetDateTime toPublishedAt(String raw) {
    if (raw == null || raw.isBlank()) {
      return OffsetDateTime.now(KST);
    }
    String v = raw.trim();
    try {
      return OffsetDateTime.parse(v);
    } catch (DateTimeParseException ignored) {
    }
    try {
      return LocalDateTime.parse(v).atOffset(KST);
    } catch (DateTimeParseException ignored) {
    }
    try {
      return LocalDate.parse(v.substring(0, Math.min(10, v.length()))).atStartOfDay().atOffset(KST);
    } catch (DateTimeParseException e) {
      return OffsetDateTime.now(KST);
    }
  }

  /** 사이트가 붙인 분류어. AI 계열(field)과는 다른 값이다. */
  private static List<String> toCategories(ImportedPost row) {
    if (row.source_categories() == null) {
      return List.of();
    }
    Set<String> seen = new LinkedHashSet<>();
    for (String name : row.source_categories()) {
      if (name != null && !name.isBlank()) {
        seen.add(name.trim());
      }
    }
    return List.copyOf(seen);
  }

  private static List<PostCollectedEvent.Tag> toTags(
      ImportedPost.Ai ai, TechNameResolver resolver) {
    if (ai == null || ai.core_techs() == null || ai.core_techs().isEmpty()) {
      return List.of();
    }

    Map<String, PostCollectedEvent.Tag> byLower = new LinkedHashMap<>();
    int rank = 1;
    for (String raw : ai.core_techs()) {
      if (raw == null || raw.isBlank()) {
        continue;
      }
      String name = resolver == null ? raw.trim() : resolver.resolve(raw);
      if (name == null || name.isBlank()) {
        continue;
      }

      String key = name.toLowerCase(Locale.ROOT);
      if (!byLower.containsKey(key)) {
        byLower.put(key, new PostCollectedEvent.Tag(name, rank++));
      }
    }

    return List.copyOf(byLower.values());
  }

  private List<PostCollectedEvent.Section> toSections(ImportedPost row) {
    List<SectionSplitter.Section> split = sectionSplitter.split(row);
    if (split == null || split.isEmpty()) {
      return List.of();
    }
    return split.stream()
        .map(s -> new PostCollectedEvent.Section(s.seq(), s.heading(), s.level(), s.contentText()))
        .toList();
  }
}
