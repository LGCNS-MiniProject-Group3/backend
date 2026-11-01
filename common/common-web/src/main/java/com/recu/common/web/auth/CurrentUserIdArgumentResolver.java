package com.recu.common.web.auth;

import com.recu.common.web.error.BusinessException;
import com.recu.common.web.error.CommonErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** {@link CurrentUserId}가 붙은 Long 인자에 X-User-Id 헤더 값을 넣는다. */
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.hasParameterAnnotation(CurrentUserId.class)
        && Long.class.equals(parameter.getParameterType());
  }

  @Override
  public Long resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    String value = webRequest.getHeader(UserHeaders.USER_ID);
    if (value == null || value.isBlank()) {
      throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }
    try {
      return Long.valueOf(value.trim());
    } catch (NumberFormatException e) {
      // Gateway를 거치면 생기지 않는다. 서비스를 직접 부른 잘못된 요청으로 본다.
      throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }
  }
}
