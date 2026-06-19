import { useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

import { signIn } from '@/api/session';

const schema = z.object({
  loginId: z.string().min(1, '아이디를 입력하세요.'),
  password: z.string().min(1, '비밀번호를 입력하세요.'),
});
type LoginForm = z.infer<typeof schema>;

export default function LoginScreen() {
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
    <SafeAreaView style={styles.safe}>
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <View style={styles.container}>
          <Text style={styles.brand}>Family OS</Text>
          <Text style={styles.subtitle}>가족 생활기록</Text>

          <View style={styles.field}>
            <Text style={styles.label}>아이디</Text>
            <Controller
              control={control}
              name="loginId"
              render={({ field: { value, onChange, onBlur } }) => (
                <TextInput
                  style={styles.input}
                  value={value}
                  onChangeText={onChange}
                  onBlur={onBlur}
                  autoCapitalize="none"
                  autoCorrect={false}
                  placeholder="loginId"
                  placeholderTextColor="#9aa0a6"
                />
              )}
            />
            {errors.loginId && <Text style={styles.error}>{errors.loginId.message}</Text>}
          </View>

          <View style={styles.field}>
            <Text style={styles.label}>비밀번호</Text>
            <Controller
              control={control}
              name="password"
              render={({ field: { value, onChange, onBlur } }) => (
                <TextInput
                  style={styles.input}
                  value={value}
                  onChangeText={onChange}
                  onBlur={onBlur}
                  secureTextEntry
                  placeholder="password"
                  placeholderTextColor="#9aa0a6"
                  onSubmitEditing={handleSubmit(onSubmit)}
                />
              )}
            />
            {errors.password && <Text style={styles.error}>{errors.password.message}</Text>}
          </View>

          {serverError && <Text style={styles.serverError}>{serverError}</Text>}

          <Pressable
            style={[styles.button, submitting && styles.buttonDisabled]}
            disabled={submitting}
            onPress={handleSubmit(onSubmit)}>
            {submitting ? (
              <ActivityIndicator color="#fff" />
            ) : (
              <Text style={styles.buttonText}>로그인</Text>
            )}
          </Pressable>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#fff' },
  flex: { flex: 1 },
  container: { flex: 1, justifyContent: 'center', paddingHorizontal: 24, gap: 16, maxWidth: 420, width: '100%', alignSelf: 'center' },
  brand: { fontSize: 32, fontWeight: '700', textAlign: 'center', color: '#1a73e8' },
  subtitle: { fontSize: 14, textAlign: 'center', color: '#5f6368', marginBottom: 16 },
  field: { gap: 6 },
  label: { fontSize: 13, color: '#3c4043', fontWeight: '600' },
  input: {
    borderWidth: 1,
    borderColor: '#dadce0',
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: 12,
    fontSize: 16,
    color: '#202124',
  },
  error: { color: '#d93025', fontSize: 12 },
  serverError: { color: '#d93025', fontSize: 14, textAlign: 'center' },
  button: {
    backgroundColor: '#1a73e8',
    borderRadius: 10,
    paddingVertical: 14,
    alignItems: 'center',
    marginTop: 8,
  },
  buttonDisabled: { opacity: 0.6 },
  buttonText: { color: '#fff', fontSize: 16, fontWeight: '600' },
});
