package com.recu.collector.features.collect.ctrl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recu.collector.features.collect.service.CollectJobService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 웹 수집 입구. 바로 202와 작업 번호를 준다.
 *
 * <p>원본의 토큰 없음 401 · USER 403 검사는 Gateway로 옮겨 가서 Gateway 테스트(#9)에서 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminCollectControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean CollectJobService jobService;

  @Test
  void 바로_202_와_작업_번호를_받는다() throws Exception {
    when(jobService.start(any()))
        .thenReturn(
            new CollectJobService.Job(
                "collect-20260927-120000", OffsetDateTime.parse("2026-09-27T12:00:00+09:00")));
    mvc.perform(
            post("/api/admin/collect")
                .contentType("application/json")
                .content("{\"companyId\": 1}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.jobId").value("collect-20260927-120000"))
        .andExpect(jsonPath("$.startedAt").value("2026-09-27T12:00:00+09:00"));
  }
}
