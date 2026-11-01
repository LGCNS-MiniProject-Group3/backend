package com.recu.content.features.post.domain.dto;

import com.recu.common.web.error.BusinessException;
import com.recu.common.web.error.CommonErrorCode;
import java.util.Arrays;
import java.util.List;

public final class ContentQueryParameters {
  private ContentQueryParameters() {}

  public static Long positiveId(String value, String field) {
    try {
      if (value == null || !value.trim().matches("[1-9][0-9]*")) {
        throw new NumberFormatException();
      }
      return Long.valueOf(value.trim());
    } catch (NumberFormatException exception) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT, field);
    }
  }

  public static List<Long> idList(String value) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT, "ids");
    }
    return Arrays.stream(value.split(",", -1)).map(v -> positiveId(v, "ids")).toList();
  }
}
