-- 가계부/일정/기록 기본 보기를 INLINE -> CALENDAR(월간)로 변경. V2가 이미 운영 적용됐으므로 신규 마이그레이션으로 추가.
-- 이 기능은 도입 직후라 실사용자가 명시적으로 값을 바꾼 적이 없다고 보고, 기존 행도 함께 갱신한다.
ALTER TABLE person_setting
    MODIFY COLUMN ledger_default_view   VARCHAR(20) NOT NULL DEFAULT 'CALENDAR',
    MODIFY COLUMN schedule_default_view VARCHAR(20) NOT NULL DEFAULT 'CALENDAR',
    MODIFY COLUMN diary_default_view    VARCHAR(20) NOT NULL DEFAULT 'CALENDAR';

UPDATE person_setting
SET ledger_default_view = 'CALENDAR',
    schedule_default_view = 'CALENDAR',
    diary_default_view = 'CALENDAR'
WHERE ledger_default_view = 'INLINE'
  AND schedule_default_view = 'INLINE'
  AND diary_default_view = 'INLINE';
