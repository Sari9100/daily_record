import { useMemo } from 'react';
import { ActivityIndicator, FlatList, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { endOfMonth, format, startOfMonth } from 'date-fns';

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

function ItemRow({ item }: { item: TimelineItem }) {
  const meta = TYPE_META[item.type] ?? { label: item.type, color: '#5f6368' };
  return (
    <View style={styles.itemRow}>
      <Text style={styles.itemTime}>{item.time ?? '종일'}</Text>
      <View style={[styles.badge, { backgroundColor: meta.color }]}>
        <Text style={styles.badgeText}>{meta.label}</Text>
      </View>
      <Text style={styles.itemTitle} numberOfLines={1}>
        {item.title ?? '(비공개)'}
      </Text>
      {item.amount != null && <Text style={styles.itemAmount}>{formatAmount(item.amount)}</Text>}
    </View>
  );
}

function DayBlock({ day }: { day: TimelineDay }) {
  return (
    <View style={styles.dayBlock}>
      <Text style={styles.dayHeader}>{day.date}</Text>
      {day.items.map((item) => (
        <ItemRow key={`${item.type}-${item.id}`} item={item} />
      ))}
    </View>
  );
}

export default function TimelineScreen() {
  const range = useMemo(() => {
    const now = new Date();
    return {
      from: format(startOfMonth(now), 'yyyy-MM-dd'),
      to: format(endOfMonth(now), 'yyyy-MM-dd'),
    };
  }, []);

  const { data, isLoading, isError, error, refetch, isRefetching } = useTimeline(range);

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
        <Text style={styles.errorText}>
          타임라인을 불러오지 못했습니다.{'\n'}
          {error instanceof Error ? error.message : ''}
        </Text>
      </View>
    );
  }

  const days = data?.days ?? [];

  return (
    <SafeAreaView style={styles.safe} edges={['bottom']}>
      <FlatList
        data={days}
        keyExtractor={(d) => d.date}
        renderItem={({ item }) => <DayBlock day={item} />}
        contentContainerStyle={days.length === 0 ? styles.emptyContainer : styles.listContent}
        onRefresh={refetch}
        refreshing={isRefetching}
        ListEmptyComponent={
          <Text style={styles.emptyText}>이번 달 기록이 아직 없어요.{'\n'}가계부·일정·기록을 추가해 보세요.</Text>
        }
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#fff' },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  listContent: { padding: 16, gap: 16 },
  emptyContainer: { flexGrow: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  emptyText: { textAlign: 'center', color: '#5f6368', fontSize: 15, lineHeight: 22 },
  errorText: { textAlign: 'center', color: '#d93025', fontSize: 14, lineHeight: 20 },
  dayBlock: { gap: 8 },
  dayHeader: { fontSize: 15, fontWeight: '700', color: '#202124', marginBottom: 2 },
  itemRow: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 6 },
  itemTime: { width: 44, fontSize: 13, color: '#5f6368' },
  badge: { paddingHorizontal: 8, paddingVertical: 2, borderRadius: 6 },
  badgeText: { color: '#fff', fontSize: 11, fontWeight: '600' },
  itemTitle: { flex: 1, fontSize: 15, color: '#202124' },
  itemAmount: { fontSize: 14, fontWeight: '600', color: '#1a73e8' },
});
