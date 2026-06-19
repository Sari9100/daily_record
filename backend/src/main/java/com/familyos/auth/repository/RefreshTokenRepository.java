package com.familyos.auth.repository;

import com.familyos.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByJti(String jti);

    /**
     * 재사용 감지(탈취 의심) 시 해당 계정의 모든 유효 토큰을 폐기.
     * 호출 메서드는 이 갱신이 롤백되지 않도록 처리해야 한다(예: noRollbackFor).
     */
    @Modifying
    @Query("update RefreshToken r set r.revoked = true where r.accountId = :accountId and r.revoked = false")
    int revokeAllByAccountId(@Param("accountId") Long accountId);
}
