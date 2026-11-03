package com.recu.content.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.recu.common.kafka.EventEnvelope;
import com.recu.common.kafka.KafkaTopics;
import com.recu.common.kafka.event.PostCollectedEvent;
import com.recu.common.kafka.event.PostCollectedEvent.Section;
import com.recu.common.kafka.event.PostCollectedEvent.Tag;
import com.recu.content.database.entity.CompanyEntity;
import com.recu.content.database.entity.TechTagEntity;
import com.recu.content.database.repository.BlogPostRepository;
import com.recu.content.database.repository.CompanyRepository;
import com.recu.content.database.repository.TechTagRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/** 같은 수신 계약을 내장·외부 환경에서 검증하고 테스트에서 만든 데이터만 정리한다. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {"spring.kafka.listener.auto-startup=true", "spring.jpa.open-in-view=false"})
@ActiveProfiles("test")
@DirtiesContext
@ExtendWith(OutputCaptureExtension.class)
abstract class AbstractPostCollectedListenerTest {
  @Autowired KafkaTemplate<String, Object> kafka;
  @Autowired KafkaListenerEndpointRegistry listeners;
  @Autowired KafkaProperties kafkaProperties;
  @Autowired CompanyRepository companies;
  @Autowired BlogPostRepository posts;
  @Autowired TechTagRepository techTags;
  @Autowired JdbcTemplate jdbc;

  private AdminClient admin;
  private Long companyId;
  private String url;
  private String knownTech;
  private String newTech;

  @BeforeEach
  void 준비() {
    listeners.getListenerContainers().forEach(c -> ContainerTestUtils.waitForAssignment(c, 3));
    admin =
        AdminClient.create(
            Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers()));
    url = "https://example.com/issue74/" + UUID.randomUUID();
    knownTech = "known-" + UUID.randomUUID();
    newTech = "new-" + UUID.randomUUID();
    companyId =
        companies
            .save(
                CompanyEntity.builder()
                    .name("A 수신 테스트 기업")
                    .feed_url("https://example.com/feed")
                    .feed_type("FEED_FULL")
                    .build())
            .getCompany_id();
    techTags.save(TechTagEntity.builder().name(knownTech).selectable(true).build());
  }

  @AfterEach
  void 정리() {
    if (url != null)
      posts
          .findByUrl(url)
          .ifPresent(
              post -> {
                for (String table :
                    List.of("blog_post_section", "blog_post_category", "blog_post_tag")) {
                  jdbc.update(
                      "delete from " + table + " where blog_post_id = ?", post.getBlog_post_id());
                }
                posts.deleteById(post.getBlog_post_id());
              });
    if (knownTech != null) techTags.findByName(knownTech).ifPresent(techTags::delete);
    if (newTech != null) techTags.findByName(newTech).ifPresent(techTags::delete);
    if (companyId != null) companies.deleteById(companyId);
    if (admin != null) admin.close(Duration.ofSeconds(5));
  }

  @Test
  void A를_수신하면_본문과_구간_분류어_기술과_rank를_저장한다() throws Exception {
    var message = message(companyId, defaultSections());
    consumed(send(message));

    var event = message.payload();
    var post = posts.findByUrl(url).orElseThrow();
    assertThat(post.getContent_html()).isEqualTo(event.contentHtml());
    assertThat(post.getContent_text()).isEqualTo(event.contentText());
    assertThat(post.getPublished_at()).isEqualTo(LocalDateTime.of(2026, 11, 3, 1, 30));
    assertThat(
            jdbc.queryForObject(
                "select company_id from blog_post where blog_post_id = ?",
                Long.class,
                post.getBlog_post_id()))
        .isEqualTo(companyId);
    assertThat(
            jdbc.query(
                "select seq, heading, level, content_text from blog_post_section "
                    + "where blog_post_id = ? order by seq",
                (rs, i) ->
                    new Section(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4)),
                post.getBlog_post_id()))
        .containsExactlyElementsOf(event.sections());
    assertThat(
            jdbc.queryForList(
                "select name from blog_post_category where blog_post_id = ?",
                String.class,
                post.getBlog_post_id()))
        .containsExactly("Backend");
    assertThat(
            jdbc.query(
                "select t.name, p.tag_rank from blog_post_tag p join tech_tag t "
                    + "on t.tech_tag_id = p.tech_tag_id where p.blog_post_id = ? order by p.tag_rank",
                (rs, i) -> new Tag(rs.getString(1), rs.getInt(2)),
                post.getBlog_post_id()))
        .containsExactly(new Tag(newTech, 1), new Tag(knownTech, 3));
    assertThat(techTags.findByName(knownTech).orElseThrow().getSelectable()).isTrue();
    assertThat(techTags.findByName(newTech).orElseThrow().getSelectable()).isFalse();
  }

  @Test
  void 동일_A를_다시_수신하면_성공적으로_건너뛰고_데이터를_추가하지_않는다(CapturedOutput output) throws Exception {
    var message = message(companyId, defaultSections());
    consumed(send(message));
    var before = counts();
    consumed(send(message));

    assertThat(counts()).isEqualTo(before);
    // 오프셋과 행 수만 보면 실패 후 건너뛴 메시지도 통과하므로, 실제 성공 반환 뒤의 로그도 확인한다.
    assertThat(output.getOut())
        .contains("key=" + url + ", eventId=" + message.eventId() + ", result=중복 URL 건너뛰기");
  }

  @Test
  void 구간_저장이_실패하면_새_기술까지_롤백하고_성공_로그를_남기지_않는다(CapturedOutput output) throws Exception {
    var before = counts();
    var message =
        message(
            companyId,
            List.of(new Section(1, "첫 구간", "h2", "본문"), new Section(1, "중복 순번", "h3", "실패")));
    consumed(send(message));

    assertThat(counts()).isEqualTo(before);
    assertThat(posts.findByUrl(url)).isEmpty();
    assertThat(techTags.findByName(newTech)).isEmpty();
    assertThat(output.getOut())
        .doesNotContain("key=" + url + ", eventId=" + message.eventId() + ", result=");
  }

  @Test
  void 없는_기업의_A는_기업_생성이나_부분_저장_없이_실패한다() throws Exception {
    var before = counts();
    consumed(send(message(-1L, defaultSections())));
    assertThat(counts()).isEqualTo(before);
    assertThat(posts.findByUrl(url)).isEmpty();
  }

  @Test
  void 긴_분류어와_요약이_있는_A도_원본_규칙으로_보정해_관련_데이터와_함께_저장한다() throws Exception {
    String prefix = "분".repeat(100);
    var message =
        message(
            companyId,
            defaultSections(),
            " \t" + "요".repeat(500) + " \n",
            Arrays.asList(
                null, "", " \t ", " Backend ", "Backend", " " + prefix + "첫번째 ", prefix + "두번째"));
    consumed(send(message));

    var post = posts.findByUrl(url).orElseThrow();
    assertThat(post.getSummary()).isEqualTo("요".repeat(300));
    assertThat(post.getContent_html()).isEqualTo(message.payload().contentHtml());
    assertThat(post.getContent_text()).isEqualTo(message.payload().contentText());
    assertThat(
            jdbc.queryForList(
                "select name from blog_post_category where blog_post_id = ?",
                String.class,
                post.getBlog_post_id()))
        .containsExactlyInAnyOrder("Backend", prefix);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from blog_post_section where blog_post_id = ?",
                Long.class,
                post.getBlog_post_id()))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from blog_post_tag where blog_post_id = ?",
                Long.class,
                post.getBlog_post_id()))
        .isEqualTo(2);
  }

  private List<Long> counts() {
    return Stream.of(
            "company",
            "blog_post",
            "blog_post_section",
            "blog_post_category",
            "blog_post_tag",
            "tech_tag")
        .map(table -> jdbc.queryForObject("select count(*) from " + table, Long.class))
        .toList();
  }

  private EventEnvelope<PostCollectedEvent> message(Long company, List<Section> sections) {
    return message(company, sections, "요약", List.of("Backend", "Backend"));
  }

  private EventEnvelope<PostCollectedEvent> message(
      Long company, List<Section> sections, String summary, List<String> categories) {
    return EventEnvelope.of(
        KafkaTopics.EVENT_TYPE_POST_COLLECTED,
        new PostCollectedEvent(
            company,
            url,
            "issue74",
            "A 수신 테스트",
            OffsetDateTime.parse("2026-11-02T16:30:00Z"),
            "<h2>첫 구간</h2><p> 본문 </p>",
            " 본문\n그대로 ",
            true,
            true,
            "Backend",
            "중급",
            summary,
            categories,
            List.of(new Tag(knownTech, 3), new Tag(newTech, 1)),
            sections));
  }

  private List<Section> defaultSections() {
    return List.of(new Section(2, "첫 구간", "h2", " 본문\n"), new Section(9, "다음 구간", "h3", "다음 본문"));
  }

  private SendResult<String, Object> send(EventEnvelope<PostCollectedEvent> message)
      throws Exception {
    return kafka.send(KafkaTopics.TOPIC_POST_COLLECTED, url, message).get(10, TimeUnit.SECONDS);
  }

  private void consumed(SendResult<String, Object> sent) {
    var partition =
        new TopicPartition(sent.getRecordMetadata().topic(), sent.getRecordMetadata().partition());
    // 실패 사례에서는 공통 컨테이너의 오류 처리가 끝난 뒤 롤백 결과를 확인한다.
    await()
        .atMost(Duration.ofSeconds(30))
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
