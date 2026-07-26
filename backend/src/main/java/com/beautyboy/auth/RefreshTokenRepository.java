package com.beautyboy.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 리프레시 토큰 한 행을 삭제하고 <b>반환값으로 지워진 행의 개수</b>(0 또는 1)를 돌려준다.
     * 지워진 엔티티를 돌려주는 것이 아니다.
     * 동시 refresh의 승자/패자는 오직 이 반환값으로 갈린다 — 1이면 내가 소유권을 얻었고,
     * 0이면 다른 요청이 먼저 가져갔다(Task 4-16a).
     *
     * <p>{@code JpaRepository.delete(entity)}를 쓰지 않는 이유:
     * 그쪽은 영속성 컨텍스트에 삭제를 등록만 하고 실제 DML을 커밋 시점까지 미룬다. 그래서 두
     * 트랜잭션이 같은 행을 지우면 늦은 쪽이 커밋하다 {@code StaleObjectStateException}으로 죽고,
     * 그것이 500으로 새어 나갔다. 반면 {@code @Modifying} 벌크 삭제는 <b>호출 즉시</b> DML을 보내므로
     * 그 자리에서 행 잠금을 잡고, 앞선 트랜잭션이 커밋되면 영향 행 수 0을 받는다 —
     * 예외 타입 해석에 기대지 않는 결정적 판정이다(엔진마다 예외가 달라지는 위험을 없앤다).
     */
    @Modifying
    @Query("delete from RefreshToken t where t.id = :id")
    int deleteRowById(@Param("id") Long id);
}
