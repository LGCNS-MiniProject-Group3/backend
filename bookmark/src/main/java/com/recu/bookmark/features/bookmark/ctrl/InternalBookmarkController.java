package com.recu.bookmark.features.bookmark.ctrl;

import com.recu.bookmark.features.bookmark.domain.dto.BookmarkLookupRequestDTO;
import com.recu.bookmark.features.bookmark.service.InternalBookmarkService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 서비스끼리만 부르는 경로. Gateway 라우트에 넣지 않아 밖에서는 부를 수 없다 (design.md 4장). */
@RestController
@RequestMapping("/internal/bookmarks")
@RequiredArgsConstructor
public class InternalBookmarkController {

  private final InternalBookmarkService service;

  @PostMapping("/lookup")
  @Operation(summary = "② 글 번호 중 저장한 글 조회 (content의 bookmarked 표시)")
  public List<Long> lookup(@Valid @RequestBody BookmarkLookupRequestDTO request) {
    return service.lookup(request.userId(), request.postIds());
  }
}
