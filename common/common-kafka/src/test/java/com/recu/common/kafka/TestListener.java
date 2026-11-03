package com.recu.common.kafka;

import com.recu.common.kafka.event.GuideCreatedEvent;
import com.recu.common.kafka.event.PostCollectedEvent;
import com.recu.common.kafka.event.ProfileUpdatedEvent;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** 테스트용 받는 쪽. 서비스의 Listener처럼 메서드 파라미터 타입만 적어서 받는다. */
@Component
class TestListener {

  final BlockingQueue<Received<PostCollectedEvent>> postCollected = new LinkedBlockingQueue<>();
  final BlockingQueue<Received<ProfileUpdatedEvent>> profileUpdated = new LinkedBlockingQueue<>();
  final BlockingQueue<Received<GuideCreatedEvent>> guideCreated = new LinkedBlockingQueue<>();
  final BlockingQueue<ConsumerRecord<String, String>> postCollectedRaw =
      new LinkedBlockingQueue<>();

  @KafkaListener(topics = KafkaTopics.TOPIC_POST_COLLECTED)
  void onPostCollected(
      EventEnvelope<PostCollectedEvent> message, @Header(KafkaHeaders.RECEIVED_KEY) String key) {
    postCollected.add(new Received<>(key, message));
  }

  @KafkaListener(topics = KafkaTopics.TOPIC_PROFILE_UPDATED)
  void onProfileUpdated(
      EventEnvelope<ProfileUpdatedEvent> message, @Header(KafkaHeaders.RECEIVED_KEY) String key) {
    profileUpdated.add(new Received<>(key, message));
  }

  @KafkaListener(topics = KafkaTopics.TOPIC_GUIDE_CREATED)
  void onGuideCreated(
      EventEnvelope<GuideCreatedEvent> message, @Header(KafkaHeaders.RECEIVED_KEY) String key) {
    guideCreated.add(new Received<>(key, message));
  }

  // 실제로 오간 JSON과 헤더를 보려고, 변환 없이 원본 레코드를 다른 그룹으로 한 번 더 읽는다
  @KafkaListener(topics = KafkaTopics.TOPIC_POST_COLLECTED, groupId = "raw-reader")
  void onPostCollectedRaw(ConsumerRecord<String, String> record) {
    postCollectedRaw.add(record);
  }

  record Received<T>(String key, EventEnvelope<T> message) {}
}
