import { type ReactNode } from 'react';
import { Platform, Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';

import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

const PANEL_WIDTH = 440;
const WIDE_BREAKPOINT = 700;

/**
 * 거래입력/일정등록처럼 목록 화면 위에 얹는 폼을 우측 슬라이드 패널로 보여줄 때 쓰는 레이아웃.
 * 라우트 자체가 `presentation: 'transparentModal'` + `animation: 'slide_from_right'`이라
 * 뒤 화면이 비쳐 보이므로, 여기선 배경 오버레이(탭하면 닫힘)와 패널만 그린다.
 * 좁은 화면(모바일)에서는 패널이 전체 폭을 차지해 사실상 풀스크린 폼이 된다.
 */
export function SidePanel({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  const theme = useTheme();
  const { width } = useWindowDimensions();
  const isWide = Platform.OS === 'web' && width >= WIDE_BREAKPOINT;

  return (
    <View style={styles.root}>
      <Pressable style={[StyleSheet.absoluteFill, { backgroundColor: theme.overlay }]} onPress={onClose} />
      <View style={[styles.panel, { backgroundColor: theme.background, width: isWide ? PANEL_WIDTH : '100%' }]}>
        <View style={[styles.header, { borderBottomColor: theme.border }]}>
          <Text style={[styles.title, { color: theme.text }]}>{title}</Text>
          <Pressable onPress={onClose} hitSlop={8}>
            <Ionicons name="close" size={22} color={theme.text} />
          </Pressable>
        </View>
        <View style={styles.body}>{children}</View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, flexDirection: 'row', justifyContent: 'flex-end' },
  panel: {
    height: '100%',
    shadowColor: '#000',
    shadowOpacity: 0.15,
    shadowRadius: 12,
    shadowOffset: { width: -2, height: 0 },
    elevation: 8,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.three,
    borderBottomWidth: 1,
  },
  title: { fontSize: 18, fontWeight: '700' },
  body: { flex: 1 },
});
