import { useMemo } from 'react';
import { ActivityIndicator, FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { endOfMonth, format, parseISO, startOfMonth } from 'date-fns';

import { useSchedules, useToggleDone } from '@/api/schedule';
import {
  SCHEDULE_TYPE_LABEL,
  SYNC_STATUS_LABEL,
  type Schedule,
  type SyncStatus,
} from '@/domain/schedule';

function groupKey(s: Schedule): string {
  if (s.allDay) return s.startDate ?? '';
  return s.startedAt ? format(parseISO(s.startedAt), 'yyyy-MM-dd') : '';
}
function timeLabel(s: Schedule): string {
  if (s.allDay) return '종일';
  return s.startedAt ? format(parseISO(s.startedAt), 'HH:mm') : '';
}
function syncColor(s: SyncStatus): string {
  if (s === 'CONFLICT') return '#d93025';
  if (s === 'SYNCED') return '#1a73e8';
  return '#9aa0a6';
}

type DayGroup = { date: string; items: Schedule[] };

export default function ScheduleScreen() {
  const router = useRouter();
  const toggleMut = useToggleDone();

  const range = useMemo(() => {
    const now = new Date();
    return { from: startOfMonth(now).toISOString(), to: endOfMonth(now).toISOString() };
  }, []);
  const { data, isLoading, isError, error, refetch, isRefetching } = useSchedules(range);

  const groups: DayGroup[] = useMemo(() => {
    const map = new Map<string, Schedule[]>();
    for (const s of data ?? []) {
      const k = groupKey(s);
      if (!map.has(k)) map.set(k, []);
      map.get(k)!.push(s);
    }
    return [...map.entries()]
      .sort((a, b) => a[0].localeCompare(b[0]))
      .map(([date, items]) => ({
        date,
        items: items.sort((x, y) => (x.startedAt ?? '').localeCompare(y.startedAt ?? '')),
      }));
  }, [data]);

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>일정</Text>
      </View>

      {isLoading ? (
        <View style={styles.center}>
          <ActivityIndicator />
        </View>
      ) : isError ? (
        <View style={styles.center}>
          <Text style={styles.errorText}>{error instanceof Error ? error.message : '일정을 불러오지 못했습니다.'}</Text>
        </View>
      ) : (
        <FlatList
          data={groups}
          keyExtractor={(g) => g.date}
          onRefresh={refetch}
          refreshing={isRefetching}
          contentContainerStyle={groups.length === 0 ? styles.emptyBox : styles.list}
          ListEmptyComponent={<Text style={styles.emptyText}>이번 달 일정이 없어요.{'\n'}+ 로 추가해 보세요.</Text>}
          renderItem={({ item: g }) => (
            <View style={styles.dayBlock}>
              <Text style={styles.dayHeader}>{g.date}</Text>
              {g.items.map((s) => (
                <Pressable
                  key={s.id}
                  style={styles.row}
                  onPress={() => router.push({ pathname: '/schedule/[id]', params: { id: s.id } })}>
                  <Text style={styles.time}>{timeLabel(s)}</Text>
                  {(s.scheduleType === 'TODO' || s.scheduleType === 'REMINDER') && (
                    <Pressable hitSlop={8} onPress={() => toggleMut.mutate(s.id)}>
                      <Ionicons
                        name={s.isDone ? 'checkmark-circle' : 'ellipse-outline'}
                        size={20}
                        color={s.isDone ? '#188038' : '#9aa0a6'}
                      />
                    </Pressable>
                  )}
                  <Text style={[styles.title, s.isDone && styles.done]} numberOfLines={1}>
                    {s.title}
                  </Text>
                  {s.syncStatus && (
                    <Text style={[styles.sync, { color: syncColor(s.syncStatus) }]}>
                      {SYNC_STATUS_LABEL[s.syncStatus]}
                    </Text>
                  )}
                  <Text style={styles.type}>{SCHEDULE_TYPE_LABEL[s.scheduleType]}</Text>
                </Pressable>
              ))}
            </View>
          )}
        />
      )}

      <Pressable style={styles.fab} onPress={() => router.push('/schedule/new')}>
        <Ionicons name="add" size={28} color="#fff" />
      </Pressable>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#fff' },
  header: { paddingHorizontal: 16, paddingVertical: 12 },
  headerTitle: { fontSize: 22, fontWeight: '700', color: '#202124' },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  errorText: { textAlign: 'center', color: '#d93025', fontSize: 14 },
  list: { padding: 16, gap: 18, paddingBottom: 96 },
  emptyBox: { flexGrow: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  emptyText: { textAlign: 'center', color: '#5f6368', fontSize: 15, lineHeight: 22 },
  dayBlock: { gap: 8 },
  dayHeader: { fontSize: 15, fontWeight: '700', color: '#202124' },
  row: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 6 },
  time: { width: 44, fontSize: 13, color: '#5f6368' },
  title: { flex: 1, fontSize: 15, color: '#202124' },
  done: { textDecorationLine: 'line-through', color: '#9aa0a6' },
  sync: { fontSize: 11, fontWeight: '600' },
  type: { fontSize: 12, color: '#9aa0a6' },
  fab: {
    position: 'absolute',
    right: 20,
    bottom: 24,
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: '#1a73e8',
    alignItems: 'center',
    justifyContent: 'center',
    elevation: 4,
  },
});
