package com.recu.content.features.ingest.service;

import com.recu.content.database.entity.BlogPostCategoryEntity;
import com.recu.content.database.entity.BlogPostCategoryPk;
import com.recu.content.database.entity.BlogPostEntity;
import com.recu.content.database.entity.BlogPostSectionEntity;
import com.recu.content.database.entity.BlogPostTagEntity;
import com.recu.content.database.entity.BlogPostTagPk;
import com.recu.content.database.entity.CompanyEntity;
import com.recu.content.database.entity.TechTagEntity;
import com.recu.content.database.repository.BlogPostCategoryRepository;
import com.recu.content.database.repository.BlogPostRepository;
import com.recu.content.database.repository.BlogPostSectionRepository;
import com.recu.content.database.repository.BlogPostTagRepository;
import com.recu.content.database.repository.CompanyRepository;
import com.recu.content.database.repository.TechTagRepository;
import com.recu.content.features.ingest.domain.dto.ImportedPost;
import com.recu.content.features.ingest.domain.dto.TechDictionary;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 읽어 온 글을 표에 넣는다. 파일에서 왔든 웹에서 수집했든 저장은 이 한 곳을 지난다.
 *
 * <p>넣는 순서가 정해져 있다. 글에 기업 번호가 들어가므로 기업이 먼저고, 태그가 기술 번호를 쓰므로 기술 사전이 그 다음이다.
 *
 * <p>같은 글은 url 로 걸러낸다. 다시 돌려도 행이 늘지 않는다.
 */
@Service
public class PostIngestService {

  private static final Logger log = LoggerFactory.getLogger(PostIngestService.class);

  /** 화면에서 고를 수 있는 기술과 그 계열. 노션 API 명세서에 확정된 20종. */
  private static final Map<String, String> SELECTABLE = new LinkedHashMap<>();

  static {
    for (String s : new String[] {"Spring", "Spring Boot", "MySQL", "Kotlin", "SSE"}) {
      SELECTABLE.put(s, "Backend");
    }
    for (String s : new String[] {"React", "JavaScript", "Vite"}) {
      SELECTABLE.put(s, "Frontend");
    }
    for (String s :
        new String[] {"LLM", "RAG", "MCP", "Claude Code", "Machine Learning", "Airflow"}) {
      SELECTABLE.put(s, "Data");
    }
    for (String s : new String[] {"Kafka", "Kubernetes", "Redis", "AWS"}) {
      SELECTABLE.put(s, "Infra");
    }
    for (String s : new String[] {"Android", "Flutter"}) {
      SELECTABLE.put(s, "Mobile");
    }
  }

  /** 기업별 수집 주소. 수집기가 쓰는 것과 같은 값. */
  private static final List<String[]> COMPANIES =
      List.of(
          new String[] {"올리브영", "https://oliveyoung.tech/rss.xml", "FEED_FULL"},
          new String[] {"인프랩", "https://tech.inflab.com/rss.xml", "FEED_FULL"},
          new String[] {"SK플래닛", "https://techtopic.skplanet.com/rss.xml", "FEED_FULL"},
          new String[] {
            "토스",
            "https://api-public.toss.im/api-public/v3/ipd-thor/api/v1/workspaces/15/posts",
            "JSON_LIST_PAGE"
          },
          new String[] {"우아한형제들", "https://techblog.woowahan.com/feed/", "FEED_PAGED"},
          new String[] {"컬리", "https://helloworld.kurly.com/rss.xml", "FEED_LIST"},
          new String[] {"네이버 D2", "https://d2.naver.com/api/v1/contents", "JSON_API"},
          new String[] {"LY(라인)", "https://techblog.lycorp.co.jp/sitemap-0.xml", "SITEMAP"});

  private final CompanyRepository companies;
  private final TechTagRepository techTags;
  private final BlogPostRepository posts;
  private final BlogPostSectionRepository sections;
  private final BlogPostCategoryRepository categories;
  private final BlogPostTagRepository tags;
  private final SectionSplitter splitter;

  public PostIngestService(
      CompanyRepository companies,
      TechTagRepository techTags,
      BlogPostRepository posts,
      BlogPostSectionRepository sections,
      BlogPostCategoryRepository categories,
      BlogPostTagRepository tags,
      SectionSplitter splitter) {
    this.companies = companies;
    this.techTags = techTags;
    this.posts = posts;
    this.sections = sections;
    this.categories = categories;
    this.tags = tags;
    this.splitter = splitter;
  }

