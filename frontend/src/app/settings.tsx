import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ChipGroup, type ChipOption } from '@/components/ChipGroup';
import { useSettings, useUpdateSettings, type DetailLevel } from '@/api/settings';

const LEVEL_OPTIONS: ChipOption<DetailLevel>[] = [
  { value: 'FULL', label: '상세' },
  { value: 'SUMMARY', label: '요약(바쁨)' },
];

export default function SettingsScreen() {
  const settingsQ = useSettings();
  const updateMut = useUpdateSettings();

  return (
    <SafeAreaView style={styles.safe} edges={['bottom']}>
      <View style={styles.container}>
        <Text style={styles.label}>공유개인 일정 표시 수준</Text>
        <Text style={styles.help}>
          다른 구성원이 내 "공유개인(SHARED_PERSONAL)" 일정을 볼 때, 제목·장소를 그대로 보여줄지(상세) 또는
          "바쁨"으로 가릴지(요약) 정합니다.
        </Text>

        {settingsQ.isLoading ? (
          <ActivityIndicator />
        ) : (
          <ChipGroup
            options={LEVEL_OPTIONS}
            value={settingsQ.data?.sharedScheduleDetailLevel ?? 'FULL'}
            onChange={(v) => updateMut.mutate(v)}
          />
        )}

        {updateMut.isPending && <Text style={styles.saving}>저장 중…</Text>}
        {updateMut.isError && (
          <Text style={styles.error}>
            {updateMut.error instanceof Error ? updateMut.error.message : '저장에 실패했습니다.'}
          </Text>
        )}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#fff' },
  container: { padding: 20, gap: 12 },
  label: { fontSize: 15, fontWeight: '700', color: '#202124' },
  help: { fontSize: 13, color: '#5f6368', lineHeight: 19 },
  saving: { fontSize: 13, color: '#5f6368' },
  error: { fontSize: 13, color: '#d93025' },
});
