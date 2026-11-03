package com.recu.user.client;

import com.recu.user.client.dto.TechTagResponseDTO;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** ③ user → content: 기술 번호로 이름 조회 (design.md 5장). 없는 번호는 응답에서 빠진다. */
@FeignClient(name = "content", path = "/internal/tech-tags")
public interface ContentClient {
  // ids는 content가 "1,2,3" 한 칸으로 받는다
  @GetMapping
  List<TechTagResponseDTO> getTechTags(@RequestParam("ids") String ids);
}
