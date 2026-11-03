package com.recu.bookmark.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.recu.bookmark.features.guideflag.repository.UserGuideFlagRepository;
import com.recu.bookmark.features.guideflag.service.UserGuideFlagService;
import com.recu.common.kafka.EventEnvelope;
import com.recu.common.kafka.KafkaEventPublisher;
import com.recu.common.kafka.KafkaTopics;
import com.recu.common.kafka.event.GuideCreatedEvent;
import com.recu.common.kafka.event.ProfileUpdatedEvent;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/** 내장 Kafka로 B·C를 실제로 보내 bookmark의 가이드 표시 사본이 바뀌는지 본다. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "spring.kafka.listener.auto-startup=true")
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 3,
    topics = {KafkaTopics.TOPIC_PROFILE_UPDATED, KafkaTopics.TOPIC_GUIDE_CREATED},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
class GuideFlagListenerTest {

  private static final long USER_ID = 12L;
  private static final long OTHER_USER_ID = 99L;
  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Autowired KafkaEventPublisher publisher;
  @Autowired KafkaTemplate<String, Object> kafka;
  @Autowired KafkaListenerEndpointRegistry listeners;
  @Autowired UserGuideFlagService service;
  @Autowired UserGuideFlagRepository flags;

  @Value("${spring.kafka.bootstrap-servers}")
  String brokers;

  @Value("${spring.application.name}")
  String groupId;

  private AdminClient admin;

  @BeforeEach
  void setUp() {
    listeners.getListenerContainers().forEach(c -> ContainerTestUtils.waitForAssignment(c, 3));
    admin = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, brokers));
    flags.deleteAll();
  }

  @AfterEach
  void tearDown() {
    admin.close(Duration.ofSeconds(5));
  }

  @Test
  @DisplayName("C를 받으면 (userId, postId) 표시를 추가한다")
  void guideCreatedAddsFlag() throws Exception {
    consumed(
        publisher
            .publishGuideCreated(new GuideCreatedEvent(USER_ID, 128L))
            .get(10, TimeUnit.SECONDS));

    assertThat(service.guidedPostIds(USER_ID, List.of(128L))).containsExactly(128L);
  }

  @Test
  @DisplayName("같은 C를 두 번 받아도 한 행만 남는다")
  void sameGuideCreatedTwice() throws Exception {
    EventEnvelope<GuideCreatedEvent> message =
        EventEnvelope.of(
            KafkaTopics.EVENT_TYPE_GUIDE_CREATED, new GuideCreatedEvent(USER_ID, 128L));
    consumed(kafka.send(KafkaTopics.TOPIC_GUIDE_CREATED, "12", message).get(10, TimeUnit.SECONDS));
    consumed(kafka.send(KafkaTopics.TOPIC_GUIDE_CREATED, "12", message).get(10, TimeUnit.SECONDS));

    assertThat(flags.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("B를 받으면 그 사용자 표시만 모두 지운다")
  void profileUpdatedClearsOnlyThatUser() throws Exception {
    service.addFlag(USER_ID, 1L);
    service.addFlag(USER_ID, 2L);
    service.addFlag(OTHER_USER_ID, 1L);

    consumed(
        publisher
            .publishProfileUpdated(new ProfileUpdatedEvent(USER_ID))
            .get(10, TimeUnit.SECONDS));

    assertThat(service.guidedPostIds(USER_ID, List.of(1L, 2L))).isEmpty();
    assertThat(service.guidedPostIds(OTHER_USER_ID, List.of(1L))).containsExactly(1L);
  }

  // 표시가 그대로인 경우(중복·삭제)도 처리가 끝난 뒤 확인하려고, 커밋된 오프셋을 기다린다 (content #65와 같은 방식)
  private void consumed(SendResult<String, Object> sent) {
    TopicPartition partition =
        new TopicPartition(sent.getRecordMetadata().topic(), sent.getRecordMetadata().partition());
    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              Map<TopicPartition, OffsetAndMetadata> offsets =
                  admin
                      .listConsumerGroupOffsets(groupId)
                      .partitionsToOffsetAndMetadata()
                      .get(5, TimeUnit.SECONDS);
              assertThat(offsets.get(partition)).isNotNull();
              assertThat(offsets.get(partition).offset())
                  .isGreaterThan(sent.getRecordMetadata().offset());
            });
  }
}
