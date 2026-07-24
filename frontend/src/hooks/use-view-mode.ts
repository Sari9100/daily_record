import { useEffect, useRef, useState } from 'react';

import { useSettings, type PersonSetting, type ViewMode } from '@/api/settings';

type ViewModeScreen = 'ledger' | 'schedule' | 'diary';

const SETTING_KEY: Record<ViewModeScreen, keyof PersonSetting> = {
  ledger: 'ledgerDefaultView',
  schedule: 'scheduleDefaultView',
  diary: 'diaryDefaultView',
};

/**
 * 화면(가계부/일정/기록)의 인라인↔캘린더 전환 상태.
 * 개인 설정(§4-2)의 화면별 기본값을 최초 로드 시 1회 반영하고, 이후 화면 내 전환은 로컬 상태로만 유지한다
 * (설정 화면에서 바꿔야 "기본값"이 서버에 저장됨 — 화면 내 토글은 세션 한정).
 */
export function useViewMode(screen: ViewModeScreen): [ViewMode, (mode: ViewMode) => void] {
  const settingsQ = useSettings();
  const [mode, setMode] = useState<ViewMode>('CALENDAR');
  const initialized = useRef(false);

  useEffect(() => {
    if (initialized.current || !settingsQ.data) return;
    setMode(settingsQ.data[SETTING_KEY[screen]] as ViewMode);
    initialized.current = true;
  }, [settingsQ.data, screen]);

  return [mode, setMode];
}
