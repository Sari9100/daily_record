package com.familyos.timeline;

import com.familyos.ledger.dto.StatTotals;
import com.familyos.ledger.mapper.TransactionStatisticsMapper;
import com.familyos.support.AbstractIntegrationTest;
import com.familyos.timeline.dto.TimelineRow;
import com.familyos.timeline.mapper.TimelineMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실 MySQL 통합테스트: MyBatis 매퍼가 실제로 실행되고 record 생성자 매핑이 되는지 검증.
 * (단위테스트가 못 잡는 영역 — UNION/CONVERT_TZ/record 매핑/매퍼 XML 파싱)
 */
class MyBatisMapperIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    TransactionStatisticsMapper statisticsMapper;
    @Autowired
    TimelineMapper timelineMapper;

    @Test
    void 통계_매퍼_record_매핑이_동작한다() {
        // 빈 데이터: coalesce 로 0 행이 반환되어 record 로 매핑되어야 함
        StatTotals totals = statisticsMapper.selectTotals(1L, 1L, true, null, null, null);
        assertThat(totals).isNotNull();
        assertThat(totals.totalIncome()).isNotNull();
        assertThat(totals.totalExpense()).isNotNull();
    }

    @Test
    void 타임라인_매퍼_record_매핑이_동작한다() {
        List<TimelineRow> rows = timelineMapper.timeline(
                1L, 1L, true,
                LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-30"), "+09:00");
        assertThat(rows).isEmpty(); // 데이터 없음 → 빈 목록(예외 없이 실행되는 것이 핵심)
    }
}
