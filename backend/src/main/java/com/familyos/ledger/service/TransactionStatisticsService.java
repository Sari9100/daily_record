package com.familyos.ledger.service;

import com.familyos.common.context.AuthUser;
import com.familyos.common.context.FamilyContext;
import com.familyos.common.error.BusinessException;
import com.familyos.common.error.NotFoundException;
import com.familyos.ledger.dto.CategoryStat;
import com.familyos.ledger.dto.MonthStat;
import com.familyos.ledger.dto.StatTotals;
import com.familyos.ledger.dto.StatisticsResponse;
import com.familyos.ledger.mapper.TransactionStatisticsMapper;
import com.familyos.person.entity.Person;
import com.familyos.person.repository.PersonRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 가계부 통계(MyBatis). TRANSFER 제외, visibility·family 격리는 매퍼 WHERE 에서 처리.
 * 월 그룹은 뷰어 timezone 의 로컬 월 기준(UTC→offset 변환).
 */
@Service
@Transactional(readOnly = true)
public class TransactionStatisticsService {

    private final TransactionStatisticsMapper statisticsMapper;
    private final PersonRepository personRepository;

    public TransactionStatisticsService(TransactionStatisticsMapper statisticsMapper,
                                        PersonRepository personRepository) {
        this.statisticsMapper = statisticsMapper;
        this.personRepository = personRepository;
    }

    public StatisticsResponse statistics(@Nullable Instant from, @Nullable Instant to, @Nullable String scope) {
        AuthUser user = FamilyContext.require();
        String scopeVisibility = parseScope(scope);
        String zoneOffset = resolveZoneOffset(user.personId());

        StatTotals totals = statisticsMapper.selectTotals(
                user.familyId(), user.personId(), user.isParent(), from, to, scopeVisibility);
        List<CategoryStat> byCategory = statisticsMapper.selectByCategory(
                user.familyId(), user.personId(), user.isParent(), from, to, scopeVisibility);
        List<MonthStat> byMonth = statisticsMapper.selectByMonth(
                user.familyId(), user.personId(), user.isParent(), from, to, scopeVisibility, zoneOffset);

        return new StatisticsResponse(totals.totalIncome(), totals.totalExpense(), byCategory, byMonth);
    }

    /** scope(보기 필터) → visibility 문자열(매퍼용). ALL/null → null. */
    private @Nullable String parseScope(@Nullable String scope) {
        if (scope == null || scope.isBlank() || scope.equalsIgnoreCase("ALL")) {
            return null;
        }
        return switch (scope.toUpperCase()) {
            case "PRIVATE" -> "PRIVATE";
            case "PARENTS" -> "PARENTS";
            default -> throw new BusinessException("scope 는 ALL/PRIVATE/PARENTS 만 허용됩니다.");
        };
    }

    /** 뷰어 timezone 의 현재 offset("+09:00" 등). MySQL CONVERT_TZ 용 — UTC 는 "Z" 대신 "+00:00". */
    private String resolveZoneOffset(Long personId) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> NotFoundException.of("사용자", personId));
        ZoneId zone = ZoneId.of(person.getTimezone());
        ZoneOffset offset = zone.getRules().getOffset(Instant.now());
        String id = offset.getId();
        return "Z".equals(id) ? "+00:00" : id;
    }
}
