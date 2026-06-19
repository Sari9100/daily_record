import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useLocalSearchParams } from 'expo-router';

import { useCachedCollection, useCollectionSummary } from '@/api/collection';

function won(n: number): string {
  return `${Math.round(n).toLocaleString('ko-KR')}원`;
}

export default function CollectionSummaryScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const numId = Number(id);
  const collection = useCachedCollection(numId);
  const { data, isLoading, isError, error } = useCollectionSummary(numId);

  const net = (data?.totalIncome ?? 0) - (data?.totalExpense ?? 0);

  return (
    <SafeAreaView style={styles.safe} edges={['bottom']}>
      <View style={styles.container}>
        {collection && (
          <View style={styles.head}>
            <Text style={styles.name}>{collection.name}</Text>
            {collection.description ? <Text style={styles.desc}>{collection.description}</Text> : null}
          </View>
        )}

        {isLoading ? (
          <ActivityIndicator />
        ) : isError ? (
          <Text style={styles.error}>{error instanceof Error ? error.message : '요약을 불러오지 못했습니다.'}</Text>
        ) : (
          <>
            <View style={styles.cards}>
              <View style={[styles.card, { backgroundColor: '#e6f4ea' }]}>
                <Text style={styles.cardLabel}>수입</Text>
                <Text style={[styles.cardValue, { color: '#188038' }]}>{won(data?.totalIncome ?? 0)}</Text>
              </View>
              <View style={[styles.card, { backgroundColor: '#fce8e6' }]}>
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

            <View style={styles.counts}>
              <Count label="거래" value={data?.transactionCount ?? 0} />
              <Count label="일정" value={data?.scheduleCount ?? 0} />
              <Count label="기록" value={data?.diaryCount ?? 0} />
              <Count label="사진" value={data?.photoCount ?? 0} />
            </View>
          </>
        )}
      </View>
    </SafeAreaView>
  );
}

function Count({ label, value }: { label: string; value: number }) {
  return (
    <View style={styles.count}>
      <Text style={styles.countValue}>{value}</Text>
      <Text style={styles.countLabel}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#fff' },
  container: { padding: 20, gap: 16 },
  head: { gap: 4 },
  name: { fontSize: 22, fontWeight: '700', color: '#202124' },
  desc: { fontSize: 14, color: '#5f6368' },
  error: { color: '#d93025', fontSize: 14 },
  cards: { flexDirection: 'row', gap: 12 },
  card: { flex: 1, borderRadius: 12, padding: 16, gap: 6 },
  cardLabel: { fontSize: 13, color: '#3c4043', fontWeight: '600' },
  cardValue: { fontSize: 20, fontWeight: '700' },
  netRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 4 },
  netLabel: { fontSize: 15, color: '#3c4043', fontWeight: '600' },
  netValue: { fontSize: 18, fontWeight: '700' },
  counts: { flexDirection: 'row', gap: 12, marginTop: 8 },
  count: { flex: 1, alignItems: 'center', backgroundColor: '#f8f9fa', borderRadius: 12, paddingVertical: 16, gap: 4 },
  countValue: { fontSize: 22, fontWeight: '700', color: '#202124' },
  countLabel: { fontSize: 13, color: '#5f6368' },
});
