package com.recu.collector.features.collect.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recu.collector.client.ContentClient;
import com.recu.collector.features.collect.domain.dto.CompanyTarget;
import com.recu.collector.features.collect.domain.dto.ImportPublishResult;
import com.recu.collector.features.collect.domain.dto.ImportedPost;
import com.recu.collector.features.collect.domain.dto.TechDictionary;
import com.recu.collector.messaging.PostCollectedPublisher;
import com.recu.common.kafka.event.PostCollectedEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PostImportServiceTest {

  @TempDir Path dataDir;

  private final ImportFileReader reader = mock(ImportFileReader.class);
  private final ContentClient contentClient = mock(ContentClient.class);
  private final PostCollectedEventMapper mapper = mock(PostCollectedEventMapper.class);
  private final PostCollectedPublisher publisher = mock(PostCollectedPublisher.class);

  @Test
  @DisplayName("발행이 한 번 실패하면 남은 글은 보내지 않는다")
  void 발행_실패시_멈춘다() {
    when(reader.readPosts(any()))
        .thenReturn(
            List.of(
                row("https://toss.tech/a"),
                row("https://toss.tech/b"),
                row("https://toss.tech/c")));
    when(reader.readDictionary(any()))
        .thenReturn(new TechDictionary(Map.of(), Map.of(), Map.of(), Map.of(), List.of()));
    when(contentClient.getCompanies())
        .thenReturn(List.of(new CompanyTarget(1L, "토스", "FEED_FULL", "https://toss.tech/rss.xml")));
    when(mapper.toEvent(any(), any(), any())).thenReturn(mock(PostCollectedEvent.class));
    when(publisher.publish(any())).thenReturn(true, false);

    PostImportService service =
        new PostImportService(reader, contentClient, mapper, publisher, dataDir.toString());
    ImportPublishResult result = service.importFromFiles();

    assertThat(result.publishedCount()).isEqualTo(1);
    verify(publisher, times(2)).publish(any());
  }

  private static ImportedPost row(String url) {
    return new ImportedPost(
        "토스",
        null,
        "제목",
        url,
        "2026-03-18",
        10,
        false,
        List.of(),
        List.of(),
        "<p>html</p>",
        "text",
        null);
  }
}
