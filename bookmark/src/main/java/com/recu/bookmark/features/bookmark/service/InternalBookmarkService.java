package com.recu.bookmark.features.bookmark.service;

import com.recu.bookmark.features.bookmark.repository.BookmarkRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 다른 서비스가 부르는 내부 API (design.md 5장). */
@Service
@RequiredArgsConstructor
public class InternalBookmarkService {

  private final BookmarkRepository bookmarkRepository;

  /** ② 받은 글 번호 중 저장한 것만 돌려준다. 순서는 받은 순서, 같은 번호는 한 번만. */
  public List<Long> lookup(Long userId, List<Long> postIds) {
    Set<Long> requested = new LinkedHashSet<>(postIds);
    if (requested.isEmpty()) {
      // IN ()은 SQL 오류라 부르지 않는다
      return List.of();
    }
    Set<Long> bookmarked = Set.copyOf(bookmarkRepository.findBookmarkedPostIds(userId, requested));
    return requested.stream().filter(bookmarked::contains).toList();
  }
}
