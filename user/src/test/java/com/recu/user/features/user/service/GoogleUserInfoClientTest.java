package com.recu.user.features.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.recu.common.web.error.BusinessException;
import com.recu.user.error.UserErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 구글 토큰의 aud 가 우리 client id 일 때만 사용자 정보를 읽는지. 실제 구글은 부르지 않는다. */
class GoogleUserInfoClientTest {

  private static final String TOKEN_INFO =
      "https://oauth2.googleapis.com/tokeninfo?access_token=token-1";
  private static final String USER_INFO = "https://www.googleapis.com/oauth2/v1/userinfo";

  private MockRestServiceServer google;
  private GoogleUserInfoClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    google = MockRestServiceServer.bindTo(builder).build();
    client = new GoogleUserInfoClient(builder, "our-client-id");
  }

  @Test
  void 우리_앱_토큰이면_사용자_정보를_돌려준다() {
    google
        .expect(requestTo(TOKEN_INFO))
        .andRespond(withSuccess("{\"aud\":\"our-client-id\"}", MediaType.APPLICATION_JSON));
    google
        .expect(requestTo(USER_INFO))
        .andRespond(
            withSuccess(
                "{\"id\":\"sub-1\",\"email\":\"a@b.c\",\"name\":\"테스트\"}",
                MediaType.APPLICATION_JSON));

    assertThat(client.getUserInfo("token-1").id()).isEqualTo("sub-1");
    google.verify();
  }

  @Test
  void 다른_앱_토큰이면_거절한다() {
    google
        .expect(requestTo(TOKEN_INFO))
        .andRespond(withSuccess("{\"aud\":\"playground-id\"}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.getUserInfo("token-1"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(UserErrorCode.INVALID_GOOGLE_TOKEN);
  }

  @Test
  void 구글이_토큰을_거부하면_거절한다() {
    google.expect(requestTo(TOKEN_INFO)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

    assertThatThrownBy(() -> client.getUserInfo("token-1"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(UserErrorCode.INVALID_GOOGLE_TOKEN);
  }
}
