import { useState } from 'react';
import { ActivityIndicator, Linking, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Button, TextField } from '@/components/ui';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useGoogleConnect, useGoogleDisconnect, useGooglePush, useGoogleSync } from '@/api/google';
import { useTelegramLink, useTelegramUnlink } from '@/api/telegram';
import { confirmAsync } from '@/lib/confirm';

export default function IntegrationsScreen() {
  const theme = useTheme();
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
    <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]} edges={['bottom']}>
      <View style={styles.container}>
        <Text style={[styles.title, { color: theme.text }]}>구글 캘린더</Text>
        <Text style={[styles.help, { color: theme.textSecondary }]}>
          개인 구글 계정을 연결하면 구글 일정이 앱으로 들어오고(동기화), 앱에서 만든 일정을 구글로 보낼 수
          있습니다(전송). 연결은 한 번만 하면 됩니다.
        </Text>

        <Button label="구글 캘린더 연결" onPress={connect} disabled={busy} />
        <Button label="지금 동기화 (구글 → 우리)" variant="outline" onPress={sync} disabled={busy} />
        <Button label="내 일정 전송 (우리 → 구글)" variant="outline" onPress={push} disabled={busy} />
        <Button label="연결 해제" variant="danger" onPress={disconnect} disabled={busy} />

        <View style={[styles.divider, { backgroundColor: theme.border }]} />

        <Text style={[styles.title, { color: theme.text }]}>텔레그램 (Hermes 봇)</Text>
        <Text style={[styles.help, { color: theme.textSecondary }]}>
          본인 텔레그램 숫자 ID를 연결하면, 봇으로 보낸 메시지가 내 계정으로 기록됩니다. ID는 봇에게 메시지를
          보내면 확인할 수 있습니다(from.id).
        </Text>
        <TextField label="텔레그램 ID" value={telegramId} onChangeText={setTelegramId} keyboardType="number-pad" placeholder="예) 123456789" />
        <Button label="텔레그램 연결" onPress={linkTelegram} disabled={busy} />
        <Button label="텔레그램 연결 해제" variant="danger" onPress={unlinkTelegram} disabled={busy} />

        {busy && <ActivityIndicator style={{ marginTop: 8 }} />}
        {message ? <Text style={{ color: theme.success, fontSize: 14, marginTop: 8, lineHeight: 20 }}>{message}</Text> : null}
        {error ? <Text style={{ color: theme.danger, fontSize: 14, marginTop: 8, lineHeight: 20 }}>{error}</Text> : null}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  container: { padding: 20, gap: Spacing.two + 4 },
  title: { fontSize: 18, fontWeight: '700' },
  help: { fontSize: 13, lineHeight: 19, marginBottom: 4 },
  divider: { height: 1, marginVertical: Spacing.three },
});
