package com.recu.collector.features.collect.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recu.collector.features.collect.domain.dto.ImportedPost;
import com.recu.common.kafka.event.PostCollectedEvent;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PostCollectedEventMapperTest {

  private final PostCollectedEventMapper mapper =
      new PostCollectedEventMapper(new SectionSplitter());

  private ImportedPost row(String publishedAt, ImportedPost.Ai ai) {
    return new ImportedPost(
        "토스",
        "ext-1",
        "제목",
        "https://toss.tech/a",
        publishedAt,
        10,
        true,
        List.of(),
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
  @DisplayName("대소문자만 다른 태그는 하나로, rank는 작은 쪽")
  void 태그_중복_rank() {
    // resolver=null → raw 이름 그대로 (Kafka / kafka)
    ImportedPost.Ai ai =
        new ImportedPost.Ai(true, List.of("Kafka", "kafka", "Redis"), "Backend", "중급", "요지");

    PostCollectedEvent event = mapper.toEvent(row("2026-03-18T00:00", ai), 5L, null);

    assertThat(event.categories()).containsExactly("Backend");
    assertThat(event.tags()).hasSize(2);
    assertThat(event.tags().get(0).name()).isEqualToIgnoringCase("Kafka");
    assertThat(event.tags().get(0).rank()).isEqualTo(1); // 첫 Kafka
    assertThat(event.tags().get(1).name()).isEqualTo("Redis");
    assertThat(event.tags().get(1).rank()).isEqualTo(3); // 세 번째 등장 위치
  }

  @Test
  @DisplayName("companyId·url을 그대로 넣는다")
  void 기본필드() {
    PostCollectedEvent event = mapper.toEvent(row("2026-03-18T00:00", null), 5L, null);

    assertThat(event.companyId()).isEqualTo(5L);
    assertThat(event.url()).isEqualTo("https://toss.tech/a");
  }
}
