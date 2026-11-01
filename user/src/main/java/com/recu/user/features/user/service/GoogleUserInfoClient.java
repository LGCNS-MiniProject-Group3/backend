package com.recu.user.features.user.service;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.recu.common.web.error.BusinessException;
import com.recu.user.error.UserErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GoogleUserInfoClient {

  // Google API 기본 주소
  private static final String GOOGLE_API_BASE_URL = "https://www.googleapis.com";

  // 사용자 정보 조회 API
  private static final String USER_INFO_PATH = "/oauth2/v1/userinfo";

  // 토큰이 어느 앱(client id)용으로 발급됐는지 확인하는 API (#24 류지범님 로직)
  private static final String TOKEN_INFO_URL =
      "https://oauth2.googleapis.com/tokeninfo?access_token={accessToken}";

  private final RestClient restClient;
  private final String googleClientId;

  public GoogleUserInfoClient(
      RestClient.Builder restClientBuilder, @Value("${google.client-id}") String googleClientId) {
    this.restClient = restClientBuilder.baseUrl(GOOGLE_API_BASE_URL).build();
    this.googleClientId = googleClientId;
  }

  // Google Access Token으로 사용자 정보 조회
  public GoogleUserInfo getUserInfo(String googleAccessToken) {
    verifyAudience(googleAccessToken);

    return restClient
        .get()
        .uri(USER_INFO_PATH)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + googleAccessToken)
        .retrieve()
        // 잘못된 Google Access Token 처리
        .onStatus(
            HttpStatusCode::isError,
            (request, response) -> {
              throw new BusinessException(UserErrorCode.INVALID_GOOGLE_TOKEN);
            })
        .body(GoogleUserInfo.class);
  }

  // 우리 client id 로 발급된 토큰인지 확인한다. 다른 앱(Playground 등)의 토큰은 거절한다
  private void verifyAudience(String googleAccessToken) {
    if (googleAccessToken == null || googleAccessToken.isBlank()) {
      throw new BusinessException(UserErrorCode.INVALID_GOOGLE_TOKEN);
    }

    GoogleTokenInfo tokenInfo =
        restClient
            .get()
            .uri(TOKEN_INFO_URL, googleAccessToken)
            .retrieve()
            .onStatus(
                HttpStatusCode::isError,
                (request, response) -> {
                  throw new BusinessException(UserErrorCode.INVALID_GOOGLE_TOKEN);
                })
            .body(GoogleTokenInfo.class);

    if (googleClientId == null
        || googleClientId.isBlank()
        || tokenInfo == null
        || !googleClientId.equals(tokenInfo.clientId())) {
      throw new BusinessException(UserErrorCode.INVALID_GOOGLE_TOKEN);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record GoogleTokenInfo(@JsonAlias({"aud", "audience", "issued_to"}) String clientId) {}

  // Google의 id는 app_user.google_sub와 연결하는 변경되지 않는 사용자 식별자다.
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GoogleUserInfo(String id, String email, String name) {}
}
