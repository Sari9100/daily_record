import { useMemo } from 'react';
import { ActivityIndicator, FlatList, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { format, parseISO } from 'date-fns';

import { Badge, Button } from '@/components/ui';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useCachedCollection, useCollectionSummary } from '@/api/collection';
import { useTransactions } from '@/api/ledger';
import { useDiaries } from '@/api/diary';
import type { Transaction } from '@/domain/ledger';
import type { Diary } from '@/domain/diary';

function won(n: number): string {
  return `${Math.round(n).toLocaleString('ko-KR')}원`;
}

function amountColor(type: Transaction['transactionType'], theme: ReturnType<typeof useTheme>): string {
  if (type === 'EXPENSE') return theme.danger;
  if (type === 'INCOME') return theme.success;
  return theme.textSecondary;
}

export default function CollectionSummaryScreen() {
  const theme = useTheme();
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const numId = Number(id);
  const collection = useCachedCollection(numId);
  const { data, isLoading, isError, error } = useCollectionSummary(numId);

  const transactionsQ = useTransactions(useMemo(() => ({ collectionId: numId, page: 0, size: 200 }), [numId]));
  const diariesQ = useDiaries(useMemo(() => ({ collectionId: numId }), [numId]));

  const net = (data?.totalIncome ?? 0) - (data?.totalExpense ?? 0);
  const transactions = transactionsQ.data?.content ?? [];
  const diaries = diariesQ.data ?? [];

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]} edges={['bottom']}>
      <ScrollView contentContainerStyle={styles.container}>
        {collection && (
          <View style={styles.head}>
            <Text style={[styles.name, { color: theme.text }]}>{collection.name}</Text>
            {collection.description ? <Text style={[styles.desc, { color: theme.textSecondary }]}>{collection.description}</Text> : null}
            {collection.tags.length > 0 && (
              <View style={styles.tagRow}>
                {collection.tags.map((t) => (
                  <Badge key={t} label={t} color={theme.primarySurface} textColor={theme.primary} />
                ))}
              </View>
            )}
          </View>
        )}

        <View style={styles.quickActions}>
          <Button
            label="+ 거래"
            variant="outline"
            onPress={() => router.push({ pathname: '/transaction/new', params: { collectionId: numId } })}
            style={styles.quickBtn}
          />
          <Button
            label="+ 일정"
            variant="outline"
            onPress={() => router.push({ pathname: '/schedule/new', params: { collectionId: numId } })}
            style={styles.quickBtn}
          />
          <Button
            label="+ 기록"
            variant="outline"
            onPress={() => router.push({ pathname: '/diary/new', params: { collectionId: numId } })}
            style={styles.quickBtn}
          />
        </View>

        {isLoading ? (
          <ActivityIndicator />
        ) : isError ? (
          <Text style={{ color: theme.danger, fontSize: 14 }}>{error instanceof Error ? error.message : '요약을 불러오지 못했습니다.'}</Text>
        ) : (
          <>
            <View style={styles.cards}>
              <View style={[styles.card, { backgroundColor: theme.successSurface }]}>
                <Text style={[styles.cardLabel, { color: theme.textSecondary }]}>수입</Text>
                <Text style={[styles.cardValue, { color: theme.success }]}>{won(data?.totalIncome ?? 0)}</Text>
              </View>
              <View style={[styles.card, { backgroundColor: theme.dangerSurface }]}>
                <Text style={[styles.cardLabel, { color: theme.textSecondary }]}>지출</Text>
                <Text style={[styles.cardValue, { color: theme.danger }]}>{won(data?.totalExpense ?? 0)}</Text>
              </View>
            </View>
            <View style={styles.netRow}>
              <Text style={[styles.netLabel, { color: theme.textSecondary }]}>순액</Text>
              <Text style={[styles.netValue, { color: net >= 0 ? theme.success : theme.danger }]}>
                {net >= 0 ? '+' : ''}
                {won(net)}
              </Text>
            </View>

            <View style={styles.counts}>
              <Count label="거래" value={data?.transactionCount ?? 0} />
              <Count label="일정" value={data?.scheduleCount ?? 0} />
              <Count label="기록" value={data?.diaryCount ?? 0} />
              <Count label="사진" value={data?.photoCount ?? 0} />
            </View>
          </>
        )}

        <View style={styles.section}>
          <Text style={[styles.sectionTitle, { color: theme.text }]}>연결된 거래</Text>
          {transactionsQ.isLoading ? (
            <ActivityIndicator />
          ) : transactions.length === 0 ? (
            <Text style={{ color: theme.textSecondary, fontSize: 14 }}>연결된 거래가 없어요.</Text>
          ) : (
            <FlatList
              data={transactions}
              scrollEnabled={false}
              keyExtractor={(t) => String(t.id)}
              ItemSeparatorComponent={() => <View style={[styles.sep, { backgroundColor: theme.surfaceMuted }]} />}
              renderItem={({ item: t }) => (
                <Pressable
                  style={styles.txRow}
                  onPress={() => router.push({ pathname: '/transaction/[id]', params: { id: t.id } })}>
                  <View style={styles.txLeft}>
                    <Text style={[styles.txTitle, { color: theme.text }]} numberOfLines={1}>
                      {t.category?.name ?? t.memo ?? '(미분류)'}
                    </Text>
                    <Text style={[styles.txDate, { color: theme.textMuted }]} numberOfLines={1}>
                      {format(parseISO(t.occurredAt), 'M/d HH:mm')}
                      {t.memo && t.category ? ` · ${t.memo}` : ''}
                    </Text>
                  </View>
                  <Text style={[styles.txAmount, { color: amountColor(t.transactionType, theme) }]}>
                    {t.transactionType === 'EXPENSE' ? '-' : t.transactionType === 'INCOME' ? '+' : ''}
                    {won(t.amount)}
                  </Text>
                </Pressable>
              )}
            />
          )}
        </View>

        <View style={styles.section}>
          <Text style={[styles.sectionTitle, { color: theme.text }]}>연결된 기록</Text>
          {diariesQ.isLoading ? (
            <ActivityIndicator />
          ) : diaries.length === 0 ? (
            <Text style={{ color: theme.textSecondary, fontSize: 14 }}>연결된 기록이 없어요.</Text>
          ) : (
            diaries
              .slice()
              .sort((a: Diary, b: Diary) => b.recordedOn.localeCompare(a.recordedOn))
              .map((d) => (
                <Pressable
                  key={d.id}
                  style={[styles.diaryRow, { borderColor: theme.border }]}
                  onPress={() => router.push({ pathname: '/diary/[id]', params: { id: d.id } })}>
                  <Text style={[styles.txDate, { color: theme.textMuted }]}>{d.recordedOn}</Text>
                  <Text style={[styles.diaryTitle, { color: theme.text }]} numberOfLines={1}>
                    {d.title ?? '(제목 없음)'}
                  </Text>
                  <Text style={[styles.diaryContent, { color: theme.textSecondary }]} numberOfLines={2}>
                    {d.content}
                  </Text>
                </Pressable>
              ))
          )}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

