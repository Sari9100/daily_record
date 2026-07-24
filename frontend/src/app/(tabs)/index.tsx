import { useMemo } from 'react';
import { ActivityIndicator, FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { endOfMonth, format, startOfMonth } from 'date-fns';

import { Badge, EmptyState, Fab } from '@/components/ui';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useTimeline } from '@/api/timeline';
import type { TimelineDay, TimelineItem, TimelineItemType } from '@/domain/types';

const TYPE_META: Record<TimelineItemType, { label: string; color: string }> = {
  TRANSACTION: { label: '가계부', color: '#1a73e8' },
  SCHEDULE: { label: '일정', color: '#188038' },
  DIARY: { label: '기록', color: '#a142f4' },
  PHOTO: { label: '사진', color: '#e37400' },
};

function formatAmount(amount: number): string {
  return `${Math.round(amount).toLocaleString('ko-KR')}원`;
}

function ItemRow({ item, onPress }: { item: TimelineItem; onPress: () => void }) {
  const theme = useTheme();
  const meta = TYPE_META[item.type] ?? { label: item.type, color: theme.textMuted };
  return (
    <Pressable style={styles.itemRow} onPress={onPress}>
      <Text style={[styles.itemTime, { color: theme.textSecondary }]}>{item.time ?? '종일'}</Text>
      <Badge label={meta.label} color={meta.color} />
      <Text style={[styles.itemTitle, { color: theme.text }]} numberOfLines={1}>
        {item.title ?? '(비공개)'}
      </Text>
      {item.amount != null && <Text style={[styles.itemAmount, { color: theme.primary }]}>{formatAmount(item.amount)}</Text>}
    </Pressable>
  );
}

function itemRoute(item: TimelineItem): { pathname: string; params: { id: number } } | null {
  if (item.type === 'TRANSACTION') return { pathname: '/transaction/[id]', params: { id: item.id } };
  if (item.type === 'SCHEDULE') return { pathname: '/schedule/[id]', params: { id: item.id } };
  if (item.type === 'DIARY') return { pathname: '/diary/[id]', params: { id: item.id } };
  return null;
}

function DayBlock({ day, onOpenItem }: { day: TimelineDay; onOpenItem: (item: TimelineItem) => void }) {
  const theme = useTheme();
  return (
    <View style={styles.dayBlock}>
      <Text style={[styles.dayHeader, { color: theme.text }]}>{day.date}</Text>
      {day.items.map((item) => (
        <ItemRow key={`${item.type}-${item.id}`} item={item} onPress={() => onOpenItem(item)} />
      ))}
    </View>
  );
}

export default function TimelineScreen() {
  const theme = useTheme();
  const router = useRouter();
  const range = useMemo(() => {
    const now = new Date();
    return {
      from: format(startOfMonth(now), 'yyyy-MM-dd'),
      to: format(endOfMonth(now), 'yyyy-MM-dd'),
    };
  }, []);

  const { data, isLoading, isError, error, refetch, isRefetching } = useTimeline(range);

  const openItem = (item: TimelineItem) => {
    const route = itemRoute(item);
    if (route) router.push(route as never);
  };

  if (isLoading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator />
      </View>
    );
  }

  if (isError) {
    return (
      <View style={styles.center}>
        <Text style={[styles.errorText, { color: theme.danger }]}>
          타임라인을 불러오지 못했습니다.{'\n'}
          {error instanceof Error ? error.message : ''}
        </Text>
      </View>
    );
  }

  const days = data?.days ?? [];

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]} edges={['bottom']}>
      <FlatList
        data={days}
        keyExtractor={(d) => d.date}
        renderItem={({ item }) => <DayBlock day={item} onOpenItem={openItem} />}
        contentContainerStyle={days.length === 0 ? styles.emptyContainer : styles.listContent}
        onRefresh={refetch}
        refreshing={isRefetching}
        ListEmptyComponent={<EmptyState text={'이번 달 기록이 아직 없어요.\n가계부·일정·기록을 추가해 보세요.'} />}
      />
      <Fab onPress={() => router.push('/transaction/new')} />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: Spacing.four },
  listContent: { padding: Spacing.three, gap: Spacing.three },
  emptyContainer: { flexGrow: 1 },
  errorText: { textAlign: 'center', fontSize: 14, lineHeight: 20 },
  dayBlock: { gap: Spacing.two },
  dayHeader: { fontSize: 15, fontWeight: '700', marginBottom: 2 },
  itemRow: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 6 },
  itemTime: { width: 44, fontSize: 13 },
  itemTitle: { flex: 1, fontSize: 15 },
  itemAmount: { fontSize: 14, fontWeight: '600' },
});
