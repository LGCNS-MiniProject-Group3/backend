package com.recu.common.kafka;

import static com.recu.common.kafka.KafkaTopics.TOPIC_GUIDE_CREATED;
import static com.recu.common.kafka.KafkaTopics.TOPIC_POST_COLLECTED;
import static com.recu.common.kafka.KafkaTopics.TOPIC_PROFILE_UPDATED;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.converter.JsonMessageConverter;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * 서비스가 common-kafka 의존성만 추가하면 켜지는 설정 (design.md 6장).
 *
 * <p>Spring Boot 기본 Kafka 설정보다 먼저 적용해서, 업무 메시지용 빈이 기본 빈 대신 쓰이게 한다.
 *
 * <p>spring.kafka.*는 주소만 읽고 바꾸지 않는다. 같은 Kafka를 쓰는 Spring Cloud Bus에 영향을 주지 않으려는 것이다.
 */
@AutoConfiguration(before = KafkaAutoConfiguration.class)
@EnableConfigurationProperties(KafkaProperties.class)
public class CommonKafkaAutoConfiguration {

  // 같은 key(url, userId)는 같은 파티션으로 가서 순서가 지켜진다
  static final int PARTITIONS = 3;

  // 로컬·배포 모두 브로커가 1대라 복제는 1개만 된다
  static final int REPLICAS = 1;

  // 보내는 쪽은 압축 전 크기로 한도를 검사한다. 본문 HTML이 든 A 메시지가 막히지 않게 4MB로 올린다
  static final int MAX_REQUEST_SIZE = 4 * 1024 * 1024;

  // 브로커로 가는 크기는 압축으로 줄인다. 브로커 한도(기본 1MB)는 바꾸지 않는다
  static final String COMPRESSION_TYPE = "lz4";

  @Bean
  @ConditionalOnMissingBean
  public ProducerFactory<String, Object> kafkaProducerFactory(
      KafkaProperties kafkaProperties, ObjectMapper objectMapper) {
    Map<String, Object> config = new HashMap<>();
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
    config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, COMPRESSION_TYPE);
    config.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, MAX_REQUEST_SIZE);
    // 받는 쪽이 보낸 쪽 클래스 이름에 묶이지 않도록 타입 정보 헤더를 싣지 않는다
    JsonSerializer<Object> valueSerializer = new JsonSerializer<Object>(objectMapper).noTypeInfo();
    return new DefaultKafkaProducerFactory<>(config, new StringSerializer(), valueSerializer);
  }

  @Bean
  @ConditionalOnMissingBean
  public KafkaTemplate<String, Object> kafkaTemplate(
      ProducerFactory<String, Object> kafkaProducerFactory) {
    return new KafkaTemplate<>(kafkaProducerFactory);
  }

  @Bean
  @ConditionalOnMissingBean
  public KafkaEventPublisher kafkaEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
    return new KafkaEventPublisher(kafkaTemplate);
  }

  @Bean
  @ConditionalOnMissingBean
  public ConsumerFactory<String, String> kafkaConsumerFactory(
      KafkaProperties kafkaProperties, @Value("${spring.application.name}") String groupId) {
    Map<String, Object> config = new HashMap<>();
    config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
    // groupId는 모듈 이름(code-convention). 서비스마다 그룹이 달라 B·C를 content와 bookmark가 각각 받는다
    config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    // 새 그룹이 처음 뜰 때, 뜨기 전에 쌓인 메시지부터 읽는다
    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    StringDeserializer deserializer = new StringDeserializer();
    return new DefaultKafkaConsumerFactory<>(config, deserializer, deserializer);
  }

  // 이름이 kafkaListenerContainerFactory라서 @KafkaListener가 따로 지정하지 않아도 이 설정을 쓴다
  @Bean
  @ConditionalOnMissingBean(name = "kafkaListenerContainerFactory")
  public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
      ConsumerFactory<String, String> kafkaConsumerFactory,
      ObjectMapper objectMapper,
      @Value("${spring.kafka.listener.auto-startup:true}") boolean autoStartup) {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(kafkaConsumerFactory);
    // 받은 JSON을 @KafkaListener 메서드의 파라미터 타입(예: EventEnvelope<GuideCreatedEvent>)으로 바꾼다
    factory.setRecordMessageConverter(new JsonMessageConverter(objectMapper));
    // Kafka가 없는 테스트에서는 spring.kafka.listener.auto-startup=false로 수신을 끈다
    factory.setAutoStartup(autoStartup);
    return factory;
  }

  @Bean
  public NewTopic postCollectedTopic() {
    return topic(TOPIC_POST_COLLECTED);
  }

  @Bean
  public NewTopic profileUpdatedTopic() {
    return topic(TOPIC_PROFILE_UPDATED);
  }

  @Bean
  public NewTopic guideCreatedTopic() {
    return topic(TOPIC_GUIDE_CREATED);
  }

  private static NewTopic topic(String name) {
    return TopicBuilder.name(name).partitions(PARTITIONS).replicas(REPLICAS).build();
  }
}
