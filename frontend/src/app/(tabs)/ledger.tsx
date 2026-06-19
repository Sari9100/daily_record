import { useMemo } from 'react';
import { ActivityIndicator, FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { format, parseISO } from 'date-fns';

import { useTransactions } from '@/api/ledger';
import type { Transaction } from '@/domain/ledger';

function amountColor(type: Transaction['transactionType']): string {
  if (type === 'EXPENSE') return '#d93025';
  if (type === 'INCOME') return '#188038';
  return '#5f6368';
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

function Row({ t, onPress }: { t: Transaction; onPress: () => void }) {
  const title = t.category?.name ?? t.memo ?? '(미분류)';
  return (
    <Pressable style={styles.row} onPress={onPress}>
      <View style={styles.rowLeft}>
        <Text style={styles.rowTitle} numberOfLines={1}>
          {title}
        </Text>
        <Text style={styles.rowSub} numberOfLines={1}>
          {accountText(t)}
          {t.memo && t.category ? ` · ${t.memo}` : ''}
        </Text>
      </View>
      <View style={styles.rowRight}>
        <Text style={[styles.amount, { color: amountColor(t.transactionType) }]}>{amountText(t)}</Text>
        <Text style={styles.time}>{format(parseISO(t.occurredAt), 'M/d HH:mm')}</Text>
      </View>
    </Pressable>
  );
}

export default function LedgerScreen() {
  const router = useRouter();
  const params = useMemo(() => ({ page: 0, size: 30 }), []);
  const { data, isLoading, isError, error, refetch, isRefetching } = useTransactions(params);

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>가계부</Text>
        <View style={styles.headerLinks}>
          <Pressable onPress={() => router.push('/accounts')} hitSlop={8}>
            <Text style={styles.headerLink}>계좌</Text>
          </Pressable>
          <Pressable onPress={() => router.push('/categories')} hitSlop={8}>
            <Text style={styles.headerLink}>카테고리</Text>
          </Pressable>
        </View>
      </View>

      {isLoading ? (
        <View style={styles.center}>
          <ActivityIndicator />
        </View>
      ) : isError ? (
        <View style={styles.center}>
          <Text style={styles.errorText}>
            거래를 불러오지 못했습니다.{'\n'}
            {error instanceof Error ? error.message : ''}
          </Text>
        </View>
      ) : (
        <FlatList
          data={data?.content ?? []}
          keyExtractor={(t) => String(t.id)}
          renderItem={({ item }) => (
            <Row
              t={item}
              onPress={() => router.push({ pathname: '/transaction/[id]', params: { id: item.id } })}
            />
          )}
          ItemSeparatorComponent={() => <View style={styles.sep} />}
          contentContainerStyle={(data?.content.length ?? 0) === 0 ? styles.emptyBox : styles.list}
          onRefresh={refetch}
          refreshing={isRefetching}
          ListEmptyComponent={
            <Text style={styles.emptyText}>아직 거래가 없어요.{'\n'}+ 버튼으로 첫 거래를 입력해 보세요.</Text>
          }
        />
      )}

      <Pressable style={styles.fab} onPress={() => router.push('/transaction/new')}>
        <Ionicons name="add" size={28} color="#fff" />
      </Pressable>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#fff' },
  header: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  headerTitle: { fontSize: 22, fontWeight: '700', color: '#202124' },
  headerLinks: { flexDirection: 'row', gap: 16 },
  headerLink: { fontSize: 15, color: '#1a73e8', fontWeight: '600' },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  errorText: { textAlign: 'center', color: '#d93025', fontSize: 14, lineHeight: 20 },
  list: { paddingHorizontal: 16, paddingBottom: 96 },
  emptyBox: { flexGrow: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  emptyText: { textAlign: 'center', color: '#5f6368', fontSize: 15, lineHeight: 22 },
  row: { flexDirection: 'row', alignItems: 'center', paddingVertical: 12, gap: 12 },
  rowLeft: { flex: 1, gap: 3 },
  rowTitle: { fontSize: 16, color: '#202124', fontWeight: '500' },
  rowSub: { fontSize: 13, color: '#5f6368' },
  rowRight: { alignItems: 'flex-end', gap: 3 },
  amount: { fontSize: 16, fontWeight: '700' },
  time: { fontSize: 12, color: '#9aa0a6' },
  sep: { height: 1, backgroundColor: '#f1f3f4' },
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
    shadowColor: '#000',
    shadowOpacity: 0.2,
    shadowRadius: 6,
    shadowOffset: { width: 0, height: 3 },
    elevation: 4,
  },
});
