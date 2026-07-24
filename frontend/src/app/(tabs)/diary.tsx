import { useMemo, useState } from 'react';
import { ActivityIndicator, FlatList, Image, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { endOfMonth, eachDayOfInterval, format, max, min, parseISO, startOfMonth } from 'date-fns';

import {
  Card,
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
import { useDiaries } from '@/api/diary';
import { useCollections } from '@/api/collection';
import { mediaUrl } from '@/domain/config';
import type { Diary } from '@/domain/diary';
import type { Collection } from '@/domain/collection';

const COLLECTION_DOT = '#f4a142';
const DIARY_DOT = '#a142f4';

type FeedItem =
  | { kind: 'diary'; date: string; diary: Diary }
  | { kind: 'collection'; date: string; collection: Collection };

/** 대표 이미지 하나 + 제목만 — 자세한 내용은 탭해서 상세로. */
function DiaryCard({ d, onPress }: { d: Diary; onPress: () => void }) {
  const theme = useTheme();
  const cover = d.photos.find((p) => p.url);
  return (
    <Pressable onPress={onPress}>
      <Card style={styles.card}>
        {cover?.url ? (
          <Image source={{ uri: mediaUrl(cover.url) }} style={styles.cover} resizeMode="cover" />
        ) : (
          <View style={[styles.cover, styles.coverEmpty, { backgroundColor: theme.surfaceMuted }]}>
            <Ionicons name="image-outline" size={28} color={theme.textMuted} />
          </View>
        )}
        <View style={styles.cardBody}>
          <Text style={[styles.title, { color: theme.text }]} numberOfLines={1}>
            {d.title ?? d.recordedOn}
          </Text>
        </View>
      </Card>
    </Pressable>
  );
}

/** 일정에서 만든 묶음인데 그 날짜에 아직 기록이 없을 때 — 탭하면 묶음 상세(거래/기록 통합)로 이동. */
function CollectionPlaceholderCard({ collection, date, onPress }: { collection: Collection; date: string; onPress: () => void }) {
  const theme = useTheme();
  return (
    <Pressable onPress={onPress}>
      <Card style={styles.card}>
        <View style={[styles.cover, styles.coverEmpty, { backgroundColor: theme.primarySurface }]}>
          <Ionicons name="folder-outline" size={28} color={COLLECTION_DOT} />
        </View>
        <View style={styles.cardBody}>
          <Text style={[styles.title, { color: theme.text }]} numberOfLines={1}>
            묶음: {collection.name}
          </Text>
          <Text style={[styles.placeholderSub, { color: theme.textMuted }]}>
            {date} · 기록 없음(탭하면 연결된 내용 보기)
          </Text>
        </View>
      </Card>
    </Pressable>
  );
}

export default function DiaryScreen() {
  const theme = useTheme();
  const router = useRouter();
  const [viewMode, setViewMode] = useViewMode('diary');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('desc');
  const [month, setMonth] = useState(new Date());
  const [selectedDate, setSelectedDate] = useState<string | null>(null);
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  const monthStart = useMemo(() => startOfMonth(month), [month]);
  const monthEnd = useMemo(() => endOfMonth(month), [month]);
  const range = useMemo(
    () => ({ from: format(monthStart, 'yyyy-MM-dd'), to: format(monthEnd, 'yyyy-MM-dd') }),
    [monthStart, monthEnd],
  );
  const { data, isLoading, isError, error, refetch, isRefetching } = useDiaries(range);
  const collectionsQ = useCollections();
  const diaries = data ?? [];

  const searchResultsQ = useDiaries({ q: searchQuery }, { enabled: searchQuery.length > 0 });
  const searchResults: SearchResultItem[] = useMemo(
    () =>
      (searchResultsQ.data ?? []).map((d) => ({
        id: String(d.id),
        title: d.title ?? d.recordedOn,
        subtitle: d.tags?.length ? d.tags.join(', ') : '',
        date: d.recordedOn,
      })),
    [searchResultsQ.data],
  );

  // 일정에서 만든 묶음은 날짜 기준(startedAt~endedAt) — 그 기간의 날마다, 그날 기록이 없으면 플레이스홀더로 노출.
  const feed = useMemo(() => {
    const diaryItems: FeedItem[] = diaries.map((d) => ({ kind: 'diary', date: d.recordedOn, diary: d }));
    const hasEntry = new Set(
      diaries.filter((d) => d.collectionId != null).map((d) => `${d.recordedOn}:${d.collectionId}`),
    );

    const placeholders: FeedItem[] = [];
    for (const c of collectionsQ.data ?? []) {
      if (!c.startedAt) continue;
      const start = new Date(c.startedAt);
      const end = c.endedAt ? new Date(c.endedAt) : start;
      const rangeStart = max([start, monthStart]);
      const rangeEnd = min([end, monthEnd]);
      if (rangeStart > rangeEnd) continue;
      for (const day of eachDayOfInterval({ start: rangeStart, end: rangeEnd })) {
        const dateStr = format(day, 'yyyy-MM-dd');
        if (hasEntry.has(`${dateStr}:${c.id}`)) continue;
        placeholders.push({ kind: 'collection', date: dateStr, collection: c });
      }
    }

    return [...diaryItems, ...placeholders].sort((a, b) => b.date.localeCompare(a.date));
  }, [diaries, collectionsQ.data, monthStart, monthEnd]);

  // 여러 날짜에 걸친 묶음은 막대로 따로 그리므로, 캘린더 점(dot)에서는 제외하고 막대만 계산.
  const multiDayCollectionIds = useMemo(() => {
    const ids = new Set<number>();
    for (const c of collectionsQ.data ?? []) {
      if (!c.startedAt) continue;
      const start = new Date(c.startedAt);
      const end = c.endedAt ? new Date(c.endedAt) : start;
      if (format(start, 'yyyy-MM-dd') !== format(end, 'yyyy-MM-dd')) ids.add(c.id);
    }
    return ids;
  }, [collectionsQ.data]);

  const bars: MonthBar[] = useMemo(() => {
    return (collectionsQ.data ?? [])
      .filter((c) => multiDayCollectionIds.has(c.id) && c.startedAt)
      .map((c) => {
        const start = max([new Date(c.startedAt as string), monthStart]);
        const end = min([c.endedAt ? new Date(c.endedAt) : start, monthEnd]);
        return { id: `col-${c.id}`, label: c.name, color: COLLECTION_DOT, startDate: format(start, 'yyyy-MM-dd'), endDate: format(end, 'yyyy-MM-dd') };
      })
      .filter((b) => b.startDate <= b.endDate);
  }, [collectionsQ.data, multiDayCollectionIds, monthStart, monthEnd]);

  const markersByDate = useMemo(() => {
    const map: Record<string, { color: string }[]> = {};
    for (const item of feed) {
      if (item.kind === 'collection' && multiDayCollectionIds.has(item.collection.id)) continue;
      (map[item.date] ??= []).push({ color: item.kind === 'diary' ? DIARY_DOT : COLLECTION_DOT });
    }
    return map;
  }, [feed, multiDayCollectionIds]);

  const sortedFeed = useMemo(
    () => [...feed].sort((a, b) => (sortOrder === 'asc' ? a.date.localeCompare(b.date) : b.date.localeCompare(a.date))),
    [feed, sortOrder],
  );
  const visibleFeed =
    viewMode === 'CALENDAR' && selectedDate ? feed.filter((i) => i.date === selectedDate) : sortedFeed;

  const openCollection = (id: number) => router.push({ pathname: '/collection/[id]', params: { id } });

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]} edges={['top']}>
      <ScreenHeader title="기록" links={[{ label: '검색', onPress: () => setSearchOpen(true) }]} />

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
            {error instanceof Error ? error.message : '기록을 불러오지 못했습니다.'}
          </Text>
        </View>
      ) : (
        <FlatList
          data={visibleFeed}
          keyExtractor={(i) => (i.kind === 'diary' ? `d${i.diary.id}` : `c${i.collection.id}-${i.date}`)}
          onRefresh={refetch}
          refreshing={isRefetching}
          contentContainerStyle={visibleFeed.length === 0 ? styles.emptyBox : styles.list}
          ListEmptyComponent={
            <EmptyState
              text={
                viewMode === 'CALENDAR' && selectedDate
                  ? '이 날짜엔 기록이 없어요.'
                  : '이번 달 기록이 없어요.\n+ 로 첫 기록을 남겨보세요.'
              }
            />
          }
          renderItem={({ item }) =>
            item.kind === 'diary' ? (
              <DiaryCard d={item.diary} onPress={() => router.push({ pathname: '/diary/[id]', params: { id: item.diary.id } })} />
            ) : (
              <CollectionPlaceholderCard collection={item.collection} date={item.date} onPress={() => openCollection(item.collection.id)} />
            )
          }
        />
      )}

      <Fab onPress={() => router.push('/diary/new')} />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  calendarBox: { paddingHorizontal: Spacing.two, paddingBottom: Spacing.two },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: Spacing.four },
  list: { padding: Spacing.three, gap: Spacing.three - 2, paddingBottom: 96 },
  emptyBox: { flexGrow: 1 },
  card: { overflow: 'hidden' },
  cover: { width: '100%', height: 180 },
  coverEmpty: { alignItems: 'center', justifyContent: 'center' },
  cardBody: { padding: 14, gap: 4 },
  title: { fontSize: 16, fontWeight: '700' },
  placeholderSub: { fontSize: 12 },
});
