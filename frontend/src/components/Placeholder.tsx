import { StyleSheet, Text, View } from 'react-native';

/** 아직 구현 전 화면용 공통 플레이스홀더. 도메인별 화면이 채워지면 교체. */
export function Placeholder({ title, note }: { title: string; note?: string }) {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>{title}</Text>
      <Text style={styles.note}>{note ?? '곧 만들 화면이에요.'}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24, gap: 8, backgroundColor: '#fff' },
  title: { fontSize: 20, fontWeight: '700', color: '#202124' },
  note: { fontSize: 14, color: '#5f6368', textAlign: 'center' },
});
