import { useMemo } from 'react';
import { ActivityIndicator, FlatList, Image, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { endOfMonth, format, startOfMonth } from 'date-fns';

import { useDiaries } from '@/api/diary';
import { mediaUrl } from '@/domain/config';
import type { Diary } from '@/domain/diary';

function DiaryCard({ d, onPress }: { d: Diary; onPress: () => void }) {
  const cover = d.photos.find((p) => p.url);
  return (
    <Pressable style={styles.card} onPress={onPress}>
      {cover?.url && <Image source={{ uri: mediaUrl(cover.url) }} style={styles.cover} resizeMode="cover" />}
      <View style={styles.cardBody}>
        <Text style={styles.date}>{d.recordedOn}</Text>
        {d.title ? <Text style={styles.title}>{d.title}</Text> : null}
        <Text style={styles.content} numberOfLines={2}>
          {d.content}
        </Text>
        {d.photos.length > 0 && (
          <Text style={styles.photoCount}>
            <Ionicons name="image-outline" size={12} color="#9aa0a6" /> {d.photos.length}
          </Text>
        )}
      </View>
    </Pressable>
  );
}

export default function DiaryScreen() {
  const router = useRouter();
  const range = useMemo(() => {
    const now = new Date();
    return { from: format(startOfMonth(now), 'yyyy-MM-dd'), to: format(endOfMonth(now), 'yyyy-MM-dd') };
  }, []);
  const { data, isLoading, isError, error, refetch, isRefetching } = useDiaries(range);

  const diaries = useMemo(
    () => [...(data ?? [])].sort((a, b) => (b.recordedAt ?? '').localeCompare(a.recordedAt ?? '')),
    [data],
  );

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>기록</Text>
      </View>

      {isLoading ? (
        <View style={styles.center}>
          <ActivityIndicator />
        </View>
      ) : isError ? (
        <View style={styles.center}>
          <Text style={styles.errorText}>{error instanceof Error ? error.message : '기록을 불러오지 못했습니다.'}</Text>
        </View>
      ) : (
        <FlatList
          data={diaries}
          keyExtractor={(d) => String(d.id)}
          onRefresh={refetch}
          refreshing={isRefetching}
          contentContainerStyle={diaries.length === 0 ? styles.emptyBox : styles.list}
          ListEmptyComponent={<Text style={styles.emptyText}>이번 달 기록이 없어요.{'\n'}+ 로 첫 기록을 남겨보세요.</Text>}
          renderItem={({ item }) => (
            <DiaryCard d={item} onPress={() => router.push({ pathname: '/diary/[id]', params: { id: item.id } })} />
          )}
        />
      )}

      <Pressable style={styles.fab} onPress={() => router.push('/diary/new')}>
        <Ionicons name="add" size={28} color="#fff" />
      </Pressable>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#fff' },
  header: { paddingHorizontal: 16, paddingVertical: 12 },
  headerTitle: { fontSize: 22, fontWeight: '700', color: '#202124' },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  errorText: { textAlign: 'center', color: '#d93025', fontSize: 14 },
  list: { padding: 16, gap: 14, paddingBottom: 96 },
  emptyBox: { flexGrow: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  emptyText: { textAlign: 'center', color: '#5f6368', fontSize: 15, lineHeight: 22 },
  card: { borderWidth: 1, borderColor: '#eceff1', borderRadius: 12, overflow: 'hidden', backgroundColor: '#fff' },
  cover: { width: '100%', height: 180, backgroundColor: '#f1f3f4' },
  cardBody: { padding: 14, gap: 4 },
  date: { fontSize: 12, color: '#9aa0a6' },
  title: { fontSize: 16, fontWeight: '700', color: '#202124' },
  content: { fontSize: 14, color: '#3c4043', lineHeight: 20 },
  photoCount: { fontSize: 12, color: '#9aa0a6', marginTop: 2 },
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
    elevation: 4,
  },
});
