package com.recu.content.client;

import com.recu.content.client.dto.UserPreferenceResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user", path = "/internal/users")
public interface UserClient {
  @GetMapping("/{userId}/preference")
  UserPreferenceResponseDTO getPreference(@PathVariable("userId") Long userId);
}
