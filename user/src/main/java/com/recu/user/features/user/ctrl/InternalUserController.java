package com.recu.user.features.user.ctrl;

import com.recu.user.features.user.domain.dto.UserPreferenceResponseDTO;
import com.recu.user.features.user.domain.dto.UserProfileResponseDTO;
import com.recu.user.features.user.service.InternalUserService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 서비스끼리만 부르는 경로. Gateway 라우트에 넣지 않아 밖에서는 부를 수 없다 (design.md 4장). */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

  private final InternalUserService service;

  @GetMapping("/{userId}/preference")
  @Operation(summary = "① 희망 직무·관심 기술 이름 (content의 기업 정렬·글 필터)")
  public UserPreferenceResponseDTO preference(@PathVariable Long userId) {
    return service.preference(userId);
  }

  @GetMapping("/{userId}/profile")
  @Operation(summary = "④ 경력·보유·관심 기술·희망 직무 (guide의 AI 입력·가이드 재사용 키)")
  public UserProfileResponseDTO profile(@PathVariable Long userId) {
    return service.profile(userId);
  }
}
