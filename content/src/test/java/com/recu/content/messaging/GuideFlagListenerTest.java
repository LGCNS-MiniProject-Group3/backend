package com.recu.content.messaging;

import com.recu.common.kafka.KafkaTopics;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

@EmbeddedKafka(
    partitions = 3,
    topics = {
      KafkaTopics.TOPIC_POST_COLLECTED,
      KafkaTopics.TOPIC_PROFILE_UPDATED,
      KafkaTopics.TOPIC_GUIDE_CREATED
    },
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestPropertySource(
    properties =
        "spring.datasource.url=jdbc:h2:mem:guideflaglistener;MODE=MariaDB;DB_CLOSE_DELAY=-1")
class GuideFlagListenerTest extends AbstractGuideFlagListenerTest {}
