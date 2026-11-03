package com.recu.content.messaging;

import com.recu.common.kafka.EventEnvelope;
import com.recu.common.kafka.KafkaTopics;
import com.recu.common.kafka.event.PostCollectedEvent;
import com.recu.content.features.ingest.service.PostCollectedIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** A 수신은 기존 저장 트랜잭션에 위임하며, 저장 실패는 공통 수신 계층으로 전파한다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PostCollectedListener {
  private final PostCollectedIngestService ingestService;

  @KafkaListener(
      id = "contentPostCollected",
      topics = KafkaTopics.TOPIC_POST_COLLECTED,
      groupId = "${spring.application.name}")
  public void onPostCollected(
      EventEnvelope<PostCollectedEvent> message,
      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
      @Header(KafkaHeaders.RECEIVED_KEY) String key) {
    boolean saved = ingestService.ingest(message.payload());
    log.info(
        "글 수신 처리: topic={}, key={}, eventId={}, result={}",
        topic,
        key,
        message.eventId(),
        saved ? "저장" : "중복 URL 건너뛰기");
  }
}
