package com.recu.content.features.ingest.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

/**
 * 기술 사전(tech_dict.json). 수집 단계에서 만든 것을 그대로 읽는다.
 *
 * @param finalCounts 기술 이름 → 붙은 글 수. 537종 전부
 * @param usable 포괄어를 뺀 408종. 지금은 안 쓰지만 원본 그대로 둔다
 * @param aliasMap 별칭 → 대표 이름 (K8s → Kubernetes)
 * @param rep 정규화 키 → 대표 표기 (springboot → Spring Boot)
 * @param tooBroad 포괄어 129종. 표에는 넣되 화면 선택지에서 뺀다
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TechDictionary(
    @com.fasterxml.jackson.annotation.JsonProperty("final") Map<String, Integer> finalCounts,
    Map<String, Integer> usable,
    @com.fasterxml.jackson.annotation.JsonProperty("alias_map") Map<String, String> aliasMap,
    Map<String, String> rep,
    @com.fasterxml.jackson.annotation.JsonProperty("too_broad") java.util.List<String> tooBroad) {}
