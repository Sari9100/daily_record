import { useMemo, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { endOfMonth, format, parseISO, startOfMonth } from 'date-fns';

import { ComboBox, EmptyState, Fab, ScreenHeader, SortOrderToggle, ViewModeToggle } from '@/components/ui';
import { MonthGrid } from '@/components/calendar/MonthGrid';
import { MonthNav } from '@/components/calendar/MonthNav';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useViewMode } from '@/hooks/use-view-mode';
import { useLinkTransactionsToCollection, useTransactions } from '@/api/ledger';
import { useCollections } from '@/api/collection';
import type { Transaction } from '@/domain/ledger';

function amountColor(type: Transaction['transactionType'], theme: ReturnType<typeof useTheme>): string {
  if (type === 'EXPENSE') return theme.danger;
  if (type === 'INCOME') return theme.success;
  return theme.textSecondary;
}

function amountText(t: Transaction): string {
  const sign = t.transactionType === 'EXPENSE' ? '-' : t.transactionType === 'INCOME' ? '+' : '';
  return `${sign}${Math.round(t.amount).toLocaleString('ko-KR')}원`;
}

/** 유형별 노출 계좌. 마스킹(null)은 "비공개"(빈칸 금지, frontend/CLAUDE.md §2). */
function accountText(t: Transaction): string {
  const mask = '비공개';
  if (t.transactionType === 'EXPENSE') return t.sourceAccount?.name ?? mask;
  if (t.transactionType === 'INCOME') return t.targetAccount?.name ?? mask;
  return `${t.sourceAccount?.name ?? mask} → ${t.targetAccount?.name ?? mask}`;
}

function Row({
  t,
  onPress,
  selecting,
  selected,
}: {
  t: Transaction;
  onPress: () => void;
  selecting: boolean;
  selected: boolean;
}) {
  const theme = useTheme();
  const title = t.category?.name ?? t.memo ?? '(미분류)';
  return (
    <Pressable style={styles.row} onPress={onPress}>
      {selecting && (
        <Ionicons
          name={selected ? 'checkbox' : 'square-outline'}
          size={22}
          color={selected ? theme.primary : theme.textMuted}
        />
      )}
      <View style={styles.rowLeft}>
        <Text style={[styles.rowTitle, { color: theme.text }]} numberOfLines={1}>
          {title}
        </Text>
        <Text style={[styles.rowSub, { color: theme.textSecondary }]} numberOfLines={1}>
          {accountText(t)}
          {t.memo && t.category ? ` · ${t.memo}` : ''}
        </Text>
      </View>
      <View style={styles.rowRight}>
        <Text style={[styles.amount, { color: amountColor(t.transactionType, theme) }]}>{amountText(t)}</Text>
        <Text style={[styles.time, { color: theme.textMuted }]}>{format(parseISO(t.occurredAt), 'M/d HH:mm')}</Text>
      </View>
    </Pressable>
  );
}

