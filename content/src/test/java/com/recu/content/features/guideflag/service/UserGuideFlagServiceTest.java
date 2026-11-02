package com.recu.content.features.guideflag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recu.content.database.repository.UserGuideFlagRepository;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(UserGuideFlagService.class)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserGuideFlagServiceTest {
  @Autowired UserGuideFlagService service;
  @Autowired UserGuideFlagRepository flags;
  @Autowired PlatformTransactionManager transactionManager;

  @BeforeEach
  @AfterEach
  void 표시_정리() {
    flags.deleteAllInBatch();
  }

  @Test
  void 추가한_표시는_기존_조회로_확인할_수_있다() {
    service.addFlag(12L, 128L);

    assertThat(flags.findGuidedPostIds(12L, List.of(128L, 129L))).containsExactly(128L);
  }

  @Test
  void 같은_사용자와_글을_반복_추가해도_한_행만_남는다() {
    service.addFlag(12L, 128L);
    service.addFlag(12L, 128L);

    assertThat(flags.count()).isEqualTo(1);
    assertThat(flags.findGuidedPostIds(12L, List.of(128L))).containsExactly(128L);
  }

  @Test
  void 같은_글도_사용자가_다르면_각각_표시를_저장한다() {
    service.addFlag(12L, 128L);
    service.addFlag(13L, 128L);
    service.addFlag(12L, 129L);

    assertThat(flags.count()).isEqualTo(3);
    assertThat(flags.findGuidedPostIds(12L, List.of(128L, 129L)))
        .containsExactlyInAnyOrder(128L, 129L);
    assertThat(flags.findGuidedPostIds(13L, List.of(128L, 129L))).containsExactly(128L);
  }

  @Test
  void 사용자별_삭제는_해당_사용자의_모든_표시만_지운다() {
    service.addFlag(12L, 128L);
    service.addFlag(12L, 129L);
    service.addFlag(13L, 128L);

    service.clearFlagsForUser(12L);

    assertThat(flags.findGuidedPostIds(12L, List.of(128L, 129L))).isEmpty();
    assertThat(flags.findGuidedPostIds(13L, List.of(128L))).containsExactly(128L);
    assertThat(flags.count()).isEqualTo(1);
  }

  @Test
  void 표시가_없는_사용자를_반복_삭제해도_다른_사용자는_유지된다() {
    service.addFlag(13L, 128L);

    service.clearFlagsForUser(12L);
    service.clearFlagsForUser(12L);

    assertThat(flags.count()).isEqualTo(1);
    assertThat(flags.findGuidedPostIds(13L, List.of(128L))).containsExactly(128L);
  }

  @Test
  void 삭제_후_새_가이드_표시를_다시_추가할_수_있다() {
    service.addFlag(12L, 128L);
    service.clearFlagsForUser(12L);
    service.addFlag(12L, 128L);

    assertThat(flags.findGuidedPostIds(12L, List.of(128L))).containsExactly(128L);
    assertThat(flags.count()).isEqualTo(1);
  }

  @Test
  void 상위_트랜잭션이_실패하면_표시_추가도_롤백된다() {
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);

    assertThatThrownBy(
            () ->
                transaction.executeWithoutResult(
                    status -> {
                      service.addFlag(12L, 128L);
                      throw new IllegalStateException("저장 이후 처리 실패");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(flags.findGuidedPostIds(12L, List.of(128L))).isEmpty();
  }

  @Test
  void 상위_트랜잭션이_실패하면_표시_삭제도_롤백된다() {
    service.addFlag(12L, 128L);
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);

    assertThatThrownBy(
            () ->
                transaction.executeWithoutResult(
                    status -> {
                      service.clearFlagsForUser(12L);
                      throw new IllegalStateException("삭제 이후 처리 실패");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(flags.findGuidedPostIds(12L, List.of(128L))).containsExactly(128L);
  }

  @Test
  void 같은_표시가_동시에_추가되어도_모두_성공하고_한_행만_남는다() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Runnable add =
          () -> {
            try {
              if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 실행 대기 시간 초과");
              }
              service.addFlag(12L, 128L);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new IllegalStateException(e);
            }
          };
      Future<?> first = executor.submit(add);
      Future<?> second = executor.submit(add);
      start.countDown();
      first.get(10, TimeUnit.SECONDS);
      second.get(10, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
      executor.awaitTermination(10, TimeUnit.SECONDS);
    }

    assertThat(flags.count()).isEqualTo(1);
    assertThat(flags.findGuidedPostIds(12L, List.of(128L))).containsExactly(128L);
  }
}