function Count({ label, value }: { label: string; value: number }) {
  const theme = useTheme();
  return (
    <View style={[styles.count, { backgroundColor: theme.backgroundElement }]}>
      <Text style={[styles.countValue, { color: theme.text }]}>{value}</Text>
      <Text style={[styles.countLabel, { color: theme.textSecondary }]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  container: { padding: Spacing.three, gap: Spacing.three },
  head: { gap: 4 },
  quickActions: { flexDirection: 'row', gap: 10 },
  quickBtn: { flex: 1, paddingVertical: 10 },
  name: { fontSize: 22, fontWeight: '700' },
  desc: { fontSize: 14 },
  tagRow: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.two, marginTop: 4 },
  cards: { flexDirection: 'row', gap: 12 },
  card: { flex: 1, borderRadius: Radius.lg, padding: 16, gap: 6 },
  cardLabel: { fontSize: 13, fontWeight: '600' },
  cardValue: { fontSize: 20, fontWeight: '700' },
  netRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 4 },
  netLabel: { fontSize: 15, fontWeight: '600' },
  netValue: { fontSize: 18, fontWeight: '700' },
  counts: { flexDirection: 'row', gap: 12, marginTop: 8 },
  count: { flex: 1, alignItems: 'center', borderRadius: Radius.lg, paddingVertical: 16, gap: 4 },
  countValue: { fontSize: 22, fontWeight: '700' },
  countLabel: { fontSize: 13 },
  section: { gap: Spacing.two, marginTop: Spacing.two },
  sectionTitle: { fontSize: 16, fontWeight: '700' },
  sep: { height: 1 },
  txRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingVertical: 10 },
  txLeft: { flex: 1, gap: 2 },
  txTitle: { fontSize: 15, fontWeight: '500' },
  txDate: { fontSize: 12 },
  txAmount: { fontSize: 15, fontWeight: '700' },
  diaryRow: { borderWidth: 1, borderRadius: Radius.md, padding: 12, gap: 4, marginBottom: Spacing.two },
  diaryTitle: { fontSize: 15, fontWeight: '700' },
  diaryContent: { fontSize: 13, lineHeight: 18 },
});
