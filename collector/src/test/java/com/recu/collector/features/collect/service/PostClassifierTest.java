package com.recu.collector.features.collect.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recu.collector.features.collect.domain.dto.CollectedPost;
import com.recu.collector.features.collect.domain.dto.ImportedPost;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 모델 답을 표 칸 값으로 옮기는 규칙. 실제 호출은 하지 않는다. */
class PostClassifierTest {

  private final PostClassifier classifier = new PostClassifier(null, "m", 10, 0, millis -> {});

  @Test
  void 허용값_안의_답은_그대로_옮긴다() throws Exception {
    ImportedPost.Ai ai =
        classifier
            .parse(
                """
                {"is_tech": true, "field": "Backend", "level": "중급",
                 "core_techs": ["Kafka", "Spring Boot"], "summary": "카프카로 주문을 나눈 이야기"}
                """)
            .orElseThrow();

    assertThat(ai.is_tech()).isTrue();
    assertThat(ai.field()).isEqualTo("Backend");
    assertThat(ai.level()).isEqualTo("중급");
    assertThat(ai.core_techs()).containsExactly("Kafka", "Spring Boot");
    assertThat(ai.summary()).isEqualTo("카프카로 주문을 나눈 이야기");
  }

  @Test
  void 허용값_밖이면_그_칸만_비우고_기술은_세_개까지만_받는다() throws Exception {
    ImportedPost.Ai ai =
        classifier
            .parse(
                """
                {"is_tech": "yes", "field": "DevOps", "level": "초급",
                 "core_techs": ["A", "B", "A", "C", "D"], "summary": " "}
                """)
            .orElseThrow();

    assertThat(ai.is_tech()).isNull();
    assertThat(ai.field()).isNull();
    assertThat(ai.level()).isNull();
    assertThat(ai.core_techs()).containsExactly("A", "B", "C");
    assertThat(ai.summary()).isNull();
  }

  @Test
  void 요지는_앞뒤_공백을_지우고_150자까지_받는다() throws Exception {
    String summary = "가".repeat(150);

    ImportedPost.Ai ai = classifier.parse(answerWithSummary("  " + summary + "  ")).orElseThrow();

    assertThat(ai.summary()).isEqualTo(summary);
  }

  @Test
  void 요지가_150자를_넘으면_요지만_비운다() throws Exception {
    ImportedPost.Ai ai = classifier.parse(answerWithSummary("가".repeat(151))).orElseThrow();

    assertThat(ai.summary()).isNull();
    assertThat(ai.field()).isEqualTo("Backend");
    assertThat(ai.level()).isEqualTo("중급");
    assertThat(ai.core_techs()).containsExactly("Kafka");
  }

  @Test
  void 본문은_설정한_글자_수까지만_넣는다() {
    CollectedPost post =
        new CollectedPost(
            "토스",
            "1",
            "제목",
            "https://a.com/1",
            LocalDateTime.now(),
            "",
            "0123456789ABCDEF",
            16,
            false,
            List.of(),
            List.of());

    String prompt = classifier.prompt(post);

    assertThat(prompt).contains("0123456789").doesNotContain("ABCDEF").contains("제목: 제목");
  }

  private static String answerWithSummary(String summary) {
    return """
        {"is_tech": true, "field": "Backend", "level": "중급",
         "core_techs": ["Kafka"], "summary": "%s"}
        """
        .formatted(summary);
  }
}
