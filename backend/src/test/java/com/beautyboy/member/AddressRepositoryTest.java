package com.beautyboy.member;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AddressRepositoryTest {

    @Autowired
    AddressRepository addressRepository;

    private Address defaultAddress(Long memberId) {
        return new Address(memberId, "받는이", "01000000000", "12345",
                "서울시", "101동", new BigDecimal("37.5"), new BigDecimal("127.0"), true);
    }

    @Test
    void 기본배송지가_둘이어도_500이_아니라_최신_1건을_반환한다() {
        Long memberId = 42L;
        // DB 유니크 제약이 없어 실제로 기본배송지가 2건이 될 수 있는 상태를 재현한다.
        addressRepository.save(defaultAddress(memberId));
        Address newer = addressRepository.saveAndFlush(defaultAddress(memberId));

        assertThatCode(() -> {
            Optional<Address> found =
                    addressRepository.findFirstByMemberIdAndIsDefaultTrueOrderByIdDesc(memberId);
            // 예외 없이 최신(id 큰) 1건을 돌려줘야 한다.
            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(newer.getId());
        }).doesNotThrowAnyException();
    }
}
