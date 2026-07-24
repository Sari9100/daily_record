import { Pressable, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';

import { useTheme } from '@/hooks/use-theme';

/** 우하단 고정 원형 액션 버튼. 목록 화면(가계부/일정/기록/계좌/카테고리/묶음)에서 공통 사용. */
export function Fab({
  icon = 'add',
  onPress,
  size = 56,
}: {
  icon?: keyof typeof Ionicons.glyphMap;
  onPress: () => void;
  size?: number;
}) {
  const theme = useTheme();
  return (
    <Pressable
      style={[
        styles.fab,
        { width: size, height: size, borderRadius: size / 2, backgroundColor: theme.primary },
      ]}
      onPress={onPress}>
      <Ionicons name={icon} size={size * 0.5} color="#fff" />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  fab: {
    position: 'absolute',
    right: 20,
    bottom: 24,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOpacity: 0.2,
    shadowRadius: 6,
    shadowOffset: { width: 0, height: 3 },
    elevation: 4,
  },
});
