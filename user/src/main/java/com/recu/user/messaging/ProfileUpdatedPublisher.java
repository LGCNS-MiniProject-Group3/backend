package com.recu.user.messaging;

import com.recu.common.kafka.KafkaEventPublisher;
import com.recu.common.kafka.event.ProfileUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * B recu.profile.updated 발행 (design.md 6장). content·bookmark가 받아 그 사용자의 가이드 표시를 지운다.
 *
 * <p>프로필 저장 트랜잭션이 커밋된 뒤에 보낸다(AFTER_COMMIT). 저장이 롤백되면 보내지 않는다. 보낸 결과는 기다리지 않고, 실패해도 프로필 응답은 그대로 두고
 * 로그만 남긴다 (재시도는 V2). guide의 C 발행과 같은 방식이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileUpdatedPublisher {

  private final KafkaEventPublisher kafkaEventPublisher;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void publish(ProfileSaved saved) {
    Long userId = saved.userId();
    try {
      kafkaEventPublisher
          .publishProfileUpdated(new ProfileUpdatedEvent(userId))
          .whenComplete(
              (result, exception) -> {
                if (exception != null) {
                  logFailure(userId, exception);
                }
              });
    } catch (Exception exception) {
      // 보내기 전에 바로 실패해도(직렬화 오류 등) 이미 저장된 프로필 응답에는 영향을 주지 않는다
      logFailure(userId, exception);
    }
  }

  private static void logFailure(Long userId, Throwable exception) {
    log.error("PROFILE_UPDATED publish failed. userId={}", userId, exception);
  }
}
