package com.recu.gateway.route;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/**
 * 외부 경로 → 서비스. 서비스 주소는 Eureka 이름(lb://)으로 찾는다.
 *
 * <p>{@code /internal/**}은 일부러 넣지 않는다. 서비스 간 호출 전용이라 외부에서 부르면 404가 난다.
 */
@Configuration
public class RouteConfig {

  @Bean
  RouteLocator routes(RouteLocatorBuilder builder) {
    return builder
        .routes()
        // 가이드는 /api/posts/** 아래에 있지만 guide 서비스 몫이라 content보다 먼저 맞춘다
        .route(
            "guide",
            r ->
                r.order(-1)
                    .method(HttpMethod.POST)
                    .and()
                    .path("/api/posts/*/guide")
                    .uri("lb://guide"))
        .route("user", r -> r.path("/api/auth/**", "/api/profile/**").uri("lb://user"))
        .route(
            "content",
            r ->
                r.path("/api/companies/**", "/api/posts/**", "/api/tech-tags/**")
                    .uri("lb://content"))
        .route("collector", r -> r.path("/api/admin/collect/**").uri("lb://collector"))
        .route("bookmark", r -> r.path("/api/bookmarks/**").uri("lb://bookmark"))
        .build();
  }
}
