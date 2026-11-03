package com.recu.bookmark.features.bookmark.ctrl;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recu.bookmark.database.entity.BookmarkEntity;
import com.recu.bookmark.features.bookmark.repository.BookmarkRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** ② POST /internal/bookmarks/lookup. 요청·응답 모양은 content의 BookmarkClient와 같다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalBookmarkControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired BookmarkRepository bookmarkRepository;

  @BeforeEach
  void setUp() {
    bookmarkRepository.deleteAll();
    save(12L, 1L);
    save(12L, 3L);
    save(99L, 2L); // 다른 회원이 저장한 글
  }

  @Test
  @DisplayName("받은 글 번호 중 이 회원이 저장한 것만 받은 순서대로 돌려준다")
  void returnsBookmarkedPostIds() throws Exception {
    lookup("{\"userId\":12,\"postIds\":[3,2,1,4]}")
        .andExpect(status().isOk())
        .andExpect(content().json("[3,1]", true));
  }

  @Test
  @DisplayName("같은 번호가 여러 번 와도 한 번만 돌려준다")
  void ignoresDuplicates() throws Exception {
    lookup("{\"userId\":12,\"postIds\":[1,1,1]}")
        .andExpect(status().isOk())
        .andExpect(content().json("[1]", true));
  }

  @Test
  @DisplayName("저장한 글이 없거나 postIds가 비면 빈 목록")
  void emptyResults() throws Exception {
    lookup("{\"userId\":12,\"postIds\":[]}").andExpect(content().json("[]", true));
    lookup("{\"userId\":7,\"postIds\":[1,2,3]}").andExpect(content().json("[]", true));
  }

  @Test
  @DisplayName("userId가 없거나 글 번호가 0 이하면 400 INVALID_INPUT")
  void invalidInput() throws Exception {
    lookup("{\"postIds\":[1]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.field").value("userId"));
    lookup("{\"userId\":12,\"postIds\":[0]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  private ResultActions lookup(String body) throws Exception {
    return mockMvc.perform(
        post("/internal/bookmarks/lookup").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private void save(Long userId, Long postId) {
    bookmarkRepository.save(
        BookmarkEntity.builder()
            .userId(userId)
            .blogPostId(postId)
            .created_at(LocalDateTime.now())
            .build());
  }
}
