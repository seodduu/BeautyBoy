package com.beautyboy.member;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByMemberId(Long memberId);

    // DB에 (member_id, is_default) 유니크 제약이 없어 기본배송지가 2건이 될 수 있다.
    // Optional 반환 finder는 그 경우 NonUniqueResultException(500)을 던지므로, 최신 1건만
    // 확정적으로 집어 방어한다(구조적 유니크 제약은 Wave 4 보정 대역에서).
    Optional<Address> findFirstByMemberIdAndIsDefaultTrueOrderByIdDesc(Long memberId);
}
