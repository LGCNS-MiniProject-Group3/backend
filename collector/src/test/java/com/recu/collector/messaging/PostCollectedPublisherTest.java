package com.recu.collector.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recu.common.kafka.KafkaEventPublisher;
import com.recu.common.kafka.event.PostCollectedEvent;
import com.recu.common.kafka.event.PostCollectedEvent.Tag;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostCollectedPublisherTest {

  @Mock KafkaEventPublisher kafkaEventPublisher;
  @InjectMocks PostCollectedPublisher publisher;

  @BeforeEach
  void setUp() {
    // 진짜 Kafka 없이 “보내기 성공”으로 위장
    when(kafkaEventPublisher.publishPostCollected(any()))
        .thenReturn(CompletableFuture.completedFuture(null));
  }

  @Test
  @DisplayName("publish 성공 시 true, publishPostCollected를 1번 부르고 인자의 url이 키로 쓰일 값이다")
  void 발행_성공_url확인() {
    PostCollectedEvent event = sample("https://toss.tech/a");

    boolean ok = publisher.publish(event);

    assertThat(ok).isTrue();

    ArgumentCaptor<PostCollectedEvent> captor = ArgumentCaptor.forClass(PostCollectedEvent.class);
    verify(kafkaEventPublisher, times(1)).publishPostCollected(captor.capture());
    assertThat(captor.getValue().url()).isEqualTo("https://toss.tech/a");
  }

  @Test
  @DisplayName("두 번 부르면 publishPostCollected도 두 번")
  void 발행_횟수() {
    publisher.publish(sample("https://toss.tech/a"));
    publisher.publish(sample("https://toss.tech/b"));

    ArgumentCaptor<PostCollectedEvent> captor = ArgumentCaptor.forClass(PostCollectedEvent.class);
    verify(kafkaEventPublisher, times(2)).publishPostCollected(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(PostCollectedEvent::url)
        .containsExactly("https://toss.tech/a", "https://toss.tech/b");
  }

  private static PostCollectedEvent sample(String url) {
    return new PostCollectedEvent(
        5L,
        url,
        null,
        "제목",
        OffsetDateTime.of(2026, 3, 18, 0, 0, 0, 0, ZoneOffset.ofHours(9)),
        "<p>html</p>",
        "text",
        true,
        true,
        "Backend",
        "중급",
        "요지",
        List.of("Backend"),
        List.of(new Tag("Kafka", 1)),
        List.of()); // sections []
  }
}
