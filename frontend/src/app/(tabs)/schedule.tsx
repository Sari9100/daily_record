import { useMemo, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { endOfMonth, format, parseISO, startOfMonth } from 'date-fns';

import {
  Badge,
  EmptyState,
  Fab,
  ScreenHeader,
  SearchSheet,
  SortOrderToggle,
  ViewModeToggle,
  type SearchResultItem,
} from '@/components/ui';
import { MonthGrid, type MonthBar } from '@/components/calendar/MonthGrid';
import { MonthNav } from '@/components/calendar/MonthNav';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useViewMode } from '@/hooks/use-view-mode';
import { useSchedules, useToggleDone } from '@/api/schedule';
import {
  SCHEDULE_TYPE_LABEL,
  SYNC_STATUS_LABEL,
  type Schedule,
  type SyncStatus,
} from '@/domain/schedule';

/** 종일 + 시작일 다른 종료일 = 다일 일정(캘린더에 막대로 표시). */
function isMultiDay(s: Schedule): boolean {
  return s.allDay && !!s.startDate && !!s.endDate && s.endDate !== s.startDate;
}

function groupKey(s: Schedule): string {
  if (s.allDay) return s.startDate ?? '';
  return s.startedAt ? format(parseISO(s.startedAt), 'yyyy-MM-dd') : '';
}
function timeLabel(s: Schedule): string {
  if (s.allDay) return '종일';
  return s.startedAt ? format(parseISO(s.startedAt), 'HH:mm') : '';
}
function syncColor(s: SyncStatus, theme: ReturnType<typeof useTheme>): string {
  if (s === 'CONFLICT') return theme.danger;
  if (s === 'SYNCED') return theme.primary;
  return theme.textMuted;
}

type DayGroup = { date: string; items: Schedule[] };

