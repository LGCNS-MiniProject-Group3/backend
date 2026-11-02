-- 3·4번 담당: company, blog_post, blog_post_section, blog_post_category,
-- blog_post_tag, tech_tag, user_guide_flag
-- design.md 8장 · docs/reference/erd.md · content 엔티티 기준
-- user_guide_flag.user_id 는 user 회원 번호만 보관 (크로스 DB FK 없음)
USE recu_content;

CREATE TABLE IF NOT EXISTS tech_tag (
  tech_tag_id BIGINT       NOT NULL AUTO_INCREMENT,
  name        VARCHAR(50)  NOT NULL,
  aliases     VARCHAR(255) NULL,
  field       VARCHAR(30)  NULL,
  post_count  INT          NOT NULL DEFAULT 0,
  selectable  TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (tech_tag_id),
  UNIQUE KEY uk_tech_tag_name (name)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS company (
  company_id    BIGINT       NOT NULL AUTO_INCREMENT,
  name          VARCHAR(100) NOT NULL,
  summary       VARCHAR(200) NULL,
  main_business VARCHAR(300) NULL,
  source_url    VARCHAR(255) NULL,
  checked_at    DATETIME     NULL,
  logo_url      VARCHAR(500) NULL,
  feed_url      VARCHAR(255) NOT NULL,
  feed_type     VARCHAR(20)  NOT NULL,
  PRIMARY KEY (company_id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS blog_post (
  blog_post_id  BIGINT       NOT NULL AUTO_INCREMENT,
  company_id    BIGINT       NOT NULL,
  external_id   VARCHAR(255) NULL,
  url           VARCHAR(500) NOT NULL,
  title         VARCHAR(300) NOT NULL,
  published_at  DATETIME     NOT NULL,
  content_html  MEDIUMTEXT   NOT NULL,
  content_text  MEDIUMTEXT   NOT NULL,
  char_count    INT          NOT NULL,
  has_code      TINYINT(1)   NOT NULL,
  collected_at  DATETIME     NOT NULL,
  is_tech       TINYINT(1)   NULL,
  field         VARCHAR(10)  NULL,
  level         VARCHAR(4)   NULL,
  summary       VARCHAR(300) NULL,
  PRIMARY KEY (blog_post_id),
  UNIQUE KEY uk_blog_post_url (url),
  KEY idx_blog_post_company_id (company_id),
  CONSTRAINT fk_blog_post_company
    FOREIGN KEY (company_id) REFERENCES company (company_id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS blog_post_section (
  section_id    BIGINT       NOT NULL AUTO_INCREMENT,
  blog_post_id  BIGINT       NOT NULL,
  seq           INT          NOT NULL,
  heading       VARCHAR(300) NOT NULL,
  level         VARCHAR(4)   NULL,
  content_text  MEDIUMTEXT   NOT NULL,
  char_count    INT          NOT NULL,
  PRIMARY KEY (section_id),
  UNIQUE KEY uk_blog_post_section (blog_post_id, seq),
  CONSTRAINT fk_blog_post_section_post
    FOREIGN KEY (blog_post_id) REFERENCES blog_post (blog_post_id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS blog_post_category (
  blog_post_id BIGINT       NOT NULL,
  name         VARCHAR(255) NOT NULL,
  PRIMARY KEY (blog_post_id, name),
  CONSTRAINT fk_blog_post_category_post
    FOREIGN KEY (blog_post_id) REFERENCES blog_post (blog_post_id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS blog_post_tag (
  blog_post_id BIGINT NOT NULL,
  tech_tag_id  BIGINT NOT NULL,
  tag_rank     INT    NULL,
  PRIMARY KEY (blog_post_id, tech_tag_id),
  CONSTRAINT fk_blog_post_tag_post
    FOREIGN KEY (blog_post_id) REFERENCES blog_post (blog_post_id),
  CONSTRAINT fk_blog_post_tag_tech
    FOREIGN KEY (tech_tag_id) REFERENCES tech_tag (tech_tag_id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- B·C 메시지 사본. "가이드 있음" 표시 (V1: profile_version 없음)
CREATE TABLE IF NOT EXISTS user_guide_flag (
  user_id      BIGINT NOT NULL,
  blog_post_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, blog_post_id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
