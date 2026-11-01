package com.recu.gateway.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 모든 요청의 JWT를 검사하고, 통과하면 회원 번호·권한을 헤더로 붙여 서비스에 넘긴다 (docs/design.md 4장). 원본의 JwtAuthenticationFilter
 * + SecurityConfig 접근 규칙을 옮겼다.
 *
 * <ol>
 *   <li>클라이언트가 보낸 {@code X-User-*} 헤더를 지운다. 지우지 않으면 누구나 다른 회원으로 위장할 수 있다
 *   <li>Authorization 헤더가 있으면 검사한다. 틀리면 공개 경로라도 401 (원본과 같음)
 *   <li>토큰이 없는데 공개 경로가 아니면 401, {@code /api/admin/**}인데 ADMIN이 아니면 403
 * </ol>
 */
@Component
public class AuthFilter implements GlobalFilter, Ordered {

  /** 요청 로그 필터 바로 다음. 로그에 401·403도 남는다 */
  static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 1;

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtVerifier jwtVerifier;
  private final ObjectMapper objectMapper;

  public AuthFilter(JwtVerifier jwtVerifier, ObjectMapper objectMapper) {
    this.jwtVerifier = jwtVerifier;
    this.objectMapper = objectMapper;
  }

  @Override
  public int getOrder() {
    return ORDER;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();
    PathContainer path = request.getPath().pathWithinApplication();
    String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

    JwtVerifier.AccessUser user;
    try {
      user = authorization == null ? null : jwtVerifier.verify(bearerToken(authorization));
    } catch (AuthException e) {
      return reject(exchange.getResponse(), e.error());
    }

    if (user == null && !AccessRule.isPublic(request.getMethod(), path)) {
      return reject(exchange.getResponse(), AuthError.UNAUTHORIZED);
    }
    if (AccessRule.isAdminOnly(path) && (user == null || !user.isAdmin())) {
      return reject(
          exchange.getResponse(), user == null ? AuthError.UNAUTHORIZED : AuthError.FORBIDDEN);
    }

    ServerHttpRequest forwarded =
        request
            .mutate()
            .headers(
                headers -> {
                  headers.keySet().removeIf(name -> startsWithIgnoreCase(name, UserHeaders.PREFIX));
                  if (user != null) {
                    headers.set(UserHeaders.USER_ID, user.userId().toString());
                    headers.set(UserHeaders.USER_ROLE, user.role());
                  }
                })
            .build();
    return chain.filter(exchange.mutate().request(forwarded).build());
  }

  private String bearerToken(String authorization) {
    if (!authorization.startsWith(BEARER_PREFIX)) {
      throw new AuthException(AuthError.INVALID_TOKEN);
    }
    String token = authorization.substring(BEARER_PREFIX.length()).trim();
    if (token.isEmpty()) {
      throw new AuthException(AuthError.INVALID_TOKEN);
    }
    return token;
  }

  /** 원본과 같은 오류 응답 {code, message, field} */
  private Mono<Void> reject(ServerHttpResponse response, AuthError error) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("code", error.code());
    body.put("message", error.message());
    body.put("field", null);

    byte[] bytes;
    try {
      bytes = objectMapper.writeValueAsBytes(body);
    } catch (JsonProcessingException e) {
      return Mono.error(e);
    }
    response.setStatusCode(error.status());
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
  }

  private static boolean startsWithIgnoreCase(String name, String prefix) {
    return name.regionMatches(true, 0, prefix, 0, prefix.length());
  }
}
