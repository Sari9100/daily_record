import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Button, TextField } from '@/components/ui';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { changePasswordRequest } from '@/api/auth';
import { signOut } from '@/api/session';
import { alertAsync } from '@/lib/confirm';

export default function ChangePasswordScreen() {
  const theme = useTheme();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    if (!currentPassword) {
      setError('현재 비밀번호를 입력하세요.');
      return;
    }
    if (newPassword.length < 8) {
      setError('새 비밀번호는 8자 이상이어야 합니다.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setError('새 비밀번호가 일치하지 않습니다.');
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await changePasswordRequest(currentPassword, newPassword);
      // 성공 시 서버가 이 계정의 모든 세션(현재 기기 포함)을 폐기하므로, 곧바로 로그아웃 처리.
      await alertAsync('비밀번호가 변경됐어요. 새 비밀번호로 다시 로그인해주세요.');
      await signOut();
    } catch (e) {
      setError(e instanceof Error ? e.message : '비밀번호 변경에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]} edges={['bottom']}>
      <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
          <TextField
            label="현재 비밀번호"
            value={currentPassword}
            onChangeText={setCurrentPassword}
            secureTextEntry
            placeholder="현재 비밀번호"
          />
          <TextField
            label="새 비밀번호 (8자 이상)"
            value={newPassword}
            onChangeText={setNewPassword}
            secureTextEntry
            placeholder="새 비밀번호"
          />
          <TextField
            label="새 비밀번호 확인"
            value={confirmPassword}
            onChangeText={setConfirmPassword}
            secureTextEntry
            placeholder="새 비밀번호 확인"
            onSubmitEditing={submit}
          />

          {error && <Text style={{ color: theme.danger, fontSize: 14 }}>{error}</Text>}

          <Button label="변경" onPress={submit} loading={submitting} disabled={submitting} />
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  flex: { flex: 1 },
  container: { padding: Spacing.three, gap: Spacing.three },
});
