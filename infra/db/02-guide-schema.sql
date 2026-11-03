-- 6번 담당: ai_guide
-- design.md 8장 · docs/reference/erd.md · guide 엔티티 기준
-- user_id · blog_post_id 는 다른 서비스 번호만 보관 (크로스 DB FK 없음)
USE recu_guide;

CREATE TABLE IF NOT EXISTS ai_guide (
  ai_guide_id     BIGINT       NOT NULL AUTO_INCREMENT,
  user_id         BIGINT       NOT NULL,
  blog_post_id    BIGINT       NOT NULL,
  profile_version INT          NOT NULL,
  prompt_version  VARCHAR(20)  NOT NULL,
  focus_sections  TEXT         NULL,
  section_badges  TEXT         NULL,
  section1_text   TEXT         NOT NULL,
  section2_text   TEXT         NOT NULL,
  section3_text   TEXT         NOT NULL,
  created_at      DATETIME     NOT NULL,
  PRIMARY KEY (ai_guide_id),
  UNIQUE KEY uk_ai_guide (user_id, blog_post_id, profile_version, prompt_version)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
