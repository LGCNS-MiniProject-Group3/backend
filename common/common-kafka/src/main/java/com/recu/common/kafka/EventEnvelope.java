package com.recu.common.kafka;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * 모든 Kafka 업무 메시지를 감싸는 공통 메시지 형식 (design.md 6장의 공통 봉투).
 *
 * @param eventId 메시지마다 새로 만드는 UUID. V1은 담기만 하고 V2에서 중복 확인에 쓴다
 * @param eventType 메시지 종류. {@link KafkaTopics}의 EVENT_TYPE_* 값
 * @param occurredAt 메시지를 만든 시각 (한국 시간)
 * @param payload 메시지 내용물. event 패키지의 DTO 3개 중 하나
 */
public record EventEnvelope<T>(
    String eventId, String eventType, OffsetDateTime occurredAt, T payload) {

  private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

  /** eventId와 occurredAt을 채워 새 메시지를 만든다. */
  public static <T> EventEnvelope<T> of(String eventType, T payload) {
    return new EventEnvelope<>(
        UUID.randomUUID().toString(), eventType, OffsetDateTime.now(KOREA), payload);
  }
}