  /** 넣은 결과. 화면과 로그에 그대로 쓴다. */
  public record IngestResult(
      int companyCount,
      int techTagCount,
      int newPosts,
      int skipped,
      int sectionCount,
      int tagCount) {}

  @Transactional
  public IngestResult ingest(List<ImportedPost> rows, TechDictionary dictionary) {
    TechNameResolver resolver = new TechNameResolver(dictionary);

    Map<String, CompanyEntity> companyByName = saveCompanies();
    Map<String, TechTagEntity> tagByName = saveTechTags(resolver);

    int newPosts = 0;
    int skipped = 0;
    int sectionCount = 0;
    int tagCount = 0;

    for (ImportedPost row : rows) {
      if (row.url() == null || posts.existsByUrl(row.url())) {
        skipped++;
        continue;
      }
      CompanyEntity company = companyByName.get(row.company());
      if (company == null) {
        log.warn("모르는 기업이라 건너뜁니다: {} ({})", row.company(), row.url());
        skipped++;
        continue;
      }

      BlogPostEntity post = posts.save(toPost(row, company));
      newPosts++;
      sectionCount += saveSections(row, post);
      saveCategories(row, post);
      tagCount += saveTags(row, post, resolver, tagByName);
    }

    IngestResult result =
        new IngestResult(
            companyByName.size(), tagByName.size(), newPosts, skipped, sectionCount, tagCount);
    log.info("적재 완료 {}", result);
    return result;
  }

  /** 화면에서 고를 수 있는 기술 20종 이름. 웹 수집이 사전을 만들 때 대표 표기로 쓴다. */
  public static Set<String> selectableNames() {
    return Collections.unmodifiableSet(SELECTABLE.keySet());
  }

  /** 기업 소개 4칸과 로고. 기업 상세 화면(04)과 기업 목록(03)에 나간다. */
  record CompanyIntro(String summary, String mainBusiness, String sourceUrl, String logoUrl) {}

  /** 소개 · 로고를 공식 페이지에서 확인한 날. */
  private static final LocalDateTime INTRO_CHECKED_AT = LocalDate.of(2026, 9, 28).atStartOfDay();

  /** 기업별 소개. 출처(sourceUrl)는 기술 블로그 주소, 로고는 각 회사 공식 이미지 주소다. */
  private static final Map<String, CompanyIntro> INTROS =
      Map.of(
          "올리브영",
          new CompanyIntro(
              "헬스&뷰티 스토어를 운영하는 옴니채널 유통기업",
              "H&B 스토어 · 온라인몰 · 자체 브랜드 · 글로벌 사업",
              "https://oliveyoung.tech",
              "https://oliveyoung.tech/icons/icon-512x512.png"),
          "인프랩",
          new CompanyIntro(
              "IT 실무 교육 플랫폼 인프런을 운영하는 에듀테크 기업",
              "온라인 강의(인프런) · IT 채용(랠릿)",
              "https://tech.inflab.com",
              "https://cdn.inflearn.com/dist/icon-512x512.png"),
          "SK플래닛",
          new CompanyIntro(
              "OK캐쉬백 등을 운영하는 데이터·마케팅 플랫폼 기업",
              "마케팅 플랫폼(OK캐쉬백·시럽) · 광고·리테일미디어 · 마이데이터",
              "https://techtopic.skplanet.com",
              "https://techtopic.skplanet.com/icons/icon-512x512.png"),
          "토스",
          new CompanyIntro(
              "송금·결제·은행·증권을 한 앱에서 제공하는 핀테크 기업",
              "간편송금·결제 · 토스뱅크 · 토스증권 · 토스페이먼츠 · 토스인슈어런스",
              "https://toss.tech",
              "https://static.toss.im/assets/toss-im/asset/favicon/favicon-196x196.png"),
          "우아한형제들",
          new CompanyIntro(
              "배달의민족 앱을 운영하는 푸드테크 기업",
              "음식 배달 · 퀵커머스(B마트) · B2B 식자재(배민상회) · 로봇배달",
              "https://techblog.woowahan.com",
              "https://woowahan-cdn.woowahan.com/favicon/ko/android-chrome-512x512.png"),
          "컬리",
          new CompanyIntro(
              "신선식품을 새벽배송하는 온라인 마켓",
              "신선식품 새벽배송(샛별배송) · 생산자 직거래 · 풀콜드체인 물류",
              "https://helloworld.kurly.com",
              "https://res.kurly.com/images/marketkurly/logo/logo_sns_marketkurly.jpg"),
          "네이버 D2",
          new CompanyIntro(
              "네이버 개발자들이 운영하는 기술 블로그. 네이버는 검색·커머스·AI 서비스 기업",
              "검색·포털 · 커머스 · 네이버페이 · 클라우드 · AI(하이퍼클로바X)",
              "https://d2.naver.com",
              "https://d2.naver.com/favicon.ico"),
          "LY(라인)",
          new CompanyIntro(
              "메신저 LINE과 야후재팬을 운영하는 일본 IT 기업",
              "메신저(LINE) · 포털(Yahoo! JAPAN) · 광고 · 이커머스",
              "https://techblog.lycorp.co.jp/ko",
              "https://www.lycorp.co.jp/assets/images/apple-touch-icon.png"));

