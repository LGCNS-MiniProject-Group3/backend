package com.recu.collector.features.collect.ctrl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recu.collector.features.collect.domain.dto.CollectJobResponseDTO;
import com.recu.collector.features.collect.service.CollectJobService;
import com.recu.common.web.error.BusinessException;
import com.recu.common.web.error.CommonErrorCode;
import java.time.OffsetDateTime;
import java.util.List;
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
            new CollectJobResponseDTO(
                "collect-20260927-120000",
                1L,
                "RUNNING",
                OffsetDateTime.parse("2026-09-27T12:00:00+09:00"),
                null,
                0));
    mvc.perform(
            post("/api/admin/collect")
                .contentType("application/json")
                .content("{\"companyId\": 1}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.jobId").value("collect-20260927-120000"))
        .andExpect(jsonPath("$.startedAt").value("2026-09-27T12:00:00+09:00"));
  }

  @Test
  void 작업_목록을_받는다() throws Exception {
    when(jobService.listRecent())
        .thenReturn(
            List.of(
                new CollectJobResponseDTO(
                    "collect-1",
                    null,
                    "DONE",
                    OffsetDateTime.parse("2026-09-27T12:00:00+09:00"),
                    OffsetDateTime.parse("2026-09-27T12:05:00+09:00"),
                    3)));
    mvc.perform(get("/api/admin/collect/jobs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].jobId").value("collect-1"));
  }

  @Test
  void 없는_작업은_404() throws Exception {
    when(jobService.get("nope")).thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));
    mvc.perform(get("/api/admin/collect/jobs/nope")).andExpect(status().isNotFound());
  }
}
