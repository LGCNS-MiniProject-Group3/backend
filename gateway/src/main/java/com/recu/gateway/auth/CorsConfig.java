package com.recu.gateway.auth;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * 원본 SecurityConfig의 CORS 설정을 옮겼다. 허용 출처(app.cors-origins)는 설정 저장소에 있다.
 *
 * <p>JWT 필터보다 먼저 돌아서, 브라우저의 사전 요청(OPTIONS)은 토큰 없이 통과하고 401·403 응답에도 CORS 헤더가 붙는다.
 */
@Configuration
public class CorsConfig {

  @Bean
  CorsWebFilter corsWebFilter(@Value("${app.cors-origins}") List<String> corsOrigins) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(corsOrigins);
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    // 토큰이 응답 헤더로 나가므로 이 둘을 노출해야 브라우저 JS가 읽는다
    config.setExposedHeaders(List.of("Authorization", "Refresh-Token"));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return new CorsWebFilter(source);
  }
}