  /** 기업 8곳을 표에 준비한다. 웹 수집은 이 행의 수집 주소를 읽어 돈다. */
  @Transactional
  public Map<String, CompanyEntity> ensureCompanies() {
    return saveCompanies();
  }

  /** 기업 8곳. 이름이 같은 행이 있으면 그대로 쓰고, 소개 · 로고 칸이 비어 있으면 채운다. */
  private Map<String, CompanyEntity> saveCompanies() {
    Map<String, CompanyEntity> byName = new LinkedHashMap<>();
    for (String[] c : COMPANIES) {
      CompanyIntro intro = INTROS.get(c[0]);
      CompanyEntity entity =
          companies
              .findByName(c[0])
              .map(found -> fillIntro(found, intro))
              .orElseGet(
                  () ->
                      companies.save(
                          CompanyEntity.builder()
                              .name(c[0])
                              .feed_url(c[1])
                              .feed_type(c[2])
                              .summary(intro.summary())
                              .main_business(intro.mainBusiness())
                              .source_url(intro.sourceUrl())
                              .checked_at(INTRO_CHECKED_AT)
                              .logo_url(intro.logoUrl())
                              .build()));
      byName.put(c[0], entity);
    }
    return byName;
  }

  /** 이미 있는 행의 빈 칸만 채운다. 사람이 고친 값은 덮어쓰지 않는다. */
  private CompanyEntity fillIntro(CompanyEntity found, CompanyIntro intro) {
    boolean complete =
        found.getSummary() != null
            && found.getMain_business() != null
            && found.getSource_url() != null
            && found.getChecked_at() != null
            && found.getLogo_url() != null;
    if (complete) {
      return found;
    }
    return companies.save(
        CompanyEntity.builder()
            .company_id(found.getCompany_id())
            .name(found.getName())
            .feed_url(found.getFeed_url())
            .feed_type(found.getFeed_type())
            .summary(orElse(found.getSummary(), intro.summary()))
            .main_business(orElse(found.getMain_business(), intro.mainBusiness()))
            .source_url(orElse(found.getSource_url(), intro.sourceUrl()))
            .checked_at(found.getChecked_at() != null ? found.getChecked_at() : INTRO_CHECKED_AT)
            .logo_url(orElse(found.getLogo_url(), intro.logoUrl()))
            .build());
  }

  private static String orElse(String value, String fallback) {
    return value != null ? value : fallback;
  }

  /** 기술 사전 전부. 20종만 화면에서 고를 수 있게 표시한다. */
  private Map<String, TechTagEntity> saveTechTags(TechNameResolver resolver) {
    Map<String, String> aliasNote = new LinkedHashMap<>();
    TechNameResolver.extraAliases().forEach((from, to) -> aliasNote.put(to, from));

    Set<String> names = new LinkedHashSet<>(resolver.allNames());
    names.addAll(SELECTABLE.keySet());

    Map<String, TechTagEntity> byName = new LinkedHashMap<>();
    for (String name : names) {
      if (name == null || name.isBlank() || name.length() > 50) {
        continue;
      }
      String resolved = resolver.resolve(name);
      final String stored = resolved == null ? name : resolved;
      if (byName.containsKey(stored)) {
        continue;
      }
      TechTagEntity entity =
          techTags
              .findByName(stored)
              .orElseGet(
                  () ->
                      techTags.save(
                          TechTagEntity.builder()
                              .name(stored)
                              .aliases(aliasNote.get(stored))
                              .field(SELECTABLE.get(stored))
                              .selectable(SELECTABLE.containsKey(stored))
                              .build()));
      byName.put(stored, entity);
    }
    return byName;
  }