export default function LedgerScreen() {
  const theme = useTheme();
  const router = useRouter();
  const [viewMode, setViewMode] = useViewMode('ledger');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('desc');
  const [month, setMonth] = useState(new Date());
  const [selectedDate, setSelectedDate] = useState<string | null>(null);

  const [selecting, setSelecting] = useState(false);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const collectionsQ = useCollections();
  const linkMut = useLinkTransactionsToCollection();

  const params = useMemo(
    () => ({
      from: startOfMonth(month).toISOString(),
      to: endOfMonth(month).toISOString(),
      page: 0,
      size: 200,
    }),
    [month],
  );
  const { data, isLoading, isError, error, refetch, isRefetching } = useTransactions(params);
  const transactions = data?.content ?? [];

  const markersByDate = useMemo(() => {
    const map: Record<string, { color: string }[]> = {};
    for (const t of transactions) {
      const key = format(parseISO(t.occurredAt), 'yyyy-MM-dd');
      const color = amountColor(t.transactionType, theme);
      (map[key] ??= []).push({ color });
    }
    return map;
  }, [transactions, theme]);

  const visibleTransactions = useMemo(() => {
    const base =
      viewMode === 'CALENDAR' && selectedDate
        ? transactions.filter((t) => format(parseISO(t.occurredAt), 'yyyy-MM-dd') === selectedDate)
        : transactions;
    if (viewMode !== 'INLINE') return base;
    return [...base].sort((a, b) =>
      sortOrder === 'asc' ? a.occurredAt.localeCompare(b.occurredAt) : b.occurredAt.localeCompare(a.occurredAt),
    );
  }, [transactions, viewMode, selectedDate, sortOrder]);

  const toggleSelecting = () => {
    setSelecting((prev) => !prev);
    setSelectedIds(new Set());
  };

  const toggleSelected = (id: number) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const openItem = (t: Transaction) => {
    if (selecting) {
      toggleSelected(t.id);
      return;
    }
    router.push({ pathname: '/transaction/[id]', params: { id: t.id } });
  };

  const linkSelectedTo = async (collectionId: number | null) => {
    if (collectionId == null) return;
    await linkMut.mutateAsync({ transactionIds: [...selectedIds], collectionId });
    setSelecting(false);
    setSelectedIds(new Set());
  };

  const collectionOptions = (collectionsQ.data ?? []).map((c) => ({
    value: c.id,
    label: c.tags.length > 0 ? `${c.name} (${c.tags.join(', ')})` : c.name,
  }));

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]} edges={['top']}>
      <ScreenHeader
        title="가계부"
        links={[
          { label: selecting ? '선택 취소' : '선택', onPress: toggleSelecting },
          { label: '통계', onPress: () => router.push('/statistics') },
          { label: '계좌', onPress: () => router.push('/accounts') },
          { label: '카테고리', onPress: () => router.push('/categories') },
        ]}
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
          <Text style={[styles.errorText, { color: theme.danger }]}>
            거래를 불러오지 못했습니다.{'\n'}
            {error instanceof Error ? error.message : ''}
          </Text>
        </View>
      ) : (
        <FlatList
          data={visibleTransactions}
          keyExtractor={(t) => String(t.id)}
          renderItem={({ item }) => (
            <Row t={item} onPress={() => openItem(item)} selecting={selecting} selected={selectedIds.has(item.id)} />
          )}
          ItemSeparatorComponent={() => <View style={[styles.sep, { backgroundColor: theme.surfaceMuted }]} />}
          contentContainerStyle={[
            visibleTransactions.length === 0 ? styles.emptyBox : styles.list,
            selecting && selectedIds.size > 0 ? styles.listWithActionBar : null,
          ]}
          onRefresh={refetch}
          refreshing={isRefetching}
          ListEmptyComponent={
            <EmptyState
              text={
                viewMode === 'CALENDAR' && selectedDate
                  ? '이 날짜엔 거래가 없어요.'
                  : '아직 거래가 없어요.\n+ 버튼으로 첫 거래를 입력해 보세요.'
              }
            />
          }
        />
      )}

      {selecting && selectedIds.size > 0 && (
        <View style={[styles.actionBar, { backgroundColor: theme.background, borderTopColor: theme.border }]}>
          <Text style={[styles.actionBarText, { color: theme.text }]}>{selectedIds.size}개 선택됨</Text>
          <View style={styles.actionBarCombo}>
            <ComboBox
              options={collectionOptions}
              value={null}
              onChange={linkSelectedTo}
              placeholder="묶음에 연결"
              searchPlaceholder="묶음 이름·태그 검색"
              emptyText="연결할 수 있는 묶음이 없어요. 일정 작성 화면에서 먼저 만들어보세요."
            />
          </View>
        </View>
      )}
      {linkMut.isError && (
        <Text style={{ color: theme.danger, fontSize: 13, textAlign: 'center', paddingBottom: 8 }}>
          {linkMut.error instanceof Error ? linkMut.error.message : '연결에 실패했습니다.'}
        </Text>
      )}

      {!selecting && <Fab onPress={() => router.push('/transaction/new')} />}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  calendarBox: { paddingHorizontal: Spacing.two, paddingBottom: Spacing.two },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: Spacing.four },
  errorText: { textAlign: 'center', fontSize: 14, lineHeight: 20 },
  list: { paddingHorizontal: Spacing.three, paddingBottom: 96 },
  listWithActionBar: { paddingBottom: 96 },
  emptyBox: { flexGrow: 1 },
  row: { flexDirection: 'row', alignItems: 'center', paddingVertical: 12, gap: 12 },
  rowLeft: { flex: 1, gap: 3 },
  rowTitle: { fontSize: 16, fontWeight: '500' },
  rowSub: { fontSize: 13 },
  rowRight: { alignItems: 'flex-end', gap: 3 },
  amount: { fontSize: 16, fontWeight: '700' },
  time: { fontSize: 12 },
  sep: { height: 1 },
  actionBar: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two + 4,
    borderTopWidth: 1,
  },
  actionBarText: { fontSize: 15, fontWeight: '600' },
  actionBarCombo: { width: 220 },
});
