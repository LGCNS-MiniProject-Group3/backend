package com.recu.content.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.recu.common.kafka.EventEnvelope;
import com.recu.common.kafka.KafkaEventPublisher;
import com.recu.common.kafka.KafkaTopics;
import com.recu.common.kafka.event.GuideCreatedEvent;
import com.recu.common.kafka.event.ProfileUpdatedEvent;
import com.recu.content.database.entity.BlogPostEntity;
import com.recu.content.database.entity.CompanyEntity;
import com.recu.content.database.repository.BlogPostRepository;
import com.recu.content.database.repository.CompanyRepository;
import com.recu.content.database.repository.UserGuideFlagRepository;
import com.recu.content.features.company.service.CompanyQueryService;
import com.recu.content.features.guideflag.service.UserGuideFlagService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/** 같은 수신 계약을 내장 Kafka/H2와 명시적으로 선택한 로컬 Kafka/MariaDB에서 검증한다. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {"spring.kafka.listener.auto-startup=true", "spring.jpa.open-in-view=false"})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class AbstractGuideFlagListenerTest {
  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Autowired KafkaEventPublisher publisher;
  @Autowired KafkaTemplate<String, Object> kafka;
  @Autowired KafkaListenerEndpointRegistry listeners;
  @Autowired KafkaProperties kafkaProperties;
  @Autowired UserGuideFlagRepository flags;
  @Autowired UserGuideFlagService service;
  @Autowired CompanyQueryService queries;
  @Autowired CompanyRepository companies;
  @Autowired BlogPostRepository posts;

  private AdminClient admin;
  private Long userId;
  private Long otherUserId;
  private CompanyEntity company;
  private BlogPostEntity post;

  @BeforeEach
  void 준비() {
    listeners.getListenerContainers().forEach(c -> ContainerTestUtils.waitForAssignment(c, 3));
    admin =
        AdminClient.create(
            Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers()));
    userId = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE - 1);
    otherUserId = userId + 1;
    company =
        companies.save(
            CompanyEntity.builder()
                .name("수신 테스트 기업")
                .feed_url("https://example.com/feed")
                .feed_type("FEED_FULL")
                .build());
    post =
        posts.save(
            BlogPostEntity.builder()
                .company(company)
                .url("https://example.com/" + UUID.randomUUID())
                .title("수신 테스트 글")
                .published_at(LocalDateTime.now())
                .content_html("<p>본문</p>")
                .content_text("본문")
                .char_count(2)
                .has_code(false)
                .collected_at(LocalDateTime.now())
                .is_tech(true)
                .build());
  }

  @AfterEach
  void 정리() {
    // 테스트에서 만든 행만 정리하며 DB 전체를 비우지 않는다.
    if (userId != null) service.clearFlagsForUser(userId);
    if (otherUserId != null) service.clearFlagsForUser(otherUserId);
    if (post != null) posts.deleteById(post.getBlog_post_id());
    if (company != null) companies.deleteById(company.getCompany_id());
    if (admin != null) admin.close(Duration.ofSeconds(5));
  }

  @Test
  void C를_수신하면_기존_조회에서_가이드_표시가_보인다() throws Exception {
    assertGuide(false);

    consumed(
        publisher
            .publishGuideCreated(new GuideCreatedEvent(userId, postId()))
            .get(10, TimeUnit.SECONDS));

    assertThat(flags.findGuidedPostIds(userId, List.of(postId()))).containsExactly(postId());
    assertGuide(true);
  }

  @Test
  void 동일한_C_메시지를_두번_수신해도_표시가_중복되지_않는다() throws Exception {
    EventEnvelope<GuideCreatedEvent> message =
        EventEnvelope.of(
            KafkaTopics.EVENT_TYPE_GUIDE_CREATED, new GuideCreatedEvent(userId, postId()));
    consumed(
        kafka
            .send(KafkaTopics.TOPIC_GUIDE_CREATED, userId.toString(), message)
            .get(10, TimeUnit.SECONDS));
    consumed(
        kafka
            .send(KafkaTopics.TOPIC_GUIDE_CREATED, userId.toString(), message)
            .get(10, TimeUnit.SECONDS));

    assertThat(flags.findAll().stream().filter(f -> f.getId().getUserId().equals(userId)).count())
        .isEqualTo(1);
    assertGuide(true);
  }

  @Test
  void B를_수신하면_해당_사용자_표시만_모두_삭제한다() throws Exception {
    service.addFlag(userId, postId());
    service.addFlag(userId, postId() + 1);
    service.addFlag(otherUserId, postId());

    consumed(
        publisher.publishProfileUpdated(new ProfileUpdatedEvent(userId)).get(10, TimeUnit.SECONDS));

    assertThat(flags.findGuidedPostIds(userId, List.of(postId(), postId() + 1))).isEmpty();
    assertThat(flags.findGuidedPostIds(otherUserId, List.of(postId()))).containsExactly(postId());
    assertGuide(false);
  }

  @Test
  void 표시가_없는_사용자의_B를_반복_수신해도_다른_표시는_유지한다() throws Exception {
    service.addFlag(otherUserId, postId());

    consumed(
        publisher.publishProfileUpdated(new ProfileUpdatedEvent(userId)).get(10, TimeUnit.SECONDS));
    consumed(
        publisher.publishProfileUpdated(new ProfileUpdatedEvent(userId)).get(10, TimeUnit.SECONDS));

    assertThat(flags.findGuidedPostIds(otherUserId, List.of(postId()))).containsExactly(postId());
    assertGuide(false);
  }

  @Test
  void C_B_C를_처리한_뒤_조회_표시가_추가_삭제_재추가된다() throws Exception {
    consumed(
        publisher
            .publishGuideCreated(new GuideCreatedEvent(userId, postId()))
            .get(10, TimeUnit.SECONDS));
    assertGuide(true);
    consumed(
        publisher.publishProfileUpdated(new ProfileUpdatedEvent(userId)).get(10, TimeUnit.SECONDS));
    assertGuide(false);
    consumed(
        publisher
            .publishGuideCreated(new GuideCreatedEvent(userId, postId()))
            .get(10, TimeUnit.SECONDS));
    assertGuide(true);
  }

  private Long postId() {
    return post.getBlog_post_id();
  }

  private void assertGuide(boolean expected) {
    assertThat(queries.getCompanyPosts(company.getCompany_id(), userId))
        .singleElement()
        .satisfies(p -> assertThat(p.hasGuide()).isEqualTo(expected));
  }

  private void consumed(SendResult<String, Object> sent) {
    TopicPartition partition =
        new TopicPartition(sent.getRecordMetadata().topic(), sent.getRecordMetadata().partition());
    // DB 변화가 없는 삭제나 중복 수신도 실제 처리를 마친 뒤 검증하도록 커밋된 오프셋을 기다린다.
    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              Map<TopicPartition, OffsetAndMetadata> offsets =
                  admin
                      .listConsumerGroupOffsets("content")
                      .partitionsToOffsetAndMetadata()
                      .get(5, TimeUnit.SECONDS);
              assertThat(offsets.get(partition)).isNotNull();
              assertThat(offsets.get(partition).offset())
                  .isGreaterThan(sent.getRecordMetadata().offset());
            });
  }
}
