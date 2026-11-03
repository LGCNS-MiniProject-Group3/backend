package com.recu.guide.client;

import com.recu.guide.client.dto.GuideInputResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** ⑤ guide → content: 가이드를 만들 글의 제목·계열·난이도·구간 (design.md 5장). */
@FeignClient(name = "content", path = "/internal/posts")
public interface ContentClient {
  @GetMapping("/{postId}/guide-input")
  GuideInputResponseDTO getGuideInput(@PathVariable("postId") Long postId);
}
