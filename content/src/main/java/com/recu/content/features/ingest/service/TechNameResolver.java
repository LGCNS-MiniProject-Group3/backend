package com.recu.content.features.ingest.service;

import com.recu.content.features.ingest.domain.dto.TechDictionary;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 가 뽑은 기술 이름을 사전의 대표 이름으로 바꾼다.
 *
 * <p>같은 기술을 다르게 적은 것이 많다. 사전을 만들 때 두 단계를 거쳤으므로 표에 넣을 때도 같은 두 단계를 거쳐야 이름이 맞는다.
 *
 * <p>1) 기계 정규화: 대소문자·공백·하이픈·복수형을 지운 열쇠로 대표 표기를 찾는다 (springboot → Spring Boot)
 *
 * <p>2) 동의어 통합: 사전이 묶어 둔 별칭을 대표 이름으로 바꾼다 (K8s → Kubernetes)
 *
 * <p>여기에 넷을 더 얹는다. 사전을 만들 때 AI 가 「포함 관계는 묶지 마라」는 지시를 보수적으로 지켜서 안 묶었는데, 사용자가 고르는 20종과 이름이 어긋나 글이 하나도
 * 안 걸리던 것들이다.
 */
public class TechNameResolver {

  /** 사전이 못 묶은 것. 왼쪽이 사전 표기, 오른쪽이 사용자 선택지 20종의 이름. */
  private static final Map<String, String> EXTRA_ALIASES =
      Map.of(
          "Spring Framework", "Spring",
          "Server-Sent Events", "SSE",
          "Model Context Protocol", "MCP",
          "Claude Code Skill", "Claude Code");

  private final Map<String, String> rep;
  private final Map<String, String> aliases;
  private final Set<String> known;

  public TechNameResolver(TechDictionary dictionary) {
    this.rep = dictionary.rep() == null ? Map.of() : dictionary.rep();
    Map<String, String> merged = new LinkedHashMap<>();
    if (dictionary.aliasMap() != null) {
      merged.putAll(dictionary.aliasMap());
    }
    merged.putAll(EXTRA_ALIASES);
    this.aliases = merged;
    this.known = dictionary.finalCounts() == null ? Set.of() : dictionary.finalCounts().keySet();
  }

  /** 표에 넣을 이름. 사전에 없는 이름이면 null 을 돌려준다. */
  public String resolve(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String name = raw.trim().replaceAll("\\s+", " ");
    name = rep.getOrDefault(normalize(name), name);
    name = aliases.getOrDefault(name, name);
    return known.contains(name) || EXTRA_ALIASES.containsValue(name) ? name : null;
  }

  /**
   * 파일 없이 사전을 만든다. 웹 수집에서 쓴다.
   *
   * <p>대표 표기는 앞에 오는 이름이 이긴다. 표에 이미 있는 이름 → 화면 선택지 20종 → 이번에 AI 가 뽑은 이름 순이다. 그래서 새로 들어온 글도 기존 태그와 같은
   * 행에 붙는다.
   */
  public static TechDictionary buildDictionary(
      Collection<String> existing, Collection<String> preferred, List<String> extracted) {
    Map<String, String> rep = new LinkedHashMap<>();
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (Collection<String> names : List.of(existing, preferred, extracted)) {
      for (String raw : names) {
        if (raw == null || raw.isBlank()) {
          continue;
        }
        String name = raw.trim().replaceAll("\\s+", " ");
        String key = normalize(name);
        if (key.isEmpty()) {
          continue;
        }
        String display = rep.computeIfAbsent(key, k -> name);
        counts.putIfAbsent(display, 0);
      }
    }
    for (String raw : extracted) {
      if (raw == null || raw.isBlank()) {
        continue;
      }
      String display = rep.get(normalize(raw.trim().replaceAll("\\s+", " ")));
      if (display != null) {
        counts.merge(display, 1, Integer::sum);
      }
    }
    return new TechDictionary(counts, counts, Map.of(), rep, List.of());
  }

  /** 사전을 만들 때 쓴 것과 같은 정규화. 대소문자·공백·하이픈·밑줄·점·복수형 s 를 없앤다. */
  private static String normalize(String s) {
    String k = s.toLowerCase().trim();
    k = k.replaceAll("[\\s\\-_.]+", "");
    k = k.replaceAll("s$", "");
    return k;
  }

  /** 사전에 있는 이름 전부. tech_tag 표에 넣을 목록이다. */
  public Set<String> allNames() {
    return known;
  }

  /** 사전 표기를 20종 이름으로 바꾼 짝. tech_tag 의 aliases 칸에 적어 둔다. */
  public static Map<String, String> extraAliases() {
    return EXTRA_ALIASES;
  }
}
