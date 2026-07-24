import { useMemo, useState } from 'react';
import { ActivityIndicator, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { endOfMonth, endOfYear, startOfMonth, startOfYear, subMonths } from 'date-fns';

import { ChipGroup, type ChipOption } from '@/components/ChipGroup';
import { Card } from '@/components/ui';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
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
  const theme = useTheme();
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
    <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]} edges={['bottom']}>
      <ScrollView contentContainerStyle={styles.container}>
        <ChipGroup options={PERIOD_OPTIONS} value={period} onChange={setPeriod} />
        <ChipGroup options={SCOPE_OPTIONS} value={scope} onChange={setScope} />

        {isLoading ? (
          <View style={styles.center}>
            <ActivityIndicator />
          </View>
        ) : isError ? (
          <Text style={{ color: theme.danger, fontSize: 14, paddingVertical: 20, textAlign: 'center' }}>
            {error instanceof Error ? error.message : '통계를 불러오지 못했습니다.'}
          </Text>
        ) : (
          <>
            <View style={styles.cards}>
              <Card style={[styles.card, { backgroundColor: theme.successSurface, borderColor: theme.successSurface }]}>
                <Text style={[styles.cardLabel, { color: theme.textSecondary }]}>수입</Text>
                <Text style={[styles.cardValue, { color: theme.success }]}>{won(data?.totalIncome ?? 0)}</Text>
              </Card>
              <Card style={[styles.card, { backgroundColor: theme.dangerSurface, borderColor: theme.dangerSurface }]}>
                <Text style={[styles.cardLabel, { color: theme.textSecondary }]}>지출</Text>
                <Text style={[styles.cardValue, { color: theme.danger }]}>{won(data?.totalExpense ?? 0)}</Text>
              </Card>
            </View>
            <View style={styles.netRow}>
              <Text style={[styles.netLabel, { color: theme.textSecondary }]}>순액</Text>
              <Text style={[styles.netValue, { color: net >= 0 ? theme.success : theme.danger }]}>
                {net >= 0 ? '+' : ''}
                {won(net)}
              </Text>
            </View>

            <View style={styles.section}>
              <View style={styles.sectionHead}>
                <Text style={[styles.sectionTitle, { color: theme.text }]}>카테고리별</Text>
                <ChipGroup options={CAT_TYPE_OPTIONS} value={catType} onChange={setCatType} />
              </View>
              {categories.length === 0 ? (
                <Text style={{ color: theme.textSecondary, fontSize: 14 }}>
                  해당 기간 {catType === 'EXPENSE' ? '지출' : '수입'} 내역이 없어요.
                </Text>
              ) : (
                categories.map((c) => (
                  <View key={c.categoryId} style={styles.catRow}>
                    <View style={styles.catTop}>
                      <Text style={[styles.catName, { color: theme.text }]}>{c.name ?? '(미분류)'}</Text>
                      <Text style={[styles.catAmount, { color: theme.text }]}>{won(c.amount)}</Text>
                    </View>
                    <View style={[styles.barTrack, { backgroundColor: theme.surfaceMuted }]}>
                      <View
                        style={[
                          styles.barFill,
                          {
                            width: `${maxCat > 0 ? (c.amount / maxCat) * 100 : 0}%`,
                            backgroundColor: catType === 'EXPENSE' ? theme.danger : theme.success,
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
                <Text style={[styles.sectionTitle, { color: theme.text }]}>월별</Text>
                {data?.byMonth.map((m) => (
                  <View key={m.month} style={styles.monthRow}>
                    <Text style={[styles.monthLabel, { color: theme.textSecondary }]}>{m.month}</Text>
                    <Text style={[styles.monthVal, { color: theme.success }]}>+{won(m.income)}</Text>
                    <Text style={[styles.monthVal, { color: theme.danger }]}>-{won(m.expense)}</Text>
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
  safe: { flex: 1 },
  container: { padding: Spacing.three, gap: Spacing.three },
  center: { paddingVertical: 40, alignItems: 'center' },
  cards: { flexDirection: 'row', gap: 12 },
  card: { flex: 1, padding: 16, gap: 6, borderWidth: 0 },
  cardLabel: { fontSize: 13, fontWeight: '600' },
  cardValue: { fontSize: 20, fontWeight: '700' },
  netRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 4 },
  netLabel: { fontSize: 15, fontWeight: '600' },
  netValue: { fontSize: 18, fontWeight: '700' },
  section: { gap: 12, paddingTop: 8 },
  sectionHead: { gap: 10 },
  sectionTitle: { fontSize: 16, fontWeight: '700' },
  catRow: { gap: 6 },
  catTop: { flexDirection: 'row', justifyContent: 'space-between' },
  catName: { fontSize: 15 },
  catAmount: { fontSize: 15, fontWeight: '600' },
  barTrack: { height: 8, borderRadius: Radius.sm / 2, overflow: 'hidden' },
  barFill: { height: 8, borderRadius: Radius.sm / 2 },
  monthRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 8, gap: 12 },
  monthLabel: { flex: 1, fontSize: 14, fontWeight: '600' },
  monthVal: { fontSize: 13, fontWeight: '600' },
});
