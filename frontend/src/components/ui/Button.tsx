import { ActivityIndicator, Pressable, StyleSheet, Text, type StyleProp, type ViewStyle } from 'react-native';

import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export type ButtonVariant = 'primary' | 'danger' | 'outline';

export function Button({
  label,
  onPress,
  variant = 'primary',
  disabled,
  loading,
  style,
}: {
  label: string;
  onPress: () => void;
  variant?: ButtonVariant;
  disabled?: boolean;
  loading?: boolean;
  style?: StyleProp<ViewStyle>;
}) {
  const theme = useTheme();
  const isDisabled = disabled || loading;

  const bg = variant === 'primary' ? theme.primary : 'transparent';
  const borderColor = variant === 'danger' ? theme.danger : variant === 'outline' ? theme.border : theme.primary;
  const textColor = variant === 'primary' ? '#fff' : variant === 'danger' ? theme.danger : theme.text;

  return (
    <Pressable
      disabled={isDisabled}
      onPress={onPress}
      style={[
        styles.base,
        { backgroundColor: bg, borderColor, opacity: isDisabled ? 0.6 : 1 },
        style,
      ]}>
      {loading ? (
        <ActivityIndicator color={variant === 'primary' ? '#fff' : theme.primary} />
      ) : (
        <Text style={[styles.label, { color: textColor }]}>{label}</Text>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    borderRadius: Radius.md,
    borderWidth: 1,
    paddingVertical: Spacing.three - 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  label: { fontSize: 16, fontWeight: '600' },
});
