package com.beautyboy.member;

import com.beautyboy.member.dto.AddressRequest;
import com.beautyboy.support.TestPersistence;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V63(default_member_id 생성 컬럼 + UNIQUE)이 지키려는 불변식 — "기본배송지는 회원당 1개" —
 * 의 회귀 방어. 실제 DB 제약은 H2 create-drop 유닛 테스트에서는 걸리지 않으므로(엔티티에 매핑하지
 * 않은 생성 컬럼), 이 테스트는 서비스 로직(unmarkExistingDefault)이 여전히 불변식을 지키는지만 본다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AddressServiceTest {

    @Autowired
    AddressService addressService;
    @Autowired
    AddressRepository addressRepository;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    EntityManager em;

    @Test
    void 새_기본배송지를_등록하면_기존_기본이_해제된다() {
        Long 회원1 = 회원_생성();

        var 첫번째 = addressService.createAddress(회원1, 기본배송지_요청());
        var 두번째 = addressService.createAddress(회원1, 기본배송지_요청());

        TestPersistence.DB_왕복_강제(em);   // 1차 캐시가 UPDATE 반영을 가린다

        assertThat(addressRepository.findById(첫번째.id())).get()
                .satisfies(a -> assertThat(a.isDefault()).isFalse());
        assertThat(addressRepository.findById(두번째.id())).get()
                .satisfies(a -> assertThat(a.isDefault()).isTrue());
    }

    private Long 회원_생성() {
        Member member = new Member("addr-test@beautyboy.com", "hashed-pw", "배송지테스트");
        return memberRepository.save(member).getId();
    }

    private AddressRequest 기본배송지_요청() {
        return new AddressRequest("받는이", "01000000000", "12345", "서울시", "101동",
                new BigDecimal("37.5"), new BigDecimal("127.0"), true);
    }
}
