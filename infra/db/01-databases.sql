-- 5번 담당
-- DB 5개 + 서비스별 계정/권한
-- 계정 이름 = config username (recu_user 등)
-- 로컬 서비스 비밀번호는 팀 합의 값 (아래 IDENTIFIED BY). 1번이 설정 저장소 cipher 에 동일 값 반영.
-- 서비스 계정에는 데이터 권한만. CREATE/DROP/ALTER 없음 (code-convention)

CREATE DATABASE IF NOT EXISTS recu_user
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS recu_content
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS recu_collector
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS recu_guide
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS recu_bookmark
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'recu_user'@'%' IDENTIFIED BY 'recu_local';
CREATE USER IF NOT EXISTS 'recu_content'@'%' IDENTIFIED BY 'recu_local';
CREATE USER IF NOT EXISTS 'recu_collector'@'%' IDENTIFIED BY 'recu_local';
CREATE USER IF NOT EXISTS 'recu_guide'@'%' IDENTIFIED BY 'recu_local';
CREATE USER IF NOT EXISTS 'recu_bookmark'@'%' IDENTIFIED BY 'recu_local';

GRANT SELECT, INSERT, UPDATE, DELETE ON recu_user.* TO 'recu_user'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON recu_content.* TO 'recu_content'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON recu_collector.* TO 'recu_collector'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON recu_guide.* TO 'recu_guide'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON recu_bookmark.* TO 'recu_bookmark'@'%';

FLUSH PRIVILEGES;