package com.recu.content.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recu.common.kafka.EventEnvelope;
import com.recu.common.kafka.KafkaTopics;
import com.recu.common.kafka.event.PostCollectedEvent;
import com.recu.content.features.ingest.service.PostCollectedIngestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostCollectedListenerTest {
  @Mock PostCollectedIngestService service;
  @Mock PostCollectedEvent payload;
  @InjectMocks PostCollectedListener listener;

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void 저장과_중복_건너뛰기는_같은_payload를_저장_서비스로_전달한다(boolean saved) {
    when(service.ingest(payload)).thenReturn(saved);

    listener.onPostCollected(message(), KafkaTopics.TOPIC_POST_COLLECTED, "https://example.com/74");

    verify(service).ingest(payload);
  }

  @Test
  void 저장_예외를_성공이나_중복으로_바꾸지_않고_그대로_전파한다() {
    var failure = new IllegalStateException("저장 실패");
    when(service.ingest(payload)).thenThrow(failure);

    assertThatThrownBy(
            () ->
                listener.onPostCollected(
                    message(), KafkaTopics.TOPIC_POST_COLLECTED, "https://example.com/74"))
        .isSameAs(failure);
  }

  private EventEnvelope<PostCollectedEvent> message() {
    return EventEnvelope.of(KafkaTopics.EVENT_TYPE_POST_COLLECTED, payload);
  }
}
