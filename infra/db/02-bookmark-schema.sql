-- 2·4번 담당: bookmark, user_guide_flag
-- design.md 8장 · docs/reference/erd.md · bookmark 엔티티 기준
-- user_id · blog_post_id 는 다른 서비스 번호만 보관 (크로스 DB FK 없음)
USE recu_bookmark;

CREATE TABLE IF NOT EXISTS bookmark (
  bookmark_id  BIGINT   NOT NULL AUTO_INCREMENT,
  user_id      BIGINT   NOT NULL,
  blog_post_id BIGINT   NOT NULL,
  created_at   DATETIME NOT NULL,
  PRIMARY KEY (bookmark_id),
  UNIQUE KEY uk_bookmark (user_id, blog_post_id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- content 와 동일한 사본 테이블 (B·C 수신용). V1: profile_version 없음
CREATE TABLE IF NOT EXISTS user_guide_flag (
  user_id      BIGINT NOT NULL,
  blog_post_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, blog_post_id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
