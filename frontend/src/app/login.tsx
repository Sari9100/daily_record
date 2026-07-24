import { useState } from 'react';
import { KeyboardAvoidingView, Platform, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

import { Button, TextField } from '@/components/ui';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { signIn } from '@/api/session';

const schema = z.object({
  loginId: z.string().min(1, '아이디를 입력하세요.'),
  password: z.string().min(1, '비밀번호를 입력하세요.'),
});
type LoginForm = z.infer<typeof schema>;

export default function LoginScreen() {
  const theme = useTheme();
  const [serverError, setServerError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginForm>({
    resolver: zodResolver(schema),
    defaultValues: { loginId: '', password: '' },
  });

  const onSubmit = async (values: LoginForm) => {
    setServerError(null);
    setSubmitting(true);
    try {
      await signIn(values.loginId, values.password);
      // 성공 시 AuthGate 가 메인으로 리디렉션
    } catch (e) {
      setServerError(e instanceof Error ? e.message : '로그인에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]}>
      <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <View style={styles.container}>
          <Text style={[styles.brand, { color: theme.primary }]}>Family OS</Text>
          <Text style={[styles.subtitle, { color: theme.textSecondary }]}>가족 생활기록</Text>

          <Controller
            control={control}
            name="loginId"
            render={({ field: { value, onChange, onBlur } }) => (
              <TextField
                label="아이디"
                value={value}
                onChangeText={onChange}
                onBlur={onBlur}
                autoCapitalize="none"
                autoCorrect={false}
                placeholder="loginId"
                error={errors.loginId?.message}
              />
            )}
          />

          <Controller
            control={control}
            name="password"
            render={({ field: { value, onChange, onBlur } }) => (
              <TextField
                label="비밀번호"
                value={value}
                onChangeText={onChange}
                onBlur={onBlur}
                secureTextEntry
                placeholder="password"
                onSubmitEditing={handleSubmit(onSubmit)}
                error={errors.password?.message}
              />
            )}
          />

          {serverError && <Text style={{ color: theme.danger, fontSize: 14, textAlign: 'center' }}>{serverError}</Text>}

          <Button label="로그인" onPress={handleSubmit(onSubmit)} loading={submitting} disabled={submitting} style={styles.submitBtn} />
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  flex: { flex: 1 },
  container: { flex: 1, justifyContent: 'center', paddingHorizontal: Spacing.four, gap: Spacing.three, maxWidth: 420, width: '100%', alignSelf: 'center' },
  brand: { fontSize: 32, fontWeight: '700', textAlign: 'center' },
  subtitle: { fontSize: 14, textAlign: 'center', marginBottom: Spacing.two + 8 },
  submitBtn: { marginTop: Spacing.one + 4 },
});
