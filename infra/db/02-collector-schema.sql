-- 5번 담당: collect_job (design.md 8장)
USE recu_collector;

CREATE TABLE IF NOT EXISTS collect_job (
  job_id         VARCHAR(40)  NOT NULL,
  company_id     BIGINT       NULL,
  status         VARCHAR(20)  NOT NULL,
  started_at     DATETIME     NOT NULL,
  finished_at    DATETIME     NULL,
  new_post_count INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (job_id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;