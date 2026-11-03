package com.recu.collector.messaging;

import com.recu.common.kafka.KafkaEventPublisher;
import com.recu.common.kafka.event.PostCollectedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * A recu.post.collected 발행 (design.md 6장). 수집·분류가 끝난 글 1편을 보낸다.
 *
 * <p>키는 url이다(공통 모듈이 맞춤). 보내기 직전에 실패하면 false를 돌려 호출 쪽이 newPostCount에 넣지 않게 한다. 브로커 전송이 나중에 실패하면 로그만
 * 남기고, 재시도는 V2다.
 */
@Component
public class PostCollectedPublisher {

  private static final Logger log = LoggerFactory.getLogger(PostCollectedPublisher.class);

  private final KafkaEventPublisher kafkaEventPublisher;

  public PostCollectedPublisher(KafkaEventPublisher kafkaEventPublisher) {
    this.kafkaEventPublisher = kafkaEventPublisher;
  }

  /**
   * @return 보내기 요청이 접수되면 true, 직렬화 등으로 바로 실패하면 false
   */
  public boolean publish(PostCollectedEvent event) {
    try {
      kafkaEventPublisher
          .publishPostCollected(event)
          .whenComplete(
              (result, exception) -> {
                if (exception != null) {
                  logFailure(event, exception);
                }
              });
      return true;
    } catch (Exception exception) {
      logFailure(event, exception);
      return false;
    }
  }

  private static void logFailure(PostCollectedEvent event, Throwable exception) {
    log.error(
        "POST_COLLECTED publish failed. companyId={}, url={}",
        event.companyId(),
        event.url(),
        exception);
  }
}
