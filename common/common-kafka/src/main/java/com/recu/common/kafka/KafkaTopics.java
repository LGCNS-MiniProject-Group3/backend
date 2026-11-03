package com.recu.common.kafka;

/**
 * Kafka 업무 메시지의 토픽 이름과 eventType (design.md 6장).
 *
 * <p>eventType은 토픽 이름의 뒤 두 단어를 UPPER_SNAKE_CASE로 옮긴 값이다.
 *
 * <p>서비스는 문자열을 직접 쓰지 않고 이 상수를 쓴다.
 */
public final class KafkaTopics {

  /** A. collector가 수집·분류를 끝낸 글 1편. 키는 url. */
  public static final String TOPIC_POST_COLLECTED = "recu.post.collected";

  /** B. user에서 프로필이 바뀜. 키는 userId. */
  public static final String TOPIC_PROFILE_UPDATED = "recu.profile.updated";

  /** C. guide가 가이드를 새로 저장함. 키는 userId. */
  public static final String TOPIC_GUIDE_CREATED = "recu.guide.created";

  public static final String EVENT_TYPE_POST_COLLECTED = "POST_COLLECTED";
  public static final String EVENT_TYPE_PROFILE_UPDATED = "PROFILE_UPDATED";
  public static final String EVENT_TYPE_GUIDE_CREATED = "GUIDE_CREATED";

  private KafkaTopics() {}
}
