package com.recu.user.features.user.service;

import com.recu.user.features.user.domain.dto.ProfileResponseDTO;
import com.recu.user.features.user.domain.dto.ProfileResponseDTO.SkillResponseDTO;
import com.recu.user.features.user.domain.dto.UserPreferenceResponseDTO;
import com.recu.user.features.user.domain.dto.UserProfileResponseDTO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 다른 서비스가 부르는 ①·④. 프로필 조회(GET /api/profile)와 같은 값을 부르는 쪽이 정한 모양으로 바꿔 준다.
 *
 * <p>원본은 content·guide가 user_job_field·user_skill 표나 ProfileService를 직접 읽었다. 기술 이름은 ③으로 채운다.
 */
@Service
@RequiredArgsConstructor
public class InternalUserService {

  private final ProfileService profileService;

  public UserPreferenceResponseDTO preference(Long userId) {
    ProfileResponseDTO profile = profileService.getProfile(userId);
    return new UserPreferenceResponseDTO(
        profile.getJobFields(), names(profile.getWantSkills()), profile.getProfileVersion());
  }

  public UserProfileResponseDTO profile(Long userId) {
    ProfileResponseDTO profile = profileService.getProfile(userId);
    return new UserProfileResponseDTO(
        profile.getUserId(),
        profile.getCareerYears(),
        profile.getJobFields(),
        names(profile.getHaveSkills()),
        names(profile.getWantSkills()),
        profile.getProfileVersion());
  }

  private static List<String> names(List<SkillResponseDTO> skills) {
    return skills.stream().map(SkillResponseDTO::name).toList();
  }
}
