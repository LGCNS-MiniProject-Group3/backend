package com.recu.user.features.user.ctrl;

import com.recu.common.web.auth.CurrentUserId;
import com.recu.user.features.user.domain.dto.ProfileRequestDTO;
import com.recu.user.features.user.domain.dto.ProfileResponseDTO;
import com.recu.user.features.user.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

  private final ProfileService profileService;

  @Operation(summary = "프로필 최초 저장", description = "희망 직무, 경력 연수, 보유 기술, 관심 기술을 저장")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "프로필 저장 성공"),
    @ApiResponse(responseCode = "400", description = "프로필 저장 실패(입력값 오류)"),
    @ApiResponse(responseCode = "401", description = "프로필 저장 실패(토큰 유효성 확인)"),
    @ApiResponse(responseCode = "409", description = "프로필 저장 실패(이미 등록된 프로필)")
  })
  @PostMapping
  public ResponseEntity<?> setProfile(
      @CurrentUserId Long userId, @RequestBody ProfileRequestDTO request) {
    ProfileResponseDTO response = profileService.setProfile(userId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @Operation(summary = "프로필 조회", description = "마이페이지에 표시할 프로필을 조회")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "프로필 조회 성공"),
    @ApiResponse(responseCode = "401", description = "프로필 조회 실패(토큰 유효성 확인)"),
  })
  @GetMapping
  public ResponseEntity<?> getProfile(@CurrentUserId Long userId) {
    ProfileResponseDTO response = profileService.getProfile(userId);
    return ResponseEntity.status(HttpStatus.OK).body(response);
  }

  @Operation(summary = "프로필 수정", description = "프로필을 저장하고 프로필 버전을 증가")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "프로필 수정 성공"),
    @ApiResponse(responseCode = "400", description = "프로필 수정 성공(입력값 오류)"),
    @ApiResponse(responseCode = "401", description = "프로필 수정 성공(토큰 유효성 확인)")
  })
  @PutMapping
  public ResponseEntity<?> editProfile(
      @CurrentUserId Long userId, @RequestBody ProfileRequestDTO request) {
    ProfileResponseDTO response = profileService.editProfile(userId, request);
    return ResponseEntity.status(HttpStatus.OK).body(response);
  }
}
