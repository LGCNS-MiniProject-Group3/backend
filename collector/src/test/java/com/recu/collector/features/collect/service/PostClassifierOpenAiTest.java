package com.recu.collector.features.collect.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.recu.collector.features.collect.domain.dto.CollectedPost;
import com.recu.collector.features.collect.domain.dto.ImportedPost;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * 실제 OpenAI에 글 한 편을 분류시켜 칸이 채워지는지 본다. 키는 환경 변수로만 받는다.
 *
 * <pre>OPENAI_API_KEY=... ./gradlew :collector:test -Pnetwork --tests '*PostClassifierOpenAiTest'
 * </pre>
 */
@Tag("network")
class PostClassifierOpenAiTest {

  @Test
  void 실제_OpenAI로_분류한다() {
    String apiKey = System.getenv("OPENAI_API_KEY");
    assumeTrue(apiKey != null && !apiKey.isBlank(), "OPENAI_API_KEY 가 없어 건너뜀");

    PostClassifier classifier =
        new PostClassifier(RestClient.builder(), apiKey, "gpt-4o-mini", 7000, 0);
    CollectedPost post =
        new CollectedPost(
            "토스",
            "1",
            "Kafka로 주문 이벤트를 나눈 이야기",
            "https://example.com/1",
            LocalDateTime.now(),
            "",
            "주문 서비스가 커지면서 결제와 배송을 Kafka 토픽으로 나눴습니다. Spring Boot 컨슈머에서 재시도와 DLT를 설정하고,"
                + " 파티션 키를 주문 번호로 잡아 순서를 지켰습니다.",
            90,
            false,
            List.of(),
            List.of());

    ImportedPost.Ai ai = classifier.classify(post).orElseThrow();

    assertThat(ai.is_tech()).isTrue();
    assertThat(ai.field()).isNotNull();
    assertThat(ai.level()).isNotNull();
    assertThat(ai.core_techs()).isNotEmpty();
    assertThat(ai.summary()).isNotBlank();
  }
}
