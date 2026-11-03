package com.recu.bookmark.messaging;

import com.recu.bookmark.features.guideflag.service.UserGuideFlagService;
import com.recu.common.kafka.EventEnvelope;
import com.recu.common.kafka.KafkaTopics;
import com.recu.common.kafka.event.ProfileUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** B recu.profile.updated 수신 → 그 사용자의 "가이드 있음" 표시 모두 삭제. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProfileUpdatedListener {
  private final UserGuideFlagService guideFlags;

  @KafkaListener(
      id = "bookmarkProfileUpdated",
      topics = KafkaTopics.TOPIC_PROFILE_UPDATED,
      groupId = "${spring.application.name}")
  public void onProfileUpdated(
      EventEnvelope<ProfileUpdatedEvent> message,
      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
      @Header(KafkaHeaders.RECEIVED_KEY) String key) {
    guideFlags.clearFlagsForUser(message.payload().userId());
    log.info("사용자 가이드 표시 삭제: topic={}, key={}, eventId={}", topic, key, message.eventId());
  }
}
