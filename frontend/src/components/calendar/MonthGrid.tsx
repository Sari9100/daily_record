import { Pressable, StyleSheet, Text, View } from 'react-native';
import {
  eachDayOfInterval,
  endOfMonth,
  endOfWeek,
  format,
  isSameDay,
  isSameMonth,
  startOfMonth,
  startOfWeek,
} from 'date-fns';

import { Radius } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
const MAX_DOTS = 3;
const MAX_BAR_LANES = 2;

export type DayMarker = { color: string };
/** 시작일~종료일에 걸친 항목(다일 일정, 묶음 기간) — 주(week) 안에서 이어지는 막대로 그려진다. */
export type MonthBar = { id: string; label: string; color: string; startDate: string; endDate: string };

type PlacedBar = MonthBar & { startCol: number; span: number; lane: number };

/**
 * 월간 캘린더 그리드. 외부 라이브러리 없이 date-fns로 직접 구성(네이티브/웹 동일 동작).
 * 주 단위로 렌더링해서, `bars`로 넘긴 기간 항목을 그 주의 날짜 칸에 걸쳐 이어지는 막대로 표시한다
 * (구글 캘린더식). 하루짜리 항목은 기존처럼 `markersByDate` 점(dot)으로.
 */
export function MonthGrid({
  month,
  markersByDate,
  bars = [],
  selectedDate,
  onSelectDate,
}: {
  month: Date;
  markersByDate: Record<string, DayMarker[]>;
  bars?: MonthBar[];
  selectedDate: string | null;
  onSelectDate: (date: string) => void;
}) {
  const theme = useTheme();
  const gridStart = startOfWeek(startOfMonth(month));
  const gridEnd = endOfWeek(endOfMonth(month));
  const days = eachDayOfInterval({ start: gridStart, end: gridEnd });
  const weeks: Date[][] = [];
  for (let i = 0; i < days.length; i += 7) weeks.push(days.slice(i, i + 7));

  return (
    <View>
      <View style={styles.weekdayRow}>
        {WEEKDAYS.map((w) => (
          <Text key={w} style={[styles.weekday, { color: theme.textMuted }]}>
            {w}
          </Text>
        ))}
      </View>

      {weeks.map((week, wi) => {
        const weekKeys = week.map((d) => format(d, 'yyyy-MM-dd'));
        const placed = placeBarsForWeek(bars, weekKeys);

        return (
          <View key={wi}>
            <View style={styles.row}>
              {week.map((day) => {
                const key = format(day, 'yyyy-MM-dd');
                const inMonth = isSameMonth(day, month);
                const selected = selectedDate === key;
                const markers = markersByDate[key] ?? [];
                return (
                  <Pressable key={key} style={styles.cell} onPress={() => onSelectDate(key)}>
                    <View
                      style={[
                        styles.cellInner,
                        selected && { backgroundColor: theme.primary },
                        !selected && isSameDay(day, new Date()) && { borderWidth: 1, borderColor: theme.primary },
                      ]}>
                      <Text style={[styles.dayText, { color: selected ? '#fff' : inMonth ? theme.text : theme.textMuted }]}>
                        {format(day, 'd')}
                      </Text>
                    </View>
                    <View style={styles.dots}>
                      {markers.slice(0, MAX_DOTS).map((m, i) => (
                        <View key={i} style={[styles.dot, { backgroundColor: m.color }]} />
                      ))}
                    </View>
                  </Pressable>
                );
              })}
            </View>

            {Array.from({ length: Math.min(maxLane(placed) + 1, MAX_BAR_LANES) }).map((_, lane) => (
              <View key={lane} style={styles.row}>
                {week.map((_, colIdx) => {
                  const bar = placed.find((b) => b.lane === lane && colIdx >= b.startCol && colIdx < b.startCol + b.span);
                  if (!bar) return <View key={colIdx} style={styles.barCell} />;
                  const isStart = colIdx === bar.startCol;
                  const isEnd = colIdx === bar.startCol + bar.span - 1;
                  return (
                    <View
                      key={colIdx}
                      style={[
                        styles.barCell,
                        styles.barFill,
                        {
                          backgroundColor: bar.color,
                          borderTopLeftRadius: isStart ? Radius.sm / 2 : 0,
                          borderBottomLeftRadius: isStart ? Radius.sm / 2 : 0,
                          borderTopRightRadius: isEnd ? Radius.sm / 2 : 0,
                          borderBottomRightRadius: isEnd ? Radius.sm / 2 : 0,
                        },
                      ]}>
                      {isStart && (
                        <Text numberOfLines={1} style={styles.barLabel}>
                          {bar.label}
                        </Text>
                      )}
                    </View>
                  );
                })}
              </View>
            ))}
          </View>
        );
      })}
    </View>
  );
}

/** 이 주(week)와 겹치는 막대만 골라 시작열/칸수를 구하고, 겹치지 않게 lane(행)을 그리디 배정. */
function placeBarsForWeek(bars: MonthBar[], weekKeys: string[]): PlacedBar[] {
  const weekStart = weekKeys[0];
  const weekEnd = weekKeys[6];

  const clipped = bars
    .map((b) => {
      const s = b.startDate < weekStart ? weekStart : b.startDate;
      const e = b.endDate > weekEnd ? weekEnd : b.endDate;
      if (s > e) return null;
      const startCol = weekKeys.indexOf(s);
      const endCol = weekKeys.indexOf(e);
      if (startCol === -1 || endCol === -1) return null;
      return { ...b, startCol, span: endCol - startCol + 1 };
    })
    .filter((b): b is MonthBar & { startCol: number; span: number } => b != null)
    .sort((a, b) => a.startCol - b.startCol || b.span - a.span);

  const laneEndCol: number[] = [];
  return clipped.map((b) => {
    let lane = laneEndCol.findIndex((end) => end < b.startCol);
    if (lane === -1) {
      lane = laneEndCol.length;
      laneEndCol.push(b.startCol + b.span - 1);
    } else {
      laneEndCol[lane] = b.startCol + b.span - 1;
    }
    return { ...b, lane };
  });
}

function maxLane(placed: PlacedBar[]): number {
  return placed.reduce((m, b) => Math.max(m, b.lane), -1);
}

const styles = StyleSheet.create({
  weekdayRow: { flexDirection: 'row' },
  weekday: { flex: 1, textAlign: 'center', fontSize: 12, fontWeight: '600' },
  row: { flexDirection: 'row' },
  cell: { flex: 1, alignItems: 'center', paddingVertical: 4, gap: 3 },
  cellInner: { width: 32, height: 32, borderRadius: 16, alignItems: 'center', justifyContent: 'center' },
  dayText: { fontSize: 14 },
  dots: { flexDirection: 'row', gap: 3, height: 6 },
  dot: { width: 5, height: 5, borderRadius: 2.5 },
  barCell: { flex: 1, height: 15, paddingHorizontal: 1 },
  barFill: { justifyContent: 'center', paddingHorizontal: 4 },
  barLabel: { fontSize: 9, fontWeight: '600', color: '#fff' },
});