  private BlogPostEntity toPost(ImportedPost row, CompanyEntity company) {
    ImportedPost.Ai ai = row.ai();
    String text = row.body_text() == null ? "" : row.body_text();
    return BlogPostEntity.builder()
        .company(company)
        .external_id(cut(row.external_id(), 255))
        .url(cut(row.url(), 500))
        .title(cut(row.title() == null ? "제목 없음" : row.title(), 300))
        .published_at(toDateTime(row.published_at()))
        .content_html(row.body_html() == null ? "" : row.body_html())
        .content_text(text)
        .char_count(row.char_count() == null ? text.length() : row.char_count())
        .has_code(Boolean.TRUE.equals(row.has_code()))
        .collected_at(LocalDateTime.now())
        .is_tech(ai == null ? null : ai.is_tech())
        .field(ai == null ? null : cut(ai.field(), 10))
        .level(ai == null ? null : cut(ai.level(), 4))
        .summary(ai == null ? null : cut(ai.summary(), 300))
        .build();
  }

  private int saveSections(ImportedPost row, BlogPostEntity post) {
    List<BlogPostSectionEntity> list = new ArrayList<>();
    for (SectionSplitter.Section s : splitter.split(row)) {
      list.add(
          BlogPostSectionEntity.builder()
              .blogPost(post)
              .seq(s.seq())
              .heading(s.heading())
              .level(cut(s.level(), 4))
              .content_text(s.contentText())
              .char_count(s.contentText().length())
              .build());
    }
    sections.saveAll(list);
    return list.size();
  }

  /** 사이트가 붙인 분류어. 같은 글에 같은 이름이 두 번 오면 한 번만 넣는다. */
  private void saveCategories(ImportedPost row, BlogPostEntity post) {
    if (row.source_categories() == null) {
      return;
    }
    Set<String> seen = new LinkedHashSet<>();
    List<BlogPostCategoryEntity> list = new ArrayList<>();
    for (String name : row.source_categories()) {
      if (name == null || name.isBlank()) {
        continue;
      }
      String value = cut(name.trim(), 100);
      if (!seen.add(value)) {
        continue;
      }
      list.add(
          BlogPostCategoryEntity.builder()
              .id(new BlogPostCategoryPk(post.getBlog_post_id(), value))
              .blogPost(post)
              .build());
    }
    categories.saveAll(list);
  }

  /** 글의 핵심 기술. 사전에 없는 이름은 버리고, 순서대로 1·2·3 을 매긴다. */
  private int saveTags(
      ImportedPost row,
      BlogPostEntity post,
      TechNameResolver resolver,
      Map<String, TechTagEntity> tagByName) {
    ImportedPost.Ai ai = row.ai();
    if (ai == null || ai.core_techs() == null) {
      return 0;
    }
    Set<String> seen = new LinkedHashSet<>();
    List<BlogPostTagEntity> list = new ArrayList<>();
    int rank = 1;
    for (String raw : ai.core_techs()) {
      String name = resolver.resolve(raw);
      if (name == null || !seen.add(name)) {
        continue;
      }
      TechTagEntity tag = tagByName.get(name);
      if (tag == null) {
        continue;
      }
      list.add(
          BlogPostTagEntity.builder()
              .id(new BlogPostTagPk(post.getBlog_post_id(), tag.getTech_tag_id()))
              .blogPost(post)
              .techTag(tag)
              .tag_rank(rank++)
              .build());
    }
    tags.saveAll(list);
    return list.size();
  }

  /** 수집 파일은 날짜만 있거나 시각까지 있다. 표는 시각까지 받으므로 날짜만 오면 0시로 둔다. */
  private LocalDateTime toDateTime(String value) {
    if (value == null || value.isBlank()) {
      return LocalDateTime.now();
    }
    String v = value.trim();
    try {
      return LocalDateTime.parse(v);
    } catch (DateTimeParseException ignored) {
      // 날짜만 온 경우 아래에서 다시 시도한다
    }
    try {
      return LocalDate.parse(v.length() > 10 ? v.substring(0, 10) : v).atStartOfDay();
    } catch (DateTimeParseException e) {
      log.warn("발행 시각을 읽지 못해 현재 시각으로 둡니다: {}", value);
      return LocalDateTime.now();
    }
  }

  private String cut(String s, int max) {
    if (s == null) {
      return null;
    }
    String t = s.trim();
    return t.length() <= max ? t : t.substring(0, max);
  }
}
