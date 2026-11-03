package com.recu.bookmark.client;

import com.recu.bookmark.client.dto.PostSummariesRequestDTO;
import com.recu.bookmark.client.dto.PostSummaryResponseDTO;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** ⑦ bookmark → content: 저장한 글의 카드 정보 (design.md 5장). */
@FeignClient(name = "content", path = "/internal/posts")
public interface ContentClient {

  /** 받은 번호 순서대로 돌려준다. 없는 글이 하나라도 있으면 content가 404로 응답한다. */
  @PostMapping("/summaries")
  List<PostSummaryResponseDTO> getSummaries(@RequestBody PostSummariesRequestDTO request);
}
