package com.recu.bookmark.features.guideflag.service;

import com.recu.bookmark.features.guideflag.repository.UserGuideFlagRepository;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 가이드 생성(C)과 프로필 변경(B)에 따라 bookmark의 가이드 표시 사본을 고친다. */
@Service
@RequiredArgsConstructor
public class UserGuideFlagService {
  private final UserGuideFlagRepository guideFlags;

  @Transactional
  public void addFlag(Long userId, Long postId) {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(postId, "postId");
    guideFlags.insertIfAbsent(userId, postId);
  }

  /** 프로필이 바뀌면 그 사람의 기존 가이드는 더 이상 맞지 않아 모두 지운다. */
  @Transactional
  public void clearFlagsForUser(Long userId) {
    Objects.requireNonNull(userId, "userId");
    guideFlags.deleteAllByUserId(userId);
  }

  /** 글 번호 중 "가이드 있음"인 것. 북마크 목록의 hasGuide에 쓴다. */
  @Transactional(readOnly = true)
  public Set<Long> guidedPostIds(Long userId, Collection<Long> postIds) {
    if (postIds.isEmpty()) {
      return Set.of();
    }
    return Set.copyOf(guideFlags.findGuidedPostIds(userId, postIds));
  }
}
