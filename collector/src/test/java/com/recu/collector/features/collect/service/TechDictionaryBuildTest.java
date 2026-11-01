package com.recu.collector.features.collect.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recu.collector.features.collect.domain.dto.TechDictionary;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 파일 없이 만든 사전. 표에 있는 이름과 20종 표기가 AI 가 적은 표기를 이긴다. */
class TechDictionaryBuildTest {

  @Test
  void 표기가_달라도_같은_기술은_앞선_이름으로_묶인다() {
    TechDictionary dictionary =
        TechNameResolver.buildDictionary(
            List.of("Redis"),
            List.of("Spring Boot", "Kafka"),
            List.of("spring-boot", "REDIS", "Kafka", "GraphQL", "Kafka"));
    TechNameResolver resolver = new TechNameResolver(dictionary);

    assertThat(resolver.resolve("springboot")).isEqualTo("Spring Boot");
    assertThat(resolver.resolve("redis")).isEqualTo("Redis");
    assertThat(resolver.resolve("GraphQL")).isEqualTo("GraphQL");
    assertThat(resolver.resolve("없는기술")).isNull();
    assertThat(dictionary.finalCounts()).containsEntry("Kafka", 2).containsEntry("Redis", 1);
  }
}
