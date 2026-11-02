-- 2번 담당: app_user, user_job_field, user_skill
-- design.md 8장 · docs/reference/erd.md · user 엔티티 기준
-- 엔티티를 바꾼 PR에 이 파일의 CREATE TABLE 도 같이 수정한다
-- user_skill.tech_tag_id 는 content 기술 번호만 보관 (크로스 DB FK 없음)
USE recu_user;

CREATE TABLE IF NOT EXISTS app_user (
  user_id         BIGINT       NOT NULL AUTO_INCREMENT,
  google_sub      VARCHAR(100) NOT NULL,
  name            VARCHAR(50)  NOT NULL,
  career_years    INT          NULL,
  profile_version INT          NOT NULL DEFAULT 0,
  refresh_token   VARCHAR(255) NULL,
  role            VARCHAR(255) NOT NULL DEFAULT 'USER',
  created_at      DATE         NOT NULL,
  updated_at      DATE         NOT NULL,
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_app_user_google_sub (google_sub)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_job_field (
  id         INT         NOT NULL AUTO_INCREMENT,
  user_id    BIGINT      NOT NULL,
  job_field  VARCHAR(10) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_user_job_field_user_id (user_id),
  CONSTRAINT fk_user_job_field_user
    FOREIGN KEY (user_id) REFERENCES app_user (user_id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_skill (
  user_id     BIGINT      NOT NULL,
  tech_tag_id BIGINT      NOT NULL,
  skill_type  VARCHAR(10) NOT NULL,
  PRIMARY KEY (user_id, tech_tag_id),
  CONSTRAINT fk_user_skill_user
    FOREIGN KEY (user_id) REFERENCES app_user (user_id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
