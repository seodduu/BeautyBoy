package com.beautyboy.support;

import jakarta.persistence.EntityManager;

/**
 * 상태 변경 검증용 영속성 헬퍼.
 *
 * <p>왜 필요한가: 테스트 클래스에 {@code @Transactional}을 걸면 영속성 컨텍스트(1차 캐시)가
 * 테스트 메서드 전체에서 유지된다. 그래서 서비스가 <b>DB에 쓰지 않았어도</b> 같은 트랜잭션 안에서
 * 재조회하면 캐시에 남은 엔티티가 그대로 나와 테스트가 통과한다.
 *
 * <p>Wave 0에서 프로필 갱신이 항상 500으로 죽는 결함을 유닛테스트가 전부 통과하며 놓쳤다(curl이 발견).
 * 원인이 정확히 이것이다. Wave 2의 재고 차감·결제 금액 검증은 같은 실수가 곧 돈 사고가 되는 영역이다.
 *
 * <p><b>규약: 상태 변경을 검증할 때는 재조회 전에 반드시 {@link #DB_왕복_강제(EntityManager)}를 호출한다.</b>
 *
 * <pre>{@code
 * memberService.프로필_갱신(memberId, 요청);
 *
 * DB_왕복_강제(entityManager);          // flush로 SQL을 실제로 날리고, clear로 1차 캐시를 비운다
 *
 * Member 다시_읽은_회원 = memberRepository.findById(memberId).orElseThrow();
 * assertThat(다시_읽은_회원.getNickname()).isEqualTo("바뀐닉네임");
 * }</pre>
 *
 * <p>조회 전용 테스트에는 쓰지 않는다 — 검증할 상태 변경이 없으면 왕복시킬 이유도 없다.
 */
public final class TestPersistence {

    private TestPersistence() {
    }

    /**
     * 쌓인 변경을 DB로 밀어내고(flush) 1차 캐시를 비운다(clear).
     *
     * <p>flush만 하면 캐시가 그대로라 여전히 캐시된 인스턴스가 조회된다 — 둘을 반드시 함께 한다.
     * clear 이후 기존 엔티티 참조는 준영속 상태가 되므로, 검증은 <b>새로 조회한 인스턴스</b>로 한다.
     */
    public static void DB_왕복_강제(EntityManager entityManager) {
        entityManager.flush();
        entityManager.clear();
    }
}
