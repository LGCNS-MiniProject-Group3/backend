package com.recu.common.web;

import com.recu.common.web.auth.CurrentUserId;
import com.recu.common.web.error.BusinessException;
import com.recu.common.web.error.CommonErrorCode;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TestController {

  static final Request REQUEST =
      Request.create(
          Request.HttpMethod.GET,
          "http://user/internal/users/1",
          Map.of(),
          null,
          StandardCharsets.UTF_8,
          null);

  record NameRequest(@NotBlank String name) {}

  @GetMapping("/me")
  Long me(@CurrentUserId Long userId) {
    return userId;
  }

  @GetMapping("/business")
  void business() {
    throw new BusinessException(CommonErrorCode.NOT_FOUND, "postId");
  }

  @PostMapping("/valid")
  void valid(@Valid @RequestBody NameRequest request) {}

  @GetMapping("/feign/error")
  void feignError() {
    throw new FeignException.InternalServerError("boom", REQUEST, null, Map.of());
  }

  @GetMapping("/feign/timeout")
  void feignTimeout() {
    throw new RetryableException(
        -1, "Read timed out", Request.HttpMethod.GET, (Long) null, REQUEST);
  }

  @GetMapping("/boom")
  void boom() {
    throw new IllegalStateException("unexpected");
  }
}
