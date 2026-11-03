package com.recu.bookmark.features.bookmark.ctrl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recu.bookmark.client.ContentClient;
import com.recu.bookmark.client.dto.PostSummariesRequestDTO;
import com.recu.bookmark.client.dto.PostSummaryResponseDTO;
import com.recu.bookmark.database.entity.BookmarkEntity;
import com.recu.bookmark.features.bookmark.repository.BookmarkRepository;
import com.recu.bookmark.features.guideflag.repository.UserGuideFlagRepository;
import com.recu.bookmark.features.guideflag.service.UserGuideFlagService;
import feign.FeignException;
import feign.Request;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 북마크 추가·목록이 content ⑦로 글 확인·카드 정보를 채우는지. content는 가짜로 둔다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookmarkControllerTest {

  private static final long USER_ID = 12L;

  @Autowired MockMvc mockMvc;
  @Autowired BookmarkRepository bookmarkRepository;
  @Autowired UserGuideFlagService guideFlags;
  @Autowired UserGuideFlagRepository guideFlagRepository;
  @MockitoBean ContentClient contentClient;

  @BeforeEach
  void setUp() {
    bookmarkRepository.deleteAll();
    guideFlagRepository.deleteAll();
  }

  @Test
  @DisplayName("저장한 글이 없으면 ⑦을 부르지 않고 빈 목록")
  void emptyList() throws Exception {
    mockMvc
        .perform(get("/api/bookmarks").header("X-User-Id", USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count").value(0))
        .andExpect(jsonPath("$.bookmarks").isEmpty());
    verifyNoInteractions(contentClient);
  }

  @Test
  @DisplayName("목록은 ⑦을 한 번만 불러 최근 저장 순으로 카드 정보를 채운다")
  void listFillsCardsFromContent() throws Exception {
    save(5L, LocalDateTime.of(2026, 11, 1, 9, 0));
    save(7L, LocalDateTime.of(2026, 11, 2, 9, 0));
    when(contentClient.getSummaries(any())).thenReturn(List.of(summary(7L), summary(5L)));

    mockMvc
        .perform(get("/api/bookmarks").header("X-User-Id", USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count").value(2))
        .andExpect(jsonPath("$.bookmarks[0].postId").value(7))
        .andExpect(jsonPath("$.bookmarks[0].title").value("글 7"))
        .andExpect(jsonPath("$.bookmarks[0].companyId").value(1))
        .andExpect(jsonPath("$.bookmarks[0].companyName").value("올리브영"))
        .andExpect(jsonPath("$.bookmarks[0].categories[0]").value("Backend"))
        .andExpect(jsonPath("$.bookmarks[0].skills[0]").value("Kafka"))
        .andExpect(jsonPath("$.bookmarks[0].publishedAt").value("2026-03-18"))
        .andExpect(jsonPath("$.bookmarks[0].savedAt").value("2026-11-02T09:00:00+09:00"))
        .andExpect(jsonPath("$.bookmarks[0].hasGuide").value(false))
        .andExpect(jsonPath("$.bookmarks[0].createdAt").doesNotExist())
        .andExpect(jsonPath("$.bookmarks[1].postId").value(5));
    verify(contentClient).getSummaries(new PostSummariesRequestDTO(List.of(7L, 5L)));
  }

  @Test
  @DisplayName("가이드 표시 사본에 있는 글만 hasGuide true, 다른 회원 표시는 섞이지 않는다")
  void listHasGuideFromFlags() throws Exception {
    save(5L, LocalDateTime.of(2026, 11, 1, 9, 0));
    save(7L, LocalDateTime.of(2026, 11, 2, 9, 0));
    guideFlags.addFlag(USER_ID, 7L);
    guideFlags.addFlag(99L, 5L); // 다른 회원의 표시
    when(contentClient.getSummaries(any())).thenReturn(List.of(summary(7L), summary(5L)));

    mockMvc
        .perform(get("/api/bookmarks").header("X-User-Id", USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bookmarks[0].postId").value(7))
        .andExpect(jsonPath("$.bookmarks[0].hasGuide").value(true))
        .andExpect(jsonPath("$.bookmarks[1].postId").value(5))
        .andExpect(jsonPath("$.bookmarks[1].hasGuide").value(false));
  }

  @Test
  @DisplayName("content가 응답하지 않으면 목록은 502 UPSTREAM_ERROR")
  void listUpstreamError() throws Exception {
    save(5L, LocalDateTime.now());
    when(contentClient.getSummaries(any())).thenThrow(feignError(503));

    mockMvc
        .perform(get("/api/bookmarks").header("X-User-Id", USER_ID))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"));
  }

  @Test
  @DisplayName("있는 글은 저장하고 201")
  void addExistingPost() throws Exception {
    when(contentClient.getSummaries(any())).thenReturn(List.of(summary(5L)));

    mockMvc
        .perform(post("/api/bookmarks/5").header("X-User-Id", USER_ID))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.postId").value(5))
        .andExpect(jsonPath("$.createdAt").value(endsWith("+09:00")))
        .andExpect(jsonPath("$.title").doesNotExist());
    verify(contentClient).getSummaries(new PostSummariesRequestDTO(List.of(5L)));
    assertThat(bookmarkRepository.existsBookmark(USER_ID, 5L)).isTrue();
  }

  @Test
  @DisplayName("없는 글은 404 NOT_FOUND, 저장하지 않는다")
  void addMissingPost() throws Exception {
    when(contentClient.getSummaries(any())).thenThrow(feignError(404));

    mockMvc
        .perform(post("/api/bookmarks/999").header("X-User-Id", USER_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.field").value("postId"));
    assertThat(bookmarkRepository.count()).isZero();
  }

  @Test
  @DisplayName("이미 저장한 글은 409 ALREADY_BOOKMARKED")
  void addTwice() throws Exception {
    save(5L, LocalDateTime.now());
    when(contentClient.getSummaries(any())).thenReturn(List.of(summary(5L)));

    mockMvc
        .perform(post("/api/bookmarks/5").header("X-User-Id", USER_ID))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ALREADY_BOOKMARKED"));
  }

  private void save(Long postId, LocalDateTime createdAt) {
    bookmarkRepository.save(
        BookmarkEntity.builder().userId(USER_ID).blogPostId(postId).created_at(createdAt).build());
  }

  private static PostSummaryResponseDTO summary(Long postId) {
    return new PostSummaryResponseDTO(
        postId,
        1L,
        "올리브영",
        "글 " + postId,
        List.of("Backend"),
        "요지",
        List.of("Kafka", "Redis"),
        LocalDate.of(2026, 3, 18));
  }

  private static FeignException feignError(int status) {
    Request request =
        Request.create(
            Request.HttpMethod.POST,
            "http://content/internal/posts/summaries",
            Map.of(),
            null,
            StandardCharsets.UTF_8,
            null);
    return FeignException.errorStatus(
        "ContentClient#getSummaries",
        feign.Response.builder()
            .status(status)
            .reason("x")
            .request(request)
            .headers(Map.of())
            .build());
  }
}
