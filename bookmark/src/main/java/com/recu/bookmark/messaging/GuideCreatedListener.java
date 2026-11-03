package com.recu.bookmark.messaging;

import com.recu.bookmark.features.guideflag.service.UserGuideFlagService;
import com.recu.common.kafka.EventEnvelope;
import com.recu.common.kafka.KafkaTopics;
import com.recu.common.kafka.event.GuideCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** C recu.guide.created 수신 → "가이드 있음" 표시 추가. */
@Component
@RequiredArgsConstructor
@Slf4j
public class GuideCreatedListener {
  private final UserGuideFlagService guideFlags;

  // id를 주면 기본으로 id가 그룹 이름이 돼서, 그룹은 모듈 이름(bookmark)으로 따로 적는다
  @KafkaListener(
      id = "bookmarkGuideCreated",
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
