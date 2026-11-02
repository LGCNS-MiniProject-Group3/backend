package com.recu.common.kafka;

import static com.recu.common.kafka.KafkaTopics.EVENT_TYPE_GUIDE_CREATED;
import static com.recu.common.kafka.KafkaTopics.EVENT_TYPE_POST_COLLECTED;
import static com.recu.common.kafka.KafkaTopics.EVENT_TYPE_PROFILE_UPDATED;
import static com.recu.common.kafka.KafkaTopics.TOPIC_GUIDE_CREATED;
import static com.recu.common.kafka.KafkaTopics.TOPIC_POST_COLLECTED;
import static com.recu.common.kafka.KafkaTopics.TOPIC_PROFILE_UPDATED;

import com.recu.common.kafka.event.GuideCreatedEvent;
import com.recu.common.kafka.event.PostCollectedEvent;
import com.recu.common.kafka.event.ProfileUpdatedEvent;
import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * 업무 메시지 3종을 보내는 도구. 토픽, 키, eventType을 design.md 6장대로 맞춰 준다.
 *
 * <p>서비스는 저장이 끝난 뒤 이 도구로 보낸다. 보내기 결과를 어떻게 다룰지는 부르는 쪽이 정한다.
 */
public class KafkaEventPublisher {

  private final KafkaTemplate<String, Object> kafkaTemplate;

  public KafkaEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  /** A. 키는 url. */
  public CompletableFuture<SendResult<String, Object>> publishPostCollected(
      PostCollectedEvent event) {
    return send(TOPIC_POST_COLLECTED, event.url(), EVENT_TYPE_POST_COLLECTED, event);
  }

  /** B. 키는 userId. */
  public CompletableFuture<SendResult<String, Object>> publishProfileUpdated(
      ProfileUpdatedEvent event) {
    return send(TOPIC_PROFILE_UPDATED, key(event.userId()), EVENT_TYPE_PROFILE_UPDATED, event);
  }

  /** C. 키는 userId. */
  public CompletableFuture<SendResult<String, Object>> publishGuideCreated(
      GuideCreatedEvent event) {
    return send(TOPIC_GUIDE_CREATED, key(event.userId()), EVENT_TYPE_GUIDE_CREATED, event);
  }

  private CompletableFuture<SendResult<String, Object>> send(
      String topic, String key, String eventType, Object payload) {
    return kafkaTemplate.send(topic, key, EventEnvelope.of(eventType, payload));
  }

  private static String key(Long userId) {
    return String.valueOf(userId);
  }
}
