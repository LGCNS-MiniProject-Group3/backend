package com.recu.collector.features.collect.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recu.collector.client.ContentClient;
import com.recu.collector.features.collect.domain.dto.CollectedPost;
import com.recu.collector.features.collect.domain.dto.CompanyTarget;
import com.recu.collector.features.collect.domain.dto.ImportedPost;
import com.recu.collector.messaging.PostCollectedPublisher;
import com.recu.common.kafka.event.PostCollectedEvent;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** ⑥ URL 목록으로 이미 있는 글을 걸러 내는지 확인한다. */
@ExtendWith(MockitoExtension.class)
class WebCollectServiceTest {

  @Mock ContentClient contentClient;
  @Mock CollectService collectService;
  @Mock PostClassifier classifier;
  @Mock PostCollectedEventMapper mapper;
  @Mock PostCollectedPublisher publisher;

  @InjectMocks WebCollectService service;

  @Test
  @DisplayName("이미 저장된 URL은 분류·발행하지 않는다")
  void 이미_저장된_URL은_분류_발행하지_않는다() {
    CompanyTarget company = new CompanyTarget(1L, "토스", "FEED_FULL", "https://toss.tech/rss.xml");

    when(contentClient.getCollectedUrls(1L)).thenReturn(List.of("https://toss.tech/already"));

    LocalDateTime recent = LocalDateTime.now().minusDays(1);
    when(collectService.collectOne(any()))
        .thenReturn(
            List.of(
                post("https://toss.tech/already", recent), post("https://toss.tech/new", recent)));

    when(classifier.classify(any()))
        .thenReturn(
            Optional.of(new ImportedPost.Ai(true, List.of("Kafka"), "Backend", "중급", "요지")));
    when(mapper.toEvent(any(), any(), any())).thenReturn(dummyEvent("https://toss.tech/new"));
    when(publisher.publish(any())).thenReturn(true);

    WebCollectService.CompanyResult result = service.runOne(company);

    assertThat(result.newInWindow()).isEqualTo(1);
    assertThat(result.publishedCount()).isEqualTo(1);
    verify(classifier, times(1)).classify(any());
    verify(contentClient).getCollectedUrls(1L);
    verify(publisher, times(1)).publish(any());
  }

  @Test
  @DisplayName("발행이 한 번 실패하면 남은 글과 남은 기업은 보내지 않는다")
  void 발행_실패시_멈춘다() {
    CompanyTarget toss = new CompanyTarget(1L, "토스", "FEED_FULL", "https://toss.tech/rss.xml");
    CompanyTarget naver = new CompanyTarget(2L, "네이버", "FEED_FULL", "https://d2.naver.com/rss");

    when(contentClient.getCollectedUrls(1L)).thenReturn(List.of());
    LocalDateTime recent = LocalDateTime.now().minusDays(1);
    when(collectService.collectOne(any()))
        .thenReturn(
            List.of(
                post("https://toss.tech/a", recent),
                post("https://toss.tech/b", recent),
                post("https://toss.tech/c", recent)));
    when(classifier.classify(any())).thenReturn(Optional.empty());
    when(mapper.toEvent(any(), any(), any())).thenReturn(dummyEvent("https://toss.tech/a"));
    when(publisher.publish(any())).thenReturn(true, false);

    List<WebCollectService.CompanyResult> results = service.run(List.of(toss, naver));

    assertThat(results).hasSize(1);
    assertThat(results.get(0).publishedCount()).isEqualTo(1);
    assertThat(results.get(0).publishStopped()).isTrue();
    verify(publisher, times(2)).publish(any());
    verify(contentClient, never()).getCollectedUrls(2L);
  }

  private static CollectedPost post(String url, LocalDateTime publishedAt) {
    return new CollectedPost(
        "토스",
        url,
        "제목 " + url,
        url,
        publishedAt,
        "<h2>들어가며</h2><p>본문</p>",
        "들어가며 본문",
        7,
        false,
        List.of(new CollectedPost.Heading(1, "h2", "들어가며")),
        List.of("Backend"));
  }

  private static PostCollectedEvent dummyEvent(String url) {
    return new PostCollectedEvent(
        1L,
        url,
        null,
        "제목",
        OffsetDateTime.of(2026, 3, 18, 0, 0, 0, 0, ZoneOffset.ofHours(9)),
        "<p>html</p>",
        "text",
        true,
        true,
        "Backend",
        "중급",
        "요지",
        List.of("Backend"),
        List.of(),
        List.of());
  }
}
