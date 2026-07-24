import { type ReactNode, useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';

import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { BottomSheetModal } from './BottomSheetModal';
import { TextField } from './TextField';

export type ComboBoxOption<T extends string | number> = { value: T; label: string };

/**
 * 검색 가능한 단일 선택 콤보박스. RN엔 네이티브 드롭다운이 없어서, 입력창처럼 보이는 필드를
 * 탭하면 검색창+필터된 목록이 바텀시트로 뜨는 방식으로 구현(선택지가 계속 늘어나는 묶음 선택 등에 적합).
 */
export function ComboBox<T extends string | number>({
  label,
  options,
  value,
  onChange,
  placeholder = '선택',
  searchPlaceholder = '검색',
  emptyText = '검색 결과가 없어요.',
  footer,
}: {
  label?: string;
  options: ComboBoxOption<T>[];
  value: T | null;
  onChange: (value: T | null) => void;
  placeholder?: string;
  searchPlaceholder?: string;
  emptyText?: string;
  footer?: ReactNode;
}) {
  const theme = useTheme();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');

  const selected = options.find((o) => o.value === value);
  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return options;
    return options.filter((o) => o.label.toLowerCase().includes(q));
  }, [options, query]);

  const openSheet = () => {
    setQuery('');
    setOpen(true);
  };

  const select = (v: T) => {
    onChange(v);
    setOpen(false);
  };

  return (
    <View style={styles.field}>
      {label ? <Text style={[styles.label, { color: theme.textSecondary }]}>{label}</Text> : null}
      <Pressable style={[styles.input, { borderColor: theme.border }]} onPress={openSheet}>
        <Text style={[styles.inputText, { color: selected ? theme.text : theme.textMuted }]} numberOfLines={1}>
          {selected?.label ?? placeholder}
        </Text>
        <Ionicons name="chevron-down" size={18} color={theme.textMuted} />
      </Pressable>

      <BottomSheetModal visible={open} onClose={() => setOpen(false)} title={label ?? '선택'}>
        <TextField label={searchPlaceholder} value={query} onChangeText={setQuery} placeholder={searchPlaceholder} autoFocus />
        <ScrollView style={styles.list} keyboardShouldPersistTaps="handled">
          {filtered.length === 0 ? (
            <Text style={{ color: theme.textSecondary, fontSize: 14, paddingVertical: Spacing.two }}>{emptyText}</Text>
          ) : (
            filtered.map((o) => {
              const isSelected = o.value === value;
              return (
                <Pressable
                  key={String(o.value)}
                  style={[styles.option, isSelected && { backgroundColor: theme.primarySurface }]}
                  onPress={() => select(o.value)}>
                  <Text style={[styles.optionText, { color: isSelected ? theme.primary : theme.text }]} numberOfLines={1}>
                    {o.label}
                  </Text>
                  {isSelected && <Ionicons name="checkmark" size={18} color={theme.primary} />}
                </Pressable>
              );
            })
          )}
        </ScrollView>
        {footer}
      </BottomSheetModal>
    </View>
  );
}

const styles = StyleSheet.create({
  field: { gap: Spacing.two },
  label: { fontSize: 13, fontWeight: '600' },
  input: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderWidth: 1,
    borderRadius: Radius.md,
    paddingHorizontal: 14,
    paddingVertical: 12,
  },
  inputText: { fontSize: 16, flex: 1 },
  list: { maxHeight: 320, marginTop: Spacing.two },
  option: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 12,
    paddingHorizontal: 4,
  },
  optionText: { fontSize: 15, flex: 1 },
});
