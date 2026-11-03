package com.recu.bookmark.features.guideflag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.recu.bookmark.features.guideflag.repository.UserGuideFlagRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 가이드 표시 사본을 DB와 직접 맞춰 본다. 수신 테스트는 실패한 메시지를 건너뛰어도 통과할 수 있어 따로 둔다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class UserGuideFlagServiceTest {

  private static final long USER_ID = 12L;

  @Autowired UserGuideFlagService service;
  @Autowired UserGuideFlagRepository flags;

  @BeforeEach
  void setUp() {
    flags.deleteAll();
  }

  @Test
  @DisplayName("같은 표시를 두 번 추가해도 예외 없이 한 행만 남는다")
  void addFlagTwice() {
    service.addFlag(USER_ID, 128L);

    assertThatNoException().isThrownBy(() -> service.addFlag(USER_ID, 128L));
    assertThat(flags.count()).isEqualTo(1);
    assertThat(service.guidedPostIds(USER_ID, List.of(128L))).containsExactly(128L);
  }
}
