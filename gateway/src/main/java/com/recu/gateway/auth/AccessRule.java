package com.recu.gateway.auth;

import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * 경로별 접근 규칙. 원본 SecurityConfig의 permitAll 목록을 옮겼다 (docs/design.md 4장).
 *
 * <p>원본에서 임시로 열어 둔 {@code POST /api/admin/collect/import}는 이번에 ADMIN 전용으로 닫았다.
 */
final class AccessRule {

  private static final List<Permit> PUBLIC =
      List.of(

          // 이메일 회원가입 및 로그인
          new Permit(HttpMethod.POST, "/api/auth/signup"),
          new Permit(HttpMethod.POST, "/api/auth/login"),
          new Permit(HttpMethod.POST, "/api/auth/google"),
          new Permit(HttpMethod.POST, "/api/auth/refresh"),
          new Permit(HttpMethod.GET, "/api/tech-tags"),
          // 기업 목록(GET /api/companies)은 토큰이 필요하고, 기업 소개 한 건만 열려 있다
          new Permit(HttpMethod.GET, "/api/companies/{id}"),
          new Permit(null, "/swagger-ui/**"),
          new Permit(null, "/swagger-ui.html"),
          new Permit(null, "/v3/api-docs/**"));

  private static final PathPattern ADMIN = parse("/api/admin/**");

  private AccessRule() {}

  /** 토큰 없이 통과시키는 경로인가 */
  static boolean isPublic(HttpMethod method, PathContainer path) {
    return PUBLIC.stream().anyMatch(p -> p.matches(method, path));
  }

  /** ADMIN만 부를 수 있는 경로인가 */
  static boolean isAdminOnly(PathContainer path) {
    return ADMIN.matches(path);
  }

  private static PathPattern parse(String pattern) {
    return PathPatternParser.defaultInstance.parse(pattern);
  }

  /** method가 null이면 모든 메서드 */
  private record Permit(HttpMethod method, PathPattern pattern) {

    Permit(HttpMethod method, String pattern) {
      this(method, parse(pattern));
    }

    boolean matches(HttpMethod requestMethod, PathContainer path) {
      return (method == null || method.equals(requestMethod)) && pattern.matches(path);
    }
  }
}
