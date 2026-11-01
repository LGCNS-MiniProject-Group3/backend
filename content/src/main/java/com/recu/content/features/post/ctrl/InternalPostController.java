package com.recu.content.features.post.ctrl;

import com.recu.content.features.post.domain.dto.ContentQueryParameters;
import com.recu.content.features.post.domain.dto.InternalPostResponseDTO.GuideInput;
import com.recu.content.features.post.domain.dto.InternalPostResponseDTO.Summary;
import com.recu.content.features.post.domain.dto.PostSummariesRequestDTO;
import com.recu.content.features.post.service.InternalPostService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/posts")
@RequiredArgsConstructor
public class InternalPostController {
  private final InternalPostService service;

  @GetMapping("/{postId}/guide-input")
  @Operation(summary = "⑤ AI 가이드용 글과 구간 본문 조회")
  public GuideInput getGuideInput(@PathVariable("postId") String postId) {
    return service.getGuideInput(ContentQueryParameters.positiveId(postId, "postId"));
  }

  @GetMapping("/urls")
  @Operation(summary = "⑥ 기업별 이미 수집한 URL 조회")
  public List<String> getCollectedUrls(
      @RequestParam(value = "companyId", required = false) String companyId) {
    return service.getCollectedUrls(ContentQueryParameters.positiveId(companyId, "companyId"));
  }

  @PostMapping("/summaries")
  @Operation(summary = "⑦ 북마크 카드 정보 일괄 조회")
  public List<Summary> getSummaries(@Valid @RequestBody PostSummariesRequestDTO request) {
    return service.getSummaries(request.postIds());
  }
}
