package com.familyos.integration.telegram.repository;

import com.familyos.integration.telegram.entity.TelegramPersonMap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * @SoftDelete 가 deleted_at IS NULL 을 자동 적용하므로 아래 조회는 alive 매핑만 대상.
 * telegram_user_id 는 alive 유니크라 단건으로 person/family 를 결정할 수 있다.
 */
public interface TelegramPersonMapRepository extends JpaRepository<TelegramPersonMap, Long> {

    Optional<TelegramPersonMap> findByTelegramUserId(Long telegramUserId);

    boolean existsByTelegramUserId(Long telegramUserId);

    Optional<TelegramPersonMap> findByFamilyIdAndPersonId(Long familyId, Long personId);
}
