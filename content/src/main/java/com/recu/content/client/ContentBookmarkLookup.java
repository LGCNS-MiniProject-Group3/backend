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
        log.warn("Bookmark returned an invalid lookup result; using unsaved state");
        return Set.of();
      }
      return new HashSet<>(result);
    } catch (FeignException exception) {
      // Only lookup ② is optional; no writes or other downstream failures are hidden here.
      log.warn("Bookmark lookup unavailable; using unsaved state, status={}", exception.status());
      return Set.of();
    }
  }
}
