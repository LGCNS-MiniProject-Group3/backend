package com.recu.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recu.content.database.entity.BlogPostEntity;
import com.recu.content.database.entity.BlogPostSectionEntity;
import com.recu.content.database.entity.BlogPostTagEntity;
import com.recu.content.database.entity.BlogPostTagPk;
import com.recu.content.database.entity.CompanyEntity;
import com.recu.content.database.entity.TechTagEntity;
import com.recu.content.database.entity.UserGuideFlagEntity;
import com.recu.content.database.entity.UserGuideFlagPk;
import com.recu.content.database.repository.BlogPostCategoryRepository;
import com.recu.content.database.repository.BlogPostRepository;
import com.recu.content.database.repository.BlogPostSectionRepository;
import com.recu.content.database.repository.BlogPostTagRepository;
import com.recu.content.database.repository.CompanyRepository;
import com.recu.content.database.repository.TechTagRepository;
import com.recu.content.database.repository.UserGuideFlagRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.jpa.open-in-view=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContentApiIntegrationTest {
  private static final HttpServer REMOTE;
  private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
  private static final AtomicInteger USER_STATUS = new AtomicInteger(200);
  private static final AtomicInteger BOOKMARK_STATUS = new AtomicInteger(200);
  private static final AtomicInteger VERSION = new AtomicInteger(1);
  private static final AtomicInteger BOOKMARK_DELAY = new AtomicInteger();
  private static final AtomicInteger USER_CALLS = new AtomicInteger();
  private static final List<String> BOOKMARK_BODIES = new CopyOnWriteArrayList<>();
  private static volatile String USER_BODY;
  private static volatile String BOOKMARK_BODY;

  static {
    try {
      REMOTE = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      REMOTE.setExecutor(EXECUTOR);
      REMOTE.createContext(
          "/internal/users/",
          exchange -> {
            USER_CALLS.incrementAndGet();
            send(
                exchange,
                USER_STATUS.get(),
                USER_BODY == null
                    ? "{\"userId\":12,\"jobFields\":[\"Backend\"],\"wantSkillNames\":[\"Kafka\"],\"profileVersion\":"
                        + VERSION.get()
                        + "}"
                    : USER_BODY);
          });
      REMOTE.createContext(
          "/internal/bookmarks/lookup",
          exchange -> {
            BOOKMARK_BODIES.add(
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
              Thread.sleep(BOOKMARK_DELAY.get());
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            send(exchange, BOOKMARK_STATUS.get(), BOOKMARK_BODY == null ? "[]" : BOOKMARK_BODY);
          });
      REMOTE.start();
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static void send(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
    try {
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
    } finally {
      exchange.close();
    }
  }

  @DynamicPropertySource
  static void endpoints(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.cloud.openfeign.client.config.user.url",
        () -> "http://127.0.0.1:" + REMOTE.getAddress().getPort());
    registry.add(
        "spring.cloud.openfeign.client.config.bookmark.url",
        () -> "http://127.0.0.1:" + REMOTE.getAddress().getPort());
    registry.add("spring.cloud.openfeign.client.config.bookmark.connectTimeout", () -> 300);
    registry.add("spring.cloud.openfeign.client.config.bookmark.readTimeout", () -> 300);
  }

  @AfterAll
  static void shutdownRemote() {
    REMOTE.stop(0);
    EXECUTOR.shutdownNow();
  }

  @Autowired MockMvc mvc;
  @Autowired TestRestTemplate http;
  @Autowired ObjectMapper json;
  @Autowired CompanyRepository companies;
  @Autowired BlogPostRepository posts;
  @Autowired BlogPostSectionRepository sections;
  @Autowired BlogPostTagRepository tags;
  @Autowired BlogPostCategoryRepository categories;
  @Autowired TechTagRepository techTags;
  @Autowired UserGuideFlagRepository flags;
  @Autowired EntityManager entityManager;
  CompanyEntity first;
  CompanyEntity empty;
  BlogPostEntity match;
  BlogPostEntity other;
  BlogPostEntity general;
  BlogPostEntity nonTech;
  TechTagEntity kafka;

  @BeforeEach
  void fixture() {
    USER_STATUS.set(200);
    BOOKMARK_STATUS.set(200);
    VERSION.set(1);
    BOOKMARK_DELAY.set(0);
    USER_BODY = null;
    BOOKMARK_BODY = null;
    USER_CALLS.set(0);
    BOOKMARK_BODIES.clear();
    flags.deleteAll();
    categories.deleteAll();
    tags.deleteAll();
    sections.deleteAll();
    posts.deleteAll();
    companies.deleteAll();
    techTags.deleteAll();
    first = companies.save(company("Alpha"));
    empty = companies.save(company("Empty"));
    kafka =
        techTags.save(
            TechTagEntity.builder().name("Kafka").field("Infra").selectable(true).build());
    techTags.save(TechTagEntity.builder().name("Redis").field("Infra").selectable(true).build());
    match = posts.save(article("match", "Backend", true, 1));
    other = posts.save(article("other", "Frontend", true, 3));
    general = posts.save(article("general", "General", true, 2));
    nonTech = posts.save(article("notice", "General", false, 4));
    for (var p : List.of(match, other, general, nonTech)) {
      tags.save(
          BlogPostTagEntity.builder()
              .id(new BlogPostTagPk(p.getBlog_post_id(), kafka.getTech_tag_id()))
              .blogPost(p)
              .techTag(kafka)
              .tag_rank(1)
              .build());
    }
    sections.save(
        BlogPostSectionEntity.builder()
            .blogPost(match)
            .seq(2)
            .heading("Second")
            .content_text("section two body")
            .char_count(16)
            .level("h2")
            .build());
    sections.save(
        BlogPostSectionEntity.builder()
            .blogPost(match)
            .seq(1)
            .heading("First")
            .content_text("section one body")
            .char_count(16)
            .level("h2")
            .build());
    flags.save(
        UserGuideFlagEntity.builder()
            .id(new UserGuideFlagPk(12L, match.getBlog_post_id()))
            .build());
  }

  private CompanyEntity company(String name) {
    return CompanyEntity.builder()
        .name(name)
        .summary("company summary")
        .feed_url("https://example.com/feed")
        .feed_type("FEED_FULL")
        .build();
  }

  private BlogPostEntity article(String slug, String field, boolean tech, int day) {
    return article(first, slug, field, tech, day);
  }

  private BlogPostEntity article(
      CompanyEntity company, String slug, String field, Boolean tech, int day) {
    return BlogPostEntity.builder()
        .company(company)
        .url("https://example.com/posts/" + slug)
        .title(slug)
        .published_at(LocalDateTime.of(2026, 10, day, 12, 0))
        .content_html(
            "<h2>First</h2><img src=\"../image.png\"><h2>Second</h2><a href=\"./next\">next</a>")
        .content_text("section one body section two body")
        .char_count(33)
        .has_code(false)
        .collected_at(LocalDateTime.of(2026, 10, 30, 12, 0))
        .is_tech(tech)
        .field(field)
        .level("중급")
        .summary("article summary")
        .build();
  }

  @Test
  void realHttpServerStartsAndServesDatabaseContent() {
    var result =
        http.getForEntity(
            "/internal/posts/" + match.getBlog_post_id() + "/guide-input", JsonNode.class);
    assertThat(result.getStatusCode().value()).isEqualTo(200);
    assertThat(result.getBody().path("sections").get(0).path("contentText").asText())
        .isEqualTo("section one body");
    var unauthorized = http.getForEntity("/api/companies", JsonNode.class);
    assertThat(unauthorized.getStatusCode().value()).isEqualTo(401);
    assertThat(unauthorized.getBody().path("code").asText()).isEqualTo("UNAUTHORIZED");
  }

  @Test
  void contextContainsOnlyContentTables() {
    Set<String> tables =
        entityManager.getMetamodel().getEntities().stream()
            .map(EntityType::getName)
            .collect(Collectors.toSet());
    assertThat(tables)
        .hasSize(7)
        .noneMatch(
            n -> n.equals("UserEntity") || n.equals("BookmarkEntity") || n.equals("AiGuideEntity"));
  }

  @Test
  void publicTechListPreservesEnvelopeAndDatabaseIds() throws Exception {
    mvc.perform(get("/api/tech-tags"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.techTags.length()").value(2))
        .andExpect(jsonPath("$.techTags[0].name").value("Kafka"))
        .andExpect(jsonPath("$.techTags[0].techTagId").value(kafka.getTech_tag_id().intValue()));
  }

  @Test
  void companyDetailNeedsNoUserAndCountsOnlyEligiblePosts() throws Exception {
    mvc.perform(get("/api/companies/{id}", first.getCompany_id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stats.postCount").value(3))
        .andExpect(jsonPath("$.stats.topCategories[0].name").exists());
    assertThat(USER_CALLS).hasValue(0);
    assertThat(BOOKMARK_BODIES).isEmpty();
  }

  @Test
  void emptyCompanyDetailWorks() throws Exception {
    mvc.perform(get("/api/companies/{id}", empty.getCompany_id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stats.postCount").value(0))
        .andExpect(jsonPath("$.stats.topSkills").isEmpty());
  }

  @Test
  void companiesUseRemotePreferenceAndKeepZeroMatchCompany() throws Exception {
    mvc.perform(get("/api/companies").header("X-User-Id", "12"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.companies.length()").value(2))
        .andExpect(jsonPath("$.companies[0].companyId").value(first.getCompany_id().intValue()))
        .andExpect(jsonPath("$.companies[0].matchedPostCount").value(3))
        .andExpect(jsonPath("$.companies[0].recommended").value(true))
        .andExpect(jsonPath("$.companies[1].matchedSkillCount").value(0));
    assertThat(USER_CALLS).hasValue(1);
  }

  @Test
  void personalizedListFiltersSortsAndLooksUpBookmarksInOneBatch() throws Exception {
    BOOKMARK_BODY = "[" + match.getBlog_post_id() + "]";
    mvc.perform(get("/api/companies/{id}/posts", first.getCompany_id()).header("X-User-Id", "12"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.filter.onlyMySkills").value(true))
        .andExpect(jsonPath("$.filter.totalCount").value(3))
        .andExpect(jsonPath("$.posts.length()").value(2))
        .andExpect(jsonPath("$.posts[0].postId").value(general.getBlog_post_id().intValue()))
        .andExpect(jsonPath("$.posts[1].bookmarked").value(true))
        .andExpect(jsonPath("$.posts[1].hasGuide").value(true));
    assertThat(BOOKMARK_BODIES).hasSize(1);
    JsonNode request = json.readTree(BOOKMARK_BODIES.get(0));
    assertThat(request.path("userId").asLong()).isEqualTo(12);
    assertThat(request.path("postIds").size()).isEqualTo(3);
  }

  @Test
  void disablingPersonalizationReturnsAllTechPostsNewestFirst() throws Exception {
    mvc.perform(
            get("/api/companies/{id}/posts", first.getCompany_id())
                .param("onlyMySkills", "false")
                .header("X-User-Id", "12"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.posts.length()").value(3))
        .andExpect(jsonPath("$.posts[0].postId").value(other.getBlog_post_id().intValue()));
  }

  @Test
  void clearedGuideProjectionDoesNotShowGuideFlag() throws Exception {
    VERSION.set(2);
    flags.deleteAll();
    mvc.perform(get("/api/companies/{id}/posts", first.getCompany_id()).header("X-User-Id", "12"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.posts[1].hasGuide").value(false));
  }

  @Test
  void missingHeaderIsUnauthorized() throws Exception {
    mvc.perform(get("/api/companies"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void malformedIdentityIsUnauthorized() throws Exception {
    for (String id : List.of("abc", "9223372036854775808", "12,13")) {
      mvc.perform(get("/api/companies").header("X-User-Id", id))
          .andExpect(status().isUnauthorized());
    }
  }

  @Test
  void profileRequiredIsPreserved() throws Exception {
    VERSION.set(0);
    mvc.perform(get("/api/companies").header("X-User-Id", "12"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PROFILE_REQUIRED"));
  }

  @Test
  void userOutageIsNotSilentlyTreatedAsAnEmptyProfile() throws Exception {
    USER_STATUS.set(503);
    USER_BODY = "{}";
    mvc.perform(get("/api/companies").header("X-User-Id", "12"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"));
  }

  @Test
  void userNotFoundUsesCurrentUpstreamErrorContract() throws Exception {
    USER_STATUS.set(404);
    USER_BODY = "{}";
    mvc.perform(get("/api/companies").header("X-User-Id", "12"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"));
  }

  @Test
  void malformedUserResponseFailsClearly() throws Exception {
    USER_BODY = "{}";
    mvc.perform(get("/api/companies").header("X-User-Id", "12"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"));
  }

  @Test
  void bookmarkServerErrorFallsBackForListAndDetail() throws Exception {
    BOOKMARK_STATUS.set(503);
    mvc.perform(get("/api/companies/{id}/posts", first.getCompany_id()).header("X-User-Id", "12"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.posts[1].bookmarked").value(false));
    mvc.perform(get("/api/posts/{id}", match.getBlog_post_id()).header("X-User-Id", "12"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bookmarked").value(false));
  }

  @Test
  void bookmarkTimeoutFallsBack() throws Exception {
    BOOKMARK_DELAY.set(700);
    BOOKMARK_BODY = "[" + match.getBlog_post_id() + "]";
    mvc.perform(get("/api/posts/{id}", match.getBlog_post_id()).header("X-User-Id", "12"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bookmarked").value(false));
  }

  @Test
  void failedOptionalBookmarkLookupDoesNotBreakContent() throws Exception {
    BOOKMARK_STATUS.set(403);
    mvc.perform(get("/api/posts/{id}", match.getBlog_post_id()).header("X-User-Id", "12"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bookmarked").value(false));
  }

  @Test
  void malformedOptionalBookmarkResponseUsesFallback() throws Exception {
    BOOKMARK_BODY = "{}";
    mvc.perform(get("/api/posts/{id}", match.getBlog_post_id()).header("X-User-Id", "12"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bookmarked").value(false));
  }

  @Test
  void postBodyKeepsAnchorsSectionsAndAbsoluteImages() throws Exception {
    var result =
        mvc.perform(get("/api/posts/{id}", match.getBlog_post_id()).header("X-User-Id", "12"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sections[0].heading").value("First"))
            .andReturn();
    String html =
        json.readTree(result.getResponse().getContentAsString()).path("contentHtml").asText();
    assertThat(html)
        .contains("id=\"section-1\"", "id=\"section-2\"", "https://example.com/image.png");
  }

  @Test
  void missingPostReturnsNotFoundWithoutRemoteCalls() throws Exception {
    mvc.perform(get("/api/posts/99999999").header("X-User-Id", "12"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    assertThat(BOOKMARK_BODIES).isEmpty();
  }

  @Test
  void internalTechLookupReportsMissingIds() throws Exception {
    mvc.perform(get("/internal/tech-tags").param("ids", kafka.getTech_tag_id() + ",99999999"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Kafka"))
        .andExpect(jsonPath("$.length()").value(1));
    assertThat(USER_CALLS).hasValue(0);
  }

  @Test
  void internalTechLookupRejectsBadInput() throws Exception {
    mvc.perform(get("/internal/tech-tags")).andExpect(status().isBadRequest());
    mvc.perform(get("/internal/tech-tags").param("ids", "-1")).andExpect(status().isBadRequest());
  }

  @Test
  void guideInputContainsFullBodiesInSequenceOrder() throws Exception {
    mvc.perform(get("/internal/posts/{id}/guide-input", match.getBlog_post_id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sections[0].seq").value(1))
        .andExpect(jsonPath("$.sections[0].contentText").value("section one body"))
        .andExpect(jsonPath("$.sections[1].seq").value(2));
    assertThat(USER_CALLS).hasValue(0);
    assertThat(BOOKMARK_BODIES).isEmpty();
  }

  @Test
  void internalCompaniesExposeCollectionSettings() throws Exception {
    mvc.perform(get("/internal/companies"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].feedUrl").value("https://example.com/feed"))
        .andExpect(jsonPath("$[0].feedType").value("FEED_FULL"));
  }

  @Test
  void urlsIncludeAlreadyCollectedNonTechPosts() throws Exception {
    mvc.perform(get("/internal/posts/urls").param("companyId", first.getCompany_id().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(4));
    mvc.perform(get("/internal/posts/urls").param("companyId", "99999999"))
        .andExpect(status().isNotFound());
  }

  @Test
  void summariesAreBatchedOrderedAndDoNotCallOtherServices() throws Exception {
    mvc.perform(
            post("/internal/posts/summaries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"postIds\":["
                        + other.getBlog_post_id()
                        + ","
                        + match.getBlog_post_id()
                        + "]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].postId").value(other.getBlog_post_id().intValue()))
        .andExpect(jsonPath("$[0].companyName").value("Alpha"))
        .andExpect(jsonPath("$[0].skills[0]").value("Kafka"));
    assertThat(USER_CALLS).hasValue(0);
    assertThat(BOOKMARK_BODIES).isEmpty();
    mvc.perform(
            post("/internal/posts/summaries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"postIds\":[99999999]}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void summaryValidationAndEmptyListWork() throws Exception {
    for (String body :
        List.of("{}", "{\"postIds\":null}", "{\"postIds\":[null]}", "{\"postIds\":[-1]}")) {
      mvc.perform(
              post("/internal/posts/summaries")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
    mvc.perform(
            post("/internal/posts/summaries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"postIds\":[]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  // ---- 기업 목록 · 글 목록 · 상세 응답 고정 (#117: 조회 방식을 바꿔도 응답은 같아야 한다) ----

  @Test
  void companyListCountsOnlyTechPostsAndKeepsEmptyCompany() throws Exception {
    JsonNode list = companyList();

    assertThat(names(list)).containsExactly("Alpha", "Empty");
    // 기술 글이 아닌 notice는 매칭 글 수 · 기술 글 수 모두에서 빠진다
    assertThat(counts(list.get(0))).containsExactly(1, 1, 3, 3);
    assertThat(skillCounts(list.get(0))).containsOnly(entry("Kafka", 3));
    assertThat(counts(list.get(1))).containsExactly(0, 1, 0, 0);
    assertThat(skillCounts(list.get(1))).isEmpty();
    assertThat(recommended(list)).containsExactly(true, false);
  }

  @Test
  void companyListCountsDistinctMatchedPostsAndSortsBySkillsThenPosts() throws Exception {
    TechTagEntity redis = techTags.findByName("Redis").orElseThrow();
    TechTagEntity javaTag =
        techTags.save(
            TechTagEntity.builder().name("Java").field("Backend").selectable(true).build());
    // Alpha: match 글 하나에 관심 기술 2개 (Kafka · Redis)
    tag(match, redis);
    // Beta: is_tech가 비어 있는 글도 기술 글, 관심 밖 기술(Java)은 세지 않는다
    CompanyEntity beta = companies.save(company("Beta"));
    BlogPostEntity betaPost = posts.save(article(beta, "beta", "Backend", null, 5));
    tag(betaPost, redis);
    tag(betaPost, javaTag);
    // Gamma: Beta와 매칭 기술 수(1)가 같고 매칭 글 수(2 > 1)가 많다
    CompanyEntity gamma = companies.save(company("Gamma"));
    tag(posts.save(article(gamma, "gamma-1", "Backend", true, 6)), kafka);
    tag(posts.save(article(gamma, "gamma-2", "Backend", true, 7)), kafka);
    USER_BODY =
        """
        {"userId":12,"jobFields":["Backend"],"wantSkillNames":["Kafka","Redis"],"profileVersion":1}
        """;

    JsonNode list = companyList();

    assertThat(names(list)).containsExactly("Alpha", "Gamma", "Beta", "Empty");
    // 기술별로는 Kafka 3 + Redis 1이지만, 겹친 글은 중복 없이 3편
    assertThat(counts(list.get(0))).containsExactly(2, 2, 3, 3);
    assertThat(skillCounts(list.get(0))).containsOnly(entry("Kafka", 3), entry("Redis", 1));
    assertThat(counts(list.get(1))).containsExactly(1, 2, 2, 2);
    assertThat(skillCounts(list.get(1))).containsOnly(entry("Kafka", 2));
    assertThat(counts(list.get(2))).containsExactly(1, 2, 1, 1);
    assertThat(skillCounts(list.get(2))).containsOnly(entry("Redis", 1));
    assertThat(counts(list.get(3))).containsExactly(0, 2, 0, 0);
    assertThat(recommended(list)).containsExactly(true, false, false, false);
  }

  @Test
  void companyListWithoutWantedSkillsStillCountsTechPosts() throws Exception {
    USER_BODY =
        """
        {"userId":12,"jobFields":["Backend"],"wantSkillNames":[],"profileVersion":1}
        """;

    JsonNode list = companyList();

    assertThat(names(list)).containsExactly("Alpha", "Empty");
    assertThat(counts(list.get(0))).containsExactly(0, 0, 0, 3);
    assertThat(skillCounts(list.get(0))).isEmpty();
    assertThat(counts(list.get(1))).containsExactly(0, 0, 0, 0);
  }

  @Test
  void companyDetailStatsComeFromTechPostsOnly() throws Exception {
    mvc.perform(get("/api/companies/{id}", first.getCompany_id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stats.postCount").value(3))
        .andExpect(jsonPath("$.stats.firstPublishedAt").value("2026-10-01"))
        .andExpect(jsonPath("$.stats.lastPublishedAt").value("2026-10-03"))
        .andExpect(
            jsonPath(
                "$.stats.topCategories[*].name",
                containsInAnyOrder("Backend", "Frontend", "General")))
        .andExpect(jsonPath("$.stats.topCategories[*].count", contains(1, 1, 1)))
        .andExpect(jsonPath("$.stats.topSkills.length()").value(1))
        .andExpect(jsonPath("$.stats.topSkills[0].name").value("Kafka"))
        .andExpect(jsonPath("$.stats.topSkills[0].count").value(3));
  }

  @Test
  void companyPostListKeepsPostFields() throws Exception {
    mvc.perform(
            get("/api/companies/{id}/posts", first.getCompany_id())
                .param("onlyMySkills", "false")
                .header("X-User-Id", "12"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.posts[*].title", contains("other", "general", "match")))
        .andExpect(jsonPath("$.posts[2].postId").value(match.getBlog_post_id().intValue()))
        .andExpect(jsonPath("$.posts[2].publishedAt").value("2026-10-01"))
        .andExpect(jsonPath("$.posts[2].categories", contains("Backend")))
        .andExpect(jsonPath("$.posts[2].level").value("중급"))
        .andExpect(jsonPath("$.posts[2].summary").value("article summary"))
        .andExpect(jsonPath("$.posts[2].skills", contains("Kafka")))
        .andExpect(jsonPath("$.posts[2].charCount").value(33))
        .andExpect(jsonPath("$.posts[2].sectionCount").value(2))
        .andExpect(jsonPath("$.posts[2].originalUrl").value("https://example.com/posts/match"));
  }

  private void tag(BlogPostEntity post, TechTagEntity techTag) {
    tags.save(
        BlogPostTagEntity.builder()
            .id(new BlogPostTagPk(post.getBlog_post_id(), techTag.getTech_tag_id()))
            .blogPost(post)
            .techTag(techTag)
            .tag_rank(1)
            .build());
  }

  private JsonNode companyList() throws Exception {
    String body =
        mvc.perform(get("/api/companies").header("X-User-Id", "12"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).path("companies");
  }

  private static List<String> names(JsonNode companies) {
    List<String> result = new ArrayList<>();
    for (JsonNode company : companies) {
      result.add(company.path("name").asText());
    }
    return result;
  }

  // [매칭 기술 수, 관심 기술 수, 매칭 글 수, 기술 글 수]
  private static List<Integer> counts(JsonNode company) {
    return Stream.of("matchedSkillCount", "totalSkillCount", "matchedPostCount", "totalPostCount")
        .map(field -> company.path(field).asInt())
        .toList();
  }

  // matchedSkills는 순서를 보장하지 않아 이름 → 글 수로 비교한다
  private static Map<String, Integer> skillCounts(JsonNode company) {
    Map<String, Integer> result = new HashMap<>();
    for (JsonNode skill : company.path("matchedSkills")) {
      result.put(skill.path("name").asText(), skill.path("postCount").asInt());
    }
    return result;
  }

  private static List<Boolean> recommended(JsonNode companies) {
    List<Boolean> result = new ArrayList<>();
    for (JsonNode company : companies) {
      result.add(company.path("recommended").asBoolean());
    }
    return result;
  }
}
