package com.recu.guide.messaging;

import com.recu.common.kafka.KafkaEventPublisher;
import com.recu.common.kafka.event.GuideCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * C recu.guide.created 발행 (design.md 6장). 새 가이드를 저장한 뒤에 부른다.
 *
 * <p>보낸 결과를 기다리지 않는다. 실패해도 가이드 응답은 그대로 두고 로그만 남긴다 (재시도는 V2).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuideCreatedPublisher {

  private final KafkaEventPublisher kafkaEventPublisher;

  public void publish(Long userId, Long postId) {
    try {
      kafkaEventPublisher
          .publishGuideCreated(new GuideCreatedEvent(userId, postId))
          .whenComplete(
              (result, exception) -> {
                if (exception != null) {
                  logFailure(userId, postId, exception);
                }
              });
    } catch (Exception exception) {
      // 보내기 전에 바로 실패해도(직렬화 오류 등) 가이드 응답에는 영향을 주지 않는다
      logFailure(userId, postId, exception);
    }
  }

  private static void logFailure(Long userId, Long postId, Throwable exception) {
    log.error("GUIDE_CREATED publish failed. userId={}, postId={}", userId, postId, exception);
  }
}
