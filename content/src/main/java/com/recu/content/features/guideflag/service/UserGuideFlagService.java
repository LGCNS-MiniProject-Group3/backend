package com.recu.content.features.guideflag.service;

import com.recu.content.database.repository.UserGuideFlagRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 가이드 생성(C)과 프로필 변경(B)에 따라 content의 가이드 표시 사본을 갱신한다. */
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

  @Transactional
  public void clearFlagsForUser(Long userId) {
    Objects.requireNonNull(userId, "userId");
    guideFlags.deleteAllByUserId(userId);
  }
}
