package com.recu.content.client;

import com.recu.content.client.dto.BookmarkLookupRequestDTO;
import feign.FeignException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContentBookmarkLookup {
  private final BookmarkClient client;

  public Set<Long> get(Long userId, List<Long> postIds) {
    if (postIds.isEmpty()) return Set.of();
    try {
      List<Long> result = client.lookup(new BookmarkLookupRequestDTO(userId, postIds));
      if (result == null
          || result.stream().anyMatch(Objects::isNull)
          || !new HashSet<>(postIds).containsAll(result)) {
        log.warn("북마크 조회 응답이 올바르지 않아 저장 표시를 비활성화합니다");
        return Set.of();
      }
      return new HashSet<>(result);
    } catch (FeignException exception) {
      // 설계에서 선택 값으로 정한 북마크 조회 ②에만 대체 응답을 적용한다.
      log.warn("북마크 조회에 실패해 저장 표시를 비활성화합니다. status={}", exception.status());
      return Set.of();
    }
  }
}
