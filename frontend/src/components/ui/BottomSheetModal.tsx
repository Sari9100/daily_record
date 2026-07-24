import { type ReactNode } from 'react';
import { Modal, StyleSheet, Text, View } from 'react-native';

import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/** 하단시트형 Modal — 계좌/카테고리/묶음/가족 등 CRUD 화면들이 공통으로 쓰던 backdrop+슬라이드 카드 패턴. */
export function BottomSheetModal({
  visible,
  onClose,
  title,
  children,
}: {
  visible: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
}) {
  const theme = useTheme();
  return (
    <Modal visible={visible} animationType="slide" transparent onRequestClose={onClose}>
      <View style={[styles.backdrop, { backgroundColor: theme.overlay }]}>
        <View style={[styles.card, { backgroundColor: theme.background }]}>
          <Text style={[styles.title, { color: theme.text }]}>{title}</Text>
          {children}
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, justifyContent: 'flex-end' },
  card: { borderTopLeftRadius: 16, borderTopRightRadius: 16, padding: 20, gap: Spacing.two },
  title: { fontSize: 18, fontWeight: '700', marginBottom: 4 },
});
