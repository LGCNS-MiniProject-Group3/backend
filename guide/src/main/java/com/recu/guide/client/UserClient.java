package com.recu.guide.client;

import com.recu.guide.client.dto.UserProfileResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** ④ guide → user: 가이드를 요청한 사람의 프로필 (design.md 5장). */
@FeignClient(name = "user", path = "/internal/users")
public interface UserClient {
  @GetMapping("/{userId}/profile")
  UserProfileResponseDTO getProfile(@PathVariable("userId") Long userId);
}
