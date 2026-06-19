import { useMemo, useState } from 'react';
import { ActivityIndicator, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { endOfMonth, endOfYear, startOfMonth, startOfYear, subMonths } from 'date-fns';

import { ChipGroup, type ChipOption } from '@/components/ChipGroup';
import { useStatistics } from '@/api/ledger';
import type { CategoryStat, CategoryType, StatScope } from '@/domain/ledger';

type Period = 'thisMonth' | 'lastMonth' | 'thisYear';

const PERIOD_OPTIONS: ChipOption<Period>[] = [
  { value: 'thisMonth', label: '이번달' },
  { value: 'lastMonth', label: '지난달' },
  { value: 'thisYear', label: '올해' },
];
const SCOPE_OPTIONS: ChipOption<StatScope>[] = [
  { value: 'ALL', label: '전체' },
  { value: 'PRIVATE', label: '개인' },
  { value: 'PARENTS', label: '부모공유' },
];
const CAT_TYPE_OPTIONS: ChipOption<CategoryType>[] = [
  { value: 'EXPENSE', label: '지출' },
  { value: 'INCOME', label: '수입' },
];

function won(n: number): string {
  return `${Math.round(n).toLocaleString('ko-KR')}원`;
}

export default function StatisticsScreen() {
  const [period, setPeriod] = useState<Period>('thisMonth');
  const [scope, setScope] = useState<StatScope>('ALL');
  const [catType, setCatType] = useState<CategoryType>('EXPENSE');

  const params = useMemo(() => {
    const now = new Date();
    let from: Date;
    let to: Date;
    if (period === 'thisMonth') {
      from = startOfMonth(now);
      to = endOfMonth(now);
    } else if (period === 'lastMonth') {
      const m = subMonths(now, 1);
      from = startOfMonth(m);
      to = endOfMonth(m);
    } else {
      from = startOfYear(now);
      to = endOfYear(now);
    }
    return { from: from.toISOString(), to: to.toISOString(), scope };
  }, [period, scope]);

  const { data, isLoading, isError, error } = useStatistics(params);

  const categories: CategoryStat[] = useMemo(() => {
    const list = (data?.byCategory ?? []).filter((c) => c.type === catType);
    return [...list].sort((a, b) => b.amount - a.amount);
  }, [data?.byCategory, catType]);
  const maxCat = categories.reduce((m, c) => Math.max(m, c.amount), 0);

  const net = (data?.totalIncome ?? 0) - (data?.totalExpense ?? 0);

  return (
    <SafeAreaView style={styles.safe} edges={['bottom']}>
      <ScrollView contentContainerStyle={styles.container}>
        <ChipGroup options={PERIOD_OPTIONS} value={period} onChange={setPeriod} />
        <ChipGroup options={SCOPE_OPTIONS} value={scope} onChange={setScope} />

        {isLoading ? (
          <View style={styles.center}>
            <ActivityIndicator />
          </View>
        ) : isError ? (
          <Text style={styles.error}>{error instanceof Error ? error.message : '통계를 불러오지 못했습니다.'}</Text>
        ) : (
          <>
            <View style={styles.cards}>
              <View style={[styles.card, styles.cardIncome]}>
                <Text style={styles.cardLabel}>수입</Text>
                <Text style={[styles.cardValue, { color: '#188038' }]}>{won(data?.totalIncome ?? 0)}</Text>
              </View>
              <View style={[styles.card, styles.cardExpense]}>
                <Text style={styles.cardLabel}>지출</Text>
                <Text style={[styles.cardValue, { color: '#d93025' }]}>{won(data?.totalExpense ?? 0)}</Text>
              </View>
            </View>
            <View style={styles.netRow}>
              <Text style={styles.netLabel}>순액</Text>
              <Text style={[styles.netValue, { color: net >= 0 ? '#188038' : '#d93025' }]}>
                {net >= 0 ? '+' : ''}
                {won(net)}
              </Text>
            </View>

            <View style={styles.section}>
              <View style={styles.sectionHead}>
                <Text style={styles.sectionTitle}>카테고리별</Text>
                <ChipGroup options={CAT_TYPE_OPTIONS} value={catType} onChange={setCatType} />
              </View>
              {categories.length === 0 ? (
                <Text style={styles.empty}>해당 기간 {catType === 'EXPENSE' ? '지출' : '수입'} 내역이 없어요.</Text>
              ) : (
                categories.map((c) => (
                  <View key={c.categoryId} style={styles.catRow}>
                    <View style={styles.catTop}>
                      <Text style={styles.catName}>{c.name ?? '(미분류)'}</Text>
                      <Text style={styles.catAmount}>{won(c.amount)}</Text>
                    </View>
                    <View style={styles.barTrack}>
                      <View
                        style={[
                          styles.barFill,
                          {
                            width: `${maxCat > 0 ? (c.amount / maxCat) * 100 : 0}%`,
                            backgroundColor: catType === 'EXPENSE' ? '#d93025' : '#188038',
                          },
                        ]}
                      />
                    </View>
                  </View>
                ))
              )}
            </View>

            {(data?.byMonth.length ?? 0) > 1 && (
              <View style={styles.section}>
                <Text style={styles.sectionTitle}>월별</Text>
                {data?.byMonth.map((m) => (
                  <View key={m.month} style={styles.monthRow}>
                    <Text style={styles.monthLabel}>{m.month}</Text>
                    <Text style={[styles.monthVal, { color: '#188038' }]}>+{won(m.income)}</Text>
                    <Text style={[styles.monthVal, { color: '#d93025' }]}>-{won(m.expense)}</Text>
                  </View>
                ))}
              </View>
            )}
          </>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#fff' },
  container: { padding: 16, gap: 16 },
  center: { paddingVertical: 40, alignItems: 'center' },
  error: { color: '#d93025', fontSize: 14, paddingVertical: 20, textAlign: 'center' },
  cards: { flexDirection: 'row', gap: 12 },
  card: { flex: 1, borderRadius: 12, padding: 16, gap: 6 },
  cardIncome: { backgroundColor: '#e6f4ea' },
  cardExpense: { backgroundColor: '#fce8e6' },
  cardLabel: { fontSize: 13, color: '#3c4043', fontWeight: '600' },
  cardValue: { fontSize: 20, fontWeight: '700' },
  netRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 4 },
  netLabel: { fontSize: 15, color: '#3c4043', fontWeight: '600' },
  netValue: { fontSize: 18, fontWeight: '700' },
  section: { gap: 12, paddingTop: 8 },
  sectionHead: { gap: 10 },
  sectionTitle: { fontSize: 16, fontWeight: '700', color: '#202124' },
  empty: { color: '#5f6368', fontSize: 14 },
  catRow: { gap: 6 },
  catTop: { flexDirection: 'row', justifyContent: 'space-between' },
  catName: { fontSize: 15, color: '#202124' },
  catAmount: { fontSize: 15, color: '#202124', fontWeight: '600' },
  barTrack: { height: 8, borderRadius: 4, backgroundColor: '#f1f3f4', overflow: 'hidden' },
  barFill: { height: 8, borderRadius: 4 },
  monthRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 8, gap: 12 },
  monthLabel: { flex: 1, fontSize: 14, color: '#3c4043', fontWeight: '600' },
  monthVal: { fontSize: 13, fontWeight: '600' },
});
