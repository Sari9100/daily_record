import { useState } from 'react';
import { ActivityIndicator, Linking, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { useGoogleConnect, useGoogleDisconnect, useGooglePush, useGoogleSync } from '@/api/google';
import { useTelegramLink, useTelegramUnlink } from '@/api/telegram';
import { confirmAsync } from '@/lib/confirm';

export default function IntegrationsScreen() {
  const connectMut = useGoogleConnect();
  const syncMut = useGoogleSync();
  const pushMut = useGooglePush();
  const disconnectMut = useGoogleDisconnect();
  const tgLinkMut = useTelegramLink();
  const tgUnlinkMut = useTelegramUnlink();
  const [telegramId, setTelegramId] = useState('');
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const run = async (fn: () => Promise<string>) => {
    setError(null);
    setMessage(null);
    try {
      setMessage(await fn());
    } catch (e) {
      setError(e instanceof Error ? e.message : '요청에 실패했습니다.');
    }
  };

  const connect = () =>
    run(async () => {
      const url = await connectMut.mutateAsync();
      await Linking.openURL(url);
      return '구글 동의 창을 열었습니다. 동의 후 돌아와 "지금 동기화"를 눌러주세요.';
    });
  const sync = () =>
    run(async () => {
      const r = await syncMut.mutateAsync();
      return `구글→우리 ${r.fullSync ? '전체' : '증분'} 동기화 완료: 생성 ${r.created} · 수정 ${r.updated} · 삭제 ${r.deleted}`;
    });
  const push = () =>
    run(async () => {
      const r = await pushMut.mutateAsync();
      return `우리→구글 전송 완료: 생성 ${r.inserted} · 수정 ${r.updated} · 삭제 ${r.deleted}`;
    });
  const disconnect = () =>
    run(async () => {
      if (!(await confirmAsync('구글 캘린더 연동을 해제할까요?', '해제'))) return '';
      await disconnectMut.mutateAsync();
      return '구글 연동을 해제했습니다.';
    });

  const linkTelegram = () =>
    run(async () => {
      const id = Number(telegramId.trim());
      if (!telegramId.trim() || !Number.isInteger(id) || id <= 0) {
        throw new Error('텔레그램 숫자 ID를 입력하세요 (봇이 알려주는 from.id).');
      }
      await tgLinkMut.mutateAsync(id);
      return '텔레그램 계정을 연결했습니다.';
    });
  const unlinkTelegram = () =>
    run(async () => {
      if (!(await confirmAsync('텔레그램 연결을 해제할까요?', '해제'))) return '';
      await tgUnlinkMut.mutateAsync();
      setTelegramId('');
      return '텔레그램 연결을 해제했습니다.';
    });

  const busy =
    connectMut.isPending ||
    syncMut.isPending ||
    pushMut.isPending ||
    disconnectMut.isPending ||
    tgLinkMut.isPending ||
    tgUnlinkMut.isPending;

  return (
    <SafeAreaView style={styles.safe} edges={['bottom']}>
      <View style={styles.container}>
        <Text style={styles.title}>구글 캘린더</Text>
        <Text style={styles.help}>
          개인 구글 계정을 연결하면 구글 일정이 앱으로 들어오고(동기화), 앱에서 만든 일정을 구글로 보낼 수
          있습니다(전송). 연결은 한 번만 하면 됩니다.
        </Text>

        <Action label="구글 캘린더 연결" onPress={connect} disabled={busy} primary />
        <Action label="지금 동기화 (구글 → 우리)" onPress={sync} disabled={busy} />
        <Action label="내 일정 전송 (우리 → 구글)" onPress={push} disabled={busy} />
        <Action label="연결 해제" onPress={disconnect} disabled={busy} danger />

        <View style={styles.divider} />

        <Text style={styles.title}>텔레그램 (Hermes 봇)</Text>
        <Text style={styles.help}>
          본인 텔레그램 숫자 ID를 연결하면, 봇으로 보낸 메시지가 내 계정으로 기록됩니다. ID는 봇에게 메시지를
          보내면 확인할 수 있습니다(from.id).
        </Text>
        <TextInput
          style={styles.input}
          value={telegramId}
          onChangeText={setTelegramId}
          keyboardType="number-pad"
          placeholder="예) 123456789"
          placeholderTextColor="#9aa0a6"
        />
        <Action label="텔레그램 연결" onPress={linkTelegram} disabled={busy} primary />
        <Action label="텔레그램 연결 해제" onPress={unlinkTelegram} disabled={busy} danger />

        {busy && <ActivityIndicator style={{ marginTop: 8 }} />}
        {message ? <Text style={styles.message}>{message}</Text> : null}
        {error ? <Text style={styles.error}>{error}</Text> : null}
      </View>
    </SafeAreaView>
  );
}

function Action({
  label,
  onPress,
  disabled,
  primary,
  danger,
}: {
  label: string;
  onPress: () => void;
  disabled?: boolean;
  primary?: boolean;
  danger?: boolean;
}) {
  return (
    <Pressable
      style={[styles.btn, primary && styles.primary, danger && styles.danger, disabled && styles.disabled]}
      disabled={disabled}
      onPress={onPress}>
      <Text style={[styles.btnText, primary && styles.primaryText, danger && styles.dangerText]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#fff' },
  container: { padding: 20, gap: 12 },
  title: { fontSize: 18, fontWeight: '700', color: '#202124' },
  help: { fontSize: 13, color: '#5f6368', lineHeight: 19, marginBottom: 4 },
  divider: { height: 1, backgroundColor: '#eceff1', marginVertical: 12 },
  input: { borderWidth: 1, borderColor: '#dadce0', borderRadius: 10, paddingHorizontal: 14, paddingVertical: 12, fontSize: 16, color: '#202124' },
  btn: { borderRadius: 10, paddingVertical: 14, alignItems: 'center', borderWidth: 1, borderColor: '#dadce0' },
  btnText: { fontSize: 15, color: '#3c4043', fontWeight: '600' },
  primary: { backgroundColor: '#1a73e8', borderColor: '#1a73e8' },
  primaryText: { color: '#fff' },
  danger: { borderColor: '#d93025' },
  dangerText: { color: '#d93025' },
  disabled: { opacity: 0.5 },
  message: { fontSize: 14, color: '#188038', marginTop: 8, lineHeight: 20 },
  error: { fontSize: 14, color: '#d93025', marginTop: 8, lineHeight: 20 },
});
