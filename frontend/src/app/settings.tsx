import { type ReactNode } from 'react';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { SegmentedControl, type SegmentOption } from '@/components/ui';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useSettings, useUpdateSettings, type DetailLevel, type ViewMode } from '@/api/settings';

const LEVEL_OPTIONS: SegmentOption<DetailLevel>[] = [
  { value: 'FULL', label: '상세' },
  { value: 'SUMMARY', label: '요약(바쁨)' },
];
const VIEW_OPTIONS: SegmentOption<ViewMode>[] = [
  { value: 'INLINE', label: '인라인' },
  { value: 'CALENDAR', label: '캘린더' },
];

export default function SettingsScreen() {
  const theme = useTheme();
  const settingsQ = useSettings();
  const updateMut = useUpdateSettings();

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]} edges={['bottom']}>
      <View style={styles.container}>
        <SettingRow
          label="공유개인 일정 표시 수준"
          help='다른 구성원이 내 "공유개인(SHARED_PERSONAL)" 일정을 볼 때, 제목·장소를 그대로 보여줄지(상세) 또는 "바쁨"으로 가릴지(요약) 정합니다.'>
          {settingsQ.isLoading ? (
            <ActivityIndicator />
          ) : (
            <SegmentedControl
              options={LEVEL_OPTIONS}
              value={settingsQ.data?.sharedScheduleDetailLevel ?? 'FULL'}
              onChange={(v) => updateMut.mutate({ sharedScheduleDetailLevel: v })}
            />
          )}
        </SettingRow>

        <SettingRow label="가계부 기본 보기" help="가계부 화면을 열었을 때 인라인(목록)과 캘린더 중 무엇을 먼저 보여줄지 정합니다.">
          {settingsQ.isLoading ? (
            <ActivityIndicator />
          ) : (
            <SegmentedControl
              options={VIEW_OPTIONS}
              value={settingsQ.data?.ledgerDefaultView ?? 'INLINE'}
              onChange={(v) => updateMut.mutate({ ledgerDefaultView: v })}
            />
          )}
        </SettingRow>

        <SettingRow label="일정 기본 보기" help="일정 화면의 기본 보기를 정합니다.">
          {settingsQ.isLoading ? (
            <ActivityIndicator />
          ) : (
            <SegmentedControl
              options={VIEW_OPTIONS}
              value={settingsQ.data?.scheduleDefaultView ?? 'INLINE'}
              onChange={(v) => updateMut.mutate({ scheduleDefaultView: v })}
            />
          )}
        </SettingRow>

        <SettingRow label="기록 기본 보기" help="기록 화면의 기본 보기를 정합니다.">
          {settingsQ.isLoading ? (
            <ActivityIndicator />
          ) : (
            <SegmentedControl
              options={VIEW_OPTIONS}
              value={settingsQ.data?.diaryDefaultView ?? 'INLINE'}
              onChange={(v) => updateMut.mutate({ diaryDefaultView: v })}
            />
          )}
        </SettingRow>

        {updateMut.isPending && <Text style={{ color: theme.textSecondary, fontSize: 13 }}>저장 중…</Text>}
        {updateMut.isError && (
          <Text style={{ color: theme.danger, fontSize: 13 }}>
            {updateMut.error instanceof Error ? updateMut.error.message : '저장에 실패했습니다.'}
          </Text>
        )}
      </View>
    </SafeAreaView>
  );
}

function SettingRow({ label, help, children }: { label: string; help: string; children: ReactNode }) {
  const theme = useTheme();
  return (
    <View style={styles.row}>
      <Text style={[styles.label, { color: theme.text }]}>{label}</Text>
      <Text style={[styles.help, { color: theme.textSecondary }]}>{help}</Text>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  container: { padding: 20, gap: Spacing.five },
  row: { gap: Spacing.two },
  label: { fontSize: 15, fontWeight: '700' },
  help: { fontSize: 13, lineHeight: 19 },
});
