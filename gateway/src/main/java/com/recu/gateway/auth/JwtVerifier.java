package com.recu.gateway.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Access 토큰의 서명·만료·claim을 확인한다. user 서비스의 {@code JwtProvider.parse}와 같은 규칙이고, 발급은 하지 않는다. 서명
 * 키(jwt.secret)는 user와 같은 값을 Config에서 받는다.
 */
@Component
public class JwtVerifier {

  private static final String ROLE_CLAIM = "role";
  private static final String TYPE_CLAIM = "type";
  private static final String ACCESS_TYPE = "access";
  private static final String USER_ROLE = "USER";
  private static final String ADMIN_ROLE = "ADMIN";

  private final Key signingKey;

  public JwtVerifier(@Value("${jwt.secret}") String secret) {
    this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  /** API 인증에 쓸 수 있는 토큰이면 회원 번호와 권한을 돌려준다. Refresh 토큰은 받지 않는다 */
  public AccessUser verify(String token) {
    Claims claims;
    try {
      claims =
          Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(token).getBody();
    } catch (ExpiredJwtException e) {
      throw new AuthException(AuthError.TOKEN_EXPIRED);
    } catch (JwtException | IllegalArgumentException e) {
      throw new AuthException(AuthError.INVALID_TOKEN);
    }

    String role = claims.get(ROLE_CLAIM, String.class);
    if (!ACCESS_TYPE.equals(claims.get(TYPE_CLAIM, String.class))
        || !(USER_ROLE.equals(role) || ADMIN_ROLE.equals(role))) {
      throw new AuthException(AuthError.INVALID_TOKEN);
    }
    try {
      return new AccessUser(Long.valueOf(claims.getSubject()), role);
    } catch (NumberFormatException e) {
      throw new AuthException(AuthError.INVALID_TOKEN);
    }
  }

  public record AccessUser(Long userId, String role) {

    public boolean isAdmin() {
      return ADMIN_ROLE.equals(role);
    }
  }
}
