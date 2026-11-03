package com.recu.common.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.recu.common.kafka.TestListener.Received;
import com.recu.common.kafka.event.GuideCreatedEvent;
import com.recu.common.kafka.event.PostCollectedEvent;
import com.recu.common.kafka.event.PostCollectedEvent.Section;
import com.recu.common.kafka.event.PostCollectedEvent.Tag;
import com.recu.common.kafka.event.ProfileUpdatedEvent;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.context.EmbeddedKafka;

/** 서비스처럼 common-kafka만 의존한 앱에서 A·B·C를 내장 Kafka로 실제로 보내고 받아 본다. */
@SpringBootTest
@EmbeddedKafka
class CommonKafkaAutoConfigurationTest {

  private static final long TIMEOUT_SECONDS = 30;

  // JSON을 거치며 시간대 표기가 바뀔 수 있어, 시각은 같은 순간인지로 비교한다
  private static final Comparator<OffsetDateTime> SAME_INSTANT =
      Comparator.comparing(OffsetDateTime::toInstant);

  @Autowired KafkaEventPublisher publisher;
  @Autowired TestListener listener;
  @Autowired ProducerFactory<String, Object> producerFactory;

  @Test
  @DisplayName("A를 보내면 키는 url, eventType은 POST_COLLECTED, 내용물은 그대로 받는다")
  void postCollected() throws Exception {
    PostCollectedEvent sent = post("https://example.com/a", "<p>본문</p>");
    publisher.publishPostCollected(sent).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

    Received<PostCollectedEvent> received =
        take(listener.postCollected, r -> sent.url().equals(r.key()));
    assertThat(received.message().eventType()).isEqualTo(KafkaTopics.EVENT_TYPE_POST_COLLECTED);
    assertThat(received.message().eventId()).isNotBlank();
    assertThat(received.message().occurredAt()).isNotNull();
    assertThat(received.message().payload())
        .usingRecursiveComparison()
        .withComparatorForType(SAME_INSTANT, OffsetDateTime.class)
        .isEqualTo(sent);
  }

  @Test
  @DisplayName("B를 보내면 키는 userId, eventType은 PROFILE_UPDATED, 내용물은 그대로 받는다")
  void profileUpdated() throws Exception {
    ProfileUpdatedEvent sent = new ProfileUpdatedEvent(12L);
    publisher.publishProfileUpdated(sent).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

    Received<ProfileUpdatedEvent> received =
        take(listener.profileUpdated, r -> "12".equals(r.key()));
    assertThat(received.message().eventType()).isEqualTo(KafkaTopics.EVENT_TYPE_PROFILE_UPDATED);
    assertThat(received.message().payload()).isEqualTo(sent);
  }

  @Test
  @DisplayName("C를 보내면 키는 userId, eventType은 GUIDE_CREATED, 내용물은 그대로 받는다")
  void guideCreated() throws Exception {
    GuideCreatedEvent sent = new GuideCreatedEvent(13L, 128L);
    publisher.publishGuideCreated(sent).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

    Received<GuideCreatedEvent> received = take(listener.guideCreated, r -> "13".equals(r.key()));
    assertThat(received.message().eventType()).isEqualTo(KafkaTopics.EVENT_TYPE_GUIDE_CREATED);
    assertThat(received.message().payload()).isEqualTo(sent);
  }

  @Test
  @DisplayName("실제 JSON은 design.md 6장 모양이고, 자바 클래스 이름 헤더는 싣지 않는다")
  void wireFormat() throws Exception {
    PostCollectedEvent sent = post("https://example.com/wire", "<p>본문</p>");
    publisher.publishPostCollected(sent).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

    ConsumerRecord<String, String> raw =
        take(listener.postCollectedRaw, r -> sent.url().equals(r.key()));
    // Spring Kafka가 클래스 이름을 싣는 헤더 이름
    assertThat(raw.headers().lastHeader("__TypeId__")).isNull();
    assertThat(raw.value())
        .contains("\"eventType\":\"POST_COLLECTED\"")
        .contains("\"isTech\":true")
        .contains("\"hasCode\":true")
        .containsPattern("\"eventId\":\"[0-9a-f-]{36}\"")
        .containsPattern("\"occurredAt\":\"[^\"]+\\+09:00\"");
  }

  @Test
  @DisplayName("압축 전 1MB가 넘는 A도 보내는 쪽에서 막히지 않고 도착한다")
  void largePostCollected() throws Exception {
    String html = "<p>기술 블로그 본문</p>".repeat(60_000);
    assertThat(html.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(1024 * 1024);
    PostCollectedEvent sent = post("https://example.com/large", html);
    publisher.publishPostCollected(sent).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

    Received<PostCollectedEvent> received =
        take(listener.postCollected, r -> sent.url().equals(r.key()));
    assertThat(received.message().payload().contentHtml()).isEqualTo(html);
  }

  @Test
  @DisplayName("Kafka가 꺼져 있어도 발행은 최대 15초(max.block.ms)만 기다린다")
  void maxBlock() {
    assertThat(producerFactory.getConfigurationProperties())
        .containsEntry(ProducerConfig.MAX_BLOCK_MS_CONFIG, 15_000L);
  }

  private static PostCollectedEvent post(String url, String contentHtml) {
    return new PostCollectedEvent(
        5L,
        url,
        null,
        "제목",
        OffsetDateTime.of(2026, 3, 18, 0, 0, 0, 0, ZoneOffset.ofHours(9)),
        contentHtml,
        "본문",
        true,
        true,
        "Backend",
        "중급",
        "요약",
        List.of("Backend"),
        List.of(new Tag("Kafka", 1), new Tag("Redis", 2)),
        List.of(new Section(1, "소제목", "h2", "구간 본문")));
  }

  // 다른 테스트가 보낸 메시지는 건너뛰고, 조건에 맞는 메시지가 올 때까지 기다린다
  private static <T> T take(BlockingQueue<T> queue, Predicate<T> match) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
    while (System.nanoTime() < deadline) {
      T item = queue.poll(1, TimeUnit.SECONDS);
      if (item != null && match.test(item)) {
        return item;
      }
    }
    throw new AssertionError("메시지가 " + TIMEOUT_SECONDS + "초 안에 도착하지 않았다");
  }
}
