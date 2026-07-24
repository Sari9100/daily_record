-- 가계부/일정/기록 화면의 기본 보기(인라인/캘린더) 개인 설정. V1__init.sql은 운영 적용 후 수정 금지 — 신규 마이그레이션으로 추가.
ALTER TABLE person_setting
    ADD COLUMN ledger_default_view   VARCHAR(20) NOT NULL DEFAULT 'INLINE',  -- INLINE / CALENDAR
    ADD COLUMN schedule_default_view VARCHAR(20) NOT NULL DEFAULT 'INLINE',  -- INLINE / CALENDAR
    ADD COLUMN diary_default_view    VARCHAR(20) NOT NULL DEFAULT 'INLINE'; -- INLINE / CALENDAR
