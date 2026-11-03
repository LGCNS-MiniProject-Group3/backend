package com.recu.common.web;

import com.recu.common.web.auth.CurrentUserIdArgumentResolver;
import com.recu.common.web.error.FeignExceptionHandler;
import com.recu.common.web.error.GlobalExceptionHandler;
import feign.Request;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 서비스가 common-web 의존성만 추가하면 켜지는 설정. 패키지 스캔 없이 아래가 등록된다.
 *
 * <ul>
 *   <li>{@code @CurrentUserId} 인자 리졸버
 *   <li>오류 응답 처리기 ({code, message, field})
 *   <li>Feign을 쓰는 서비스: 호출 실패 → 502 UPSTREAM_ERROR, 기본 타임아웃
 * </ul>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonWebAutoConfiguration {

  @Bean
  public WebMvcConfigurer currentUserIdConfigurer() {
    return new WebMvcConfigurer() {
      @Override
      public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserIdArgumentResolver());
      }
    };
  }

  @Bean
  @ConditionalOnMissingBean
  public GlobalExceptionHandler globalExceptionHandler() {
    return new GlobalExceptionHandler();
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "feign.FeignException")
  static class FeignSupport {

    // 모든 Feign 호출의 기본 타임아웃. 서비스마다 바꾸지 않는다.
    // 값을 바꿔야 하면 설정 저장소의 spring.cloud.openfeign.client.config.default.* 로 덮어쓴다.
    static final long CONNECT_TIMEOUT_MILLIS = 2_000;
    static final long READ_TIMEOUT_MILLIS = 5_000;

    @Bean
    @ConditionalOnMissingBean
    public FeignExceptionHandler feignExceptionHandler() {
      return new FeignExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public Request.Options feignRequestOptions() {
      return new Request.Options(
          CONNECT_TIMEOUT_MILLIS,
          TimeUnit.MILLISECONDS,
          READ_TIMEOUT_MILLIS,
          TimeUnit.MILLISECONDS,
          true);
    }
  }
}
