package com.recu.content.client;

import com.recu.common.web.error.BusinessException;
import com.recu.common.web.error.CommonErrorCode;
import com.recu.content.client.dto.UserPreferenceResponseDTO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContentUserPreference {
  private final UserClient client;

  public UserPreferenceResponseDTO get(Long userId) {
    UserPreferenceResponseDTO result = client.getPreference(userId);
    if (result == null || result.profileVersion() == null || result.profileVersion() < 0) {
      throw new BusinessException(CommonErrorCode.UPSTREAM_ERROR);
    }
    if (result.profileVersion() == 0) {
      return new UserPreferenceResponseDTO(List.of(), List.of(), 0);
    }
    if (result.jobFields() == null
        || result.wantSkillNames() == null
        || result.jobFields().stream().anyMatch(s -> s == null || s.isBlank())
        || result.wantSkillNames().stream().anyMatch(s -> s == null || s.isBlank())) {
      throw new BusinessException(CommonErrorCode.UPSTREAM_ERROR);
    }
    return result;
  }
}
