import { ActivityIndicator, FlatList, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { useFamilyMembers } from '@/api/schedule';

export default function FamilyScreen() {
  const membersQ = useFamilyMembers();

  return (
    <SafeAreaView style={styles.safe} edges={['bottom']}>
      {membersQ.isLoading ? (
        <View style={styles.center}>
          <ActivityIndicator />
        </View>
      ) : (
        <FlatList
          data={membersQ.data ?? []}
          keyExtractor={(m) => String(m.personId)}
          ItemSeparatorComponent={() => <View style={styles.sep} />}
          contentContainerStyle={styles.list}
          ListHeaderComponent={<Text style={styles.note}>구성원 추가/자녀 계정 생성은 추후 지원합니다.</Text>}
          renderItem={({ item }) => (
            <View style={styles.row}>
              <Text style={styles.name}>{item.name}</Text>
              <View style={styles.tags}>
                <Text style={styles.role}>{item.role === 'PARENT' ? '부모' : '자녀'}</Text>
                {!item.hasAccount && <Text style={styles.noAccount}>계정없음</Text>}
              </View>
            </View>
          )}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#fff' },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  list: { padding: 16 },
  note: { fontSize: 13, color: '#9aa0a6', marginBottom: 12 },
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingVertical: 14 },
  name: { fontSize: 16, color: '#202124', fontWeight: '500' },
  tags: { flexDirection: 'row', gap: 8, alignItems: 'center' },
  role: { fontSize: 13, color: '#5f6368' },
  noAccount: { fontSize: 12, color: '#9aa0a6' },
  sep: { height: 1, backgroundColor: '#f1f3f4' },
});
