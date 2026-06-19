import { Tabs } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';

/**
 * 메인 탭 (docs/07 §1-2). 5섹션: 타임라인·가계부·일정·기록·더보기.
 * MVP 는 모바일/웹 공통 하단 탭. 웹 사이드바 분기는 후속(현재는 동일 탭).
 */
export default function TabsLayout() {
  return (
    <Tabs
      screenOptions={{
        tabBarActiveTintColor: '#1a73e8',
        headerTitleStyle: { fontWeight: '700' },
      }}>
      <Tabs.Screen
        name="index"
        options={{
          title: '타임라인',
          tabBarIcon: ({ color, size }) => <Ionicons name="home-outline" color={color} size={size} />,
        }}
      />
      <Tabs.Screen
        name="ledger"
        options={{
          title: '가계부',
          tabBarIcon: ({ color, size }) => <Ionicons name="wallet-outline" color={color} size={size} />,
        }}
      />
      <Tabs.Screen
        name="schedule"
        options={{
          title: '일정',
          tabBarIcon: ({ color, size }) => <Ionicons name="calendar-outline" color={color} size={size} />,
        }}
      />
      <Tabs.Screen
        name="diary"
        options={{
          title: '기록',
          tabBarIcon: ({ color, size }) => <Ionicons name="image-outline" color={color} size={size} />,
        }}
      />
      <Tabs.Screen
        name="more"
        options={{
          title: '더보기',
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="ellipsis-horizontal" color={color} size={size} />
          ),
        }}
      />
    </Tabs>
  );
}
