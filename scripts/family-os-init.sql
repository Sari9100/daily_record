-- Family OS DB 초기화 (mysql-server-v2 재사용, family_os 스키마 추가)
-- 설계: scratchpad/phase-drafts.md (Phase 1)
-- 스키마(테이블)는 family-os-api 기동 시 Flyway(V1__init.sql)가 생성. 여기서는 DB/계정만.
-- 비밀번호는 이 파일에 박지 않고 .env 의 DB_PASSWORD 를 주입해 실행(유출 방지):
--
--   set -a; . ./.env; set +a
--   sed "s/__DB_PASSWORD__/$DB_PASSWORD/" scripts/family-os-init.sql \
--     | docker exec -i mysql-server-v2 mysql -uroot -p<ROOT_PW>

CREATE DATABASE IF NOT EXISTS family_os
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'family_os'@'%' IDENTIFIED BY '__DB_PASSWORD__';
GRANT ALL PRIVILEGES ON family_os.* TO 'family_os'@'%';  -- family_os 한정(stockdb_v2 접근 불가)
FLUSH PRIVILEGES;