export default function ScheduleScreen() {
  const theme = useTheme();
  const router = useRouter();
  const toggleMut = useToggleDone();
  const [viewMode, setViewMode] = useViewMode('schedule');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('asc');
  const [month, setMonth] = useState(new Date());
  const [selectedDate, setSelectedDate] = useState<string | null>(null);
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  const range = useMemo(
    () => ({ from: startOfMonth(month).toISOString(), to: endOfMonth(month).toISOString() }),
    [month],
  );
  const { data, isLoading, isError, error, refetch, isRefetching } = useSchedules(range);

  const searchResultsQ = useSchedules({ q: searchQuery }, { enabled: searchQuery.length > 0 });
  const searchResults: SearchResultItem[] = useMemo(
    () =>
      (searchResultsQ.data ?? []).map((s) => ({
        id: String(s.id),
        title: s.title,
        subtitle: SCHEDULE_TYPE_LABEL[s.scheduleType],
        date: groupKey(s),
      })),
    [searchResultsQ.data],
  );

  const allGroups: DayGroup[] = useMemo(() => {
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

  const bars: MonthBar[] = useMemo(
    () =>
      (data ?? [])
        .filter(isMultiDay)
        .map((s) => ({ id: String(s.id), label: s.title, color: theme.success, startDate: s.startDate as string, endDate: s.endDate as string })),
    [data, theme],
  );

  const markersByDate = useMemo(() => {
    const map: Record<string, { color: string }[]> = {};
    for (const g of allGroups) {
      if (!g.date) continue;
      // 다일 일정은 막대로 따로 표시하므로 점(dot)에서는 제외.
      const dotItems = g.items.filter((s) => !isMultiDay(s));
      if (dotItems.length > 0) map[g.date] = dotItems.map(() => ({ color: theme.success }));
    }
    return map;
  }, [allGroups, theme]);

  const sortedGroups = useMemo(
    () => [...allGroups].sort((a, b) => (sortOrder === 'asc' ? a.date.localeCompare(b.date) : b.date.localeCompare(a.date))),
    [allGroups, sortOrder],
  );
  const groups =
    viewMode === 'CALENDAR' && selectedDate ? allGroups.filter((g) => g.date === selectedDate) : sortedGroups;

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]} edges={['top']}>
      <ScreenHeader title="일정" links={[{ label: '검색', onPress: () => setSearchOpen(true) }]} />

      <SearchSheet
        visible={searchOpen}
        onClose={() => setSearchOpen(false)}
        onQueryChange={setSearchQuery}
        results={searchResults}
        isLoading={searchResultsQ.isFetching}
        onSelect={(item) => {
          setMonth(parseISO(item.date));
          setSelectedDate(item.date);
          if (viewMode !== 'CALENDAR') setViewMode('CALENDAR');
          setSearchOpen(false);
        }}
      />

      <MonthNav
        month={month}
        onMonthChange={(m) => {
          setMonth(m);
          setSelectedDate(null);
        }}
        right={
          <>
            {viewMode === 'INLINE' && <SortOrderToggle order={sortOrder} onChange={setSortOrder} />}
            <ViewModeToggle mode={viewMode} onChange={setViewMode} />
          </>
        }
      />

      {viewMode === 'CALENDAR' && (
        <View style={styles.calendarBox}>
          <MonthGrid
            month={month}
            markersByDate={markersByDate}
            bars={bars}
            selectedDate={selectedDate}
            onSelectDate={(d) => setSelectedDate((prev) => (prev === d ? null : d))}
          />
        </View>
      )}

      {isLoading ? (
        <View style={styles.center}>
          <ActivityIndicator />
        </View>
      ) : isError ? (
        <View style={styles.center}>
          <Text style={{ color: theme.danger, fontSize: 14 }}>
            {error instanceof Error ? error.message : '일정을 불러오지 못했습니다.'}
          </Text>
        </View>
      ) : (
        <FlatList
          data={groups}
          keyExtractor={(g) => g.date}
          onRefresh={refetch}
          refreshing={isRefetching}
          contentContainerStyle={groups.length === 0 ? styles.emptyBox : styles.list}
          ListEmptyComponent={
            <EmptyState
              text={
                viewMode === 'CALENDAR' && selectedDate
                  ? '이 날짜엔 일정이 없어요.'
                  : '이번 달 일정이 없어요.\n+ 로 추가해 보세요.'
              }
            />
          }
          renderItem={({ item: g }) => (
            <View style={styles.dayBlock}>
              <Text style={[styles.dayHeader, { color: theme.text }]}>{g.date}</Text>
              {g.items.map((s) => (
                <Pressable
                  key={s.id}
                  style={styles.row}
                  onPress={() => router.push({ pathname: '/schedule/[id]', params: { id: s.id } })}>
                  <Text style={[styles.time, { color: theme.textSecondary }]}>{timeLabel(s)}</Text>
                  {(s.scheduleType === 'TODO' || s.scheduleType === 'REMINDER') && (
                    <Pressable hitSlop={8} onPress={() => toggleMut.mutate(s.id)}>
                      <Ionicons
                        name={s.isDone ? 'checkmark-circle' : 'ellipse-outline'}
                        size={20}
                        color={s.isDone ? theme.success : theme.textMuted}
                      />
                    </Pressable>
                  )}
                  <Text style={[styles.title, { color: theme.text }, s.isDone && { textDecorationLine: 'line-through', color: theme.textMuted }]} numberOfLines={1}>
                    {s.title}
                  </Text>
                  {s.syncStatus && <Badge label={SYNC_STATUS_LABEL[s.syncStatus]} color={syncColor(s.syncStatus, theme)} />}
                  <Text style={[styles.type, { color: theme.textMuted }]}>{SCHEDULE_TYPE_LABEL[s.scheduleType]}</Text>
                </Pressable>
              ))}
            </View>
          )}
        />
      )}

      <Fab onPress={() => router.push('/schedule/new')} />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  calendarBox: { paddingHorizontal: Spacing.two, paddingBottom: Spacing.two },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: Spacing.four },
  list: { padding: Spacing.three, gap: Spacing.three, paddingBottom: 96 },
  emptyBox: { flexGrow: 1 },
  dayBlock: { gap: Spacing.two },
  dayHeader: { fontSize: 15, fontWeight: '700' },
  row: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 6 },
  time: { width: 44, fontSize: 13 },
  title: { flex: 1, fontSize: 15 },
  type: { fontSize: 12 },
});
