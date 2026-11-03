package com.recu.content.features.ingest.service;

import com.recu.common.kafka.event.PostCollectedEvent;
import com.recu.common.web.error.BusinessException;
import com.recu.common.web.error.CommonErrorCode;
import com.recu.content.database.entity.BlogPostCategoryEntity;
import com.recu.content.database.entity.BlogPostCategoryPk;
import com.recu.content.database.entity.BlogPostEntity;
import com.recu.content.database.entity.BlogPostSectionEntity;
import com.recu.content.database.entity.BlogPostTagEntity;
import com.recu.content.database.entity.BlogPostTagPk;
import com.recu.content.database.entity.TechTagEntity;
import com.recu.content.database.repository.BlogPostCategoryRepository;
import com.recu.content.database.repository.BlogPostRepository;
import com.recu.content.database.repository.BlogPostSectionRepository;
import com.recu.content.database.repository.BlogPostTagRepository;
import com.recu.content.database.repository.CompanyRepository;
import com.recu.content.database.repository.TechTagRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 수집·분류가 끝난 A 이벤트를 저장한다. 파일 입력용 재분할·태그 재분류는 수행하지 않는다. */
@Service
@RequiredArgsConstructor
public class PostCollectedIngestService {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final CompanyRepository companies;
  private final BlogPostRepository posts;
  private final BlogPostSectionRepository sections;
  private final BlogPostCategoryRepository categories;
  private final BlogPostTagRepository postTags;
  private final TechTagRepository techTags;

  /** 새 글이면 true, 이미 저장된 URL이면 변경 없이 false를 반환한다. */
  @Transactional
  public boolean ingest(PostCollectedEvent event) {
    if (posts.existsByUrl(event.url())) {
      return false;
    }
    var company =
        companies
            .findById(event.companyId())
            .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "companyId"));
    var post =
        posts.save(
            BlogPostEntity.builder()
                .company(company)
                .url(event.url())
                .external_id(event.externalId())
                .title(event.title())
                .published_at(event.publishedAt().atZoneSameInstant(SEOUL).toLocalDateTime())
                .content_html(event.contentHtml())
                .content_text(event.contentText())
                .char_count(event.contentText().length())
                .has_code(event.hasCode())
                .collected_at(LocalDateTime.now(SEOUL))
                .is_tech(event.isTech())
                .field(event.field())
                .level(event.level())
                .summary(event.summary())
                .build());

    for (String name : new LinkedHashSet<>(event.categories())) {
      categories.save(
          BlogPostCategoryEntity.builder()
              .id(new BlogPostCategoryPk(post.getBlog_post_id(), name))
              .blogPost(post)
              .build());
    }
    for (var tag : event.tags()) {
      // 별칭 추측 없이 이름으로 재사용한다. 새 기술의 나머지 값은 엔티티·스키마 기본값이다.
      var tech =
          techTags
              .findByName(tag.name())
              .orElseGet(() -> techTags.save(TechTagEntity.builder().name(tag.name()).build()));
      postTags.save(
          BlogPostTagEntity.builder()
              .id(new BlogPostTagPk(post.getBlog_post_id(), tech.getTech_tag_id()))
              .blogPost(post)
              .techTag(tech)
              .tag_rank(tag.rank())
              .build());
    }
    for (var section : event.sections()) {
      sections.save(
          BlogPostSectionEntity.builder()
              .blogPost(post)
              .seq(section.seq())
              .heading(section.heading())
              .level(section.level())
              .content_text(section.contentText())
              .char_count(section.contentText().length())
              .build());
    }
    return true;
  }
}
