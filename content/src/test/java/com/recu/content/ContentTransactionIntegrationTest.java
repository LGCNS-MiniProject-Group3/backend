package com.recu.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.recu.common.web.error.BusinessException;
import com.recu.common.web.error.CommonErrorCode;
import com.recu.content.client.BookmarkClient;
import com.recu.content.client.UserClient;
import com.recu.content.client.dto.UserPreferenceResponseDTO;
import com.recu.content.database.entity.BlogPostEntity;
import com.recu.content.database.entity.CompanyEntity;
import com.recu.content.database.repository.BlogPostRepository;
import com.recu.content.database.repository.CompanyRepository;
import com.recu.content.features.company.service.CompanyService;
import com.recu.content.features.post.service.PostService;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(
    properties = {
      "spring.jpa.open-in-view=false",
      "spring.datasource.url=jdbc:h2:mem:content_tx;MODE=MariaDB;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"
    })
@ActiveProfiles("test")
class ContentTransactionIntegrationTest {
  @Autowired CompanyService companies;
  @Autowired PostService posts;
  @Autowired CompanyRepository companyRepository;
  @Autowired BlogPostRepository postRepository;
  @Autowired DataSource dataSource;
  @MockitoBean UserClient userClient;
  @MockitoBean BookmarkClient bookmarkClient;
  CompanyEntity company;
  BlogPostEntity post;

  @BeforeEach
  void setUp() {
    postRepository.deleteAll();
    companyRepository.deleteAll();
    company =
        companyRepository.save(
            CompanyEntity.builder()
                .name("Alpha")
                .feed_url("https://example.com/feed")
                .feed_type("FEED_FULL")
                .build());
    post =
        postRepository.save(
            BlogPostEntity.builder()
                .company(company)
                .url("https://example.com/post")
                .title("Title")
                .published_at(LocalDateTime.of(2026, 1, 2, 12, 0))
                .collected_at(LocalDateTime.of(2026, 1, 2, 13, 0))
                .content_html("<p>Body</p>")
                .content_text("Body")
                .char_count(4)
                .has_code(false)
                .is_tech(true)
                .field("Backend")
                .level("중급")
                .build());
    when(userClient.getPreference(12L))
        .thenAnswer(
            invocation -> {
              assertDatabaseReleased();
              return new UserPreferenceResponseDTO(List.of("Backend"), List.of("Java"), 1);
            });
    when(bookmarkClient.lookup(any()))
        .thenAnswer(
            invocation -> {
              assertDatabaseReleased();
              return List.of(post.getBlog_post_id());
            });
  }

  @Test
  void companyPreferenceCallDoesNotStartDatabaseTransaction() {
    assertThat(companies.getCompanyList(12L).getCompanies()).hasSize(1);
    verify(userClient).getPreference(12L);
    verifyNoInteractions(bookmarkClient);
  }

  @Test
  void postListReleasesDatabaseBeforeBothRemoteCalls() {
    var response = companies.getCompanyPostList(company.getCompany_id(), false, 12L);
    assertThat(response.posts()).hasSize(1);
    assertThat(response.posts().get(0).bookmarked()).isTrue();
    verify(userClient).getPreference(12L);
    verify(bookmarkClient).lookup(any());
  }

  @Test
  void postDetailReleasesDatabaseBeforeBookmarkCall() {
    var response = posts.getPost(post.getBlog_post_id(), 12L);
    assertThat(response.companyName()).isEqualTo("Alpha");
    assertThat(response.contentHtml()).contains("Body");
    assertThat(response.bookmarked()).isTrue();
    verify(bookmarkClient).lookup(any());
    verifyNoInteractions(userClient);
  }

  @Test
  void missingCompanyKeepsNotFoundWithoutRemoteCalls() {
    assertThatThrownBy(() -> companies.getCompanyPostList(Long.MAX_VALUE, true, 12L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    verifyNoInteractions(userClient, bookmarkClient);
  }

  @Test
  void emptyCompanyDoesNotRequireRemoteServices() {
    postRepository.deleteAll();
    var response = companies.getCompanyPostList(company.getCompany_id(), true, 12L);
    assertThat(response.posts()).isEmpty();
    assertThat(response.filter().totalCount()).isZero();
    verifyNoInteractions(userClient, bookmarkClient);
  }

  private void assertDatabaseReleased() throws SQLException {
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    assertThat(TransactionSynchronizationManager.hasResource(dataSource)).isFalse();
    // 외부 응답을 기다리는 동안 실제 DB 커넥션도 반납된 상태인지 검사한다.
    assertThat(
            dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections())
        .isZero();
  }
}
