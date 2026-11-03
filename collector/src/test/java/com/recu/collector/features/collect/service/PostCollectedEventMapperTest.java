package com.recu.collector.features.collect.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recu.collector.features.collect.domain.dto.ImportedPost;
import com.recu.common.kafka.event.PostCollectedEvent;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PostCollectedEventMapperTest {

  private final PostCollectedEventMapper mapper =
      new PostCollectedEventMapper(new SectionSplitter());

  private ImportedPost row(String publishedAt, ImportedPost.Ai ai) {
    return row(publishedAt, ai, List.of());
  }

  private ImportedPost row(String publishedAt, ImportedPost.Ai ai, List<String> sourceCategories) {
    return new ImportedPost(
        "토스",
        "ext-1",
        "제목",
        "https://toss.tech/a",
        publishedAt,
        10,
        true,
        sourceCategories,
        List.of(), // headings 없음 → sections []
        "<p>html</p>",
        "text",
        ai);
  }

  @Test
  @DisplayName("published_at에 오프셋이 없으면 +09:00을 붙인다")
  void publishedAt_오프셋() {
    PostCollectedEvent event = mapper.toEvent(row("2026-03-18T00:00", null), 5L, null);

    assertThat(event.publishedAt().getOffset()).isEqualTo(ZoneOffset.of("+09:00"));
    assertThat(event.publishedAt().toLocalDateTime().toString()).startsWith("2026-03-18T00:00");
  }

  @Test
  @DisplayName("ai가 없으면 categories·tags·sections는 빈 배열")
  void 빈목록은_빈배열() {
    PostCollectedEvent event = mapper.toEvent(row("2026-03-18T00:00", null), 5L, null);

    assertThat(event.categories()).isEmpty();
    assertThat(event.tags()).isEmpty();
    assertThat(event.sections()).isEmpty();
  }

  @Test
  @DisplayName("대소문자만 다른 태그는 하나로, rank는 넣은 순서대로 빈 번호 없이")
  void 태그_중복_rank() {
    // resolver=null → raw 이름 그대로 (Kafka / kafka)
    ImportedPost.Ai ai =
        new ImportedPost.Ai(true, List.of("Kafka", "kafka", "Redis"), "Backend", "중급", "요지");

    PostCollectedEvent event = mapper.toEvent(row("2026-03-18T00:00", ai), 5L, null);

    assertThat(event.tags()).hasSize(2);
    assertThat(event.tags().get(0).name()).isEqualToIgnoringCase("Kafka");
    assertThat(event.tags().get(0).rank()).isEqualTo(1); // 첫 Kafka
    assertThat(event.tags().get(1).name()).isEqualTo("Redis");
    assertThat(event.tags().get(1).rank()).isEqualTo(2);
  }

  @Test
  @DisplayName("ai가 없어도 사이트 분류어는 categories로 넣는다")
  void 분류어_ai없음() {
    PostCollectedEvent event =
        mapper.toEvent(row("2026-03-18T00:00", null, List.of("Engineering", "인기글")), 5L, null);

    assertThat(event.categories()).containsExactly("Engineering", "인기글");
    assertThat(event.field()).isNull();
  }

  @Test
  @DisplayName("field가 있어도 categories는 사이트 분류어. 빈 값·공백·중복은 뺀다")
  void 분류어는_field와_별개() {
    ImportedPost.Ai ai = new ImportedPost.Ai(true, List.of("Kafka"), "Backend", "중급", "요지");
    PostCollectedEvent event =
        mapper.toEvent(
            row("2026-03-18T00:00", ai, Arrays.asList(" Engineering ", "Engineering", "", null)),
            5L,
            null);

    assertThat(event.categories()).containsExactly("Engineering");
    assertThat(event.field()).isEqualTo("Backend");
  }

  @Test
  @DisplayName("companyId·url을 그대로 넣는다")
  void 기본필드() {
    PostCollectedEvent event = mapper.toEvent(row("2026-03-18T00:00", null), 5L, null);

    assertThat(event.companyId()).isEqualTo(5L);
    assertThat(event.url()).isEqualTo("https://toss.tech/a");
  }
}
