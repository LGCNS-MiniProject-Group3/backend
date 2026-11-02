package com.recu.content.messaging;

import com.recu.common.kafka.EventEnvelope;
import com.recu.common.kafka.KafkaTopics;
import com.recu.common.kafka.event.GuideCreatedEvent;
import com.recu.content.features.guideflag.service.UserGuideFlagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GuideCreatedListener {
  private final UserGuideFlagService guideFlags;

  @KafkaListener(
      id = "contentGuideCreated",
      topics = KafkaTopics.TOPIC_GUIDE_CREATED,
      groupId = "${spring.application.name}")
  public void onGuideCreated(
      EventEnvelope<GuideCreatedEvent> message,
      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
      @Header(KafkaHeaders.RECEIVED_KEY) String key) {
    GuideCreatedEvent event = message.payload();
    guideFlags.addFlag(event.userId(), event.postId());
    log.info("가이드 표시 추가: topic={}, key={}, eventId={}", topic, key, message.eventId());
  }
}
