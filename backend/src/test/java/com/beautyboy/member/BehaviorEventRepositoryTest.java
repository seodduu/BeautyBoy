package com.beautyboy.member;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BehaviorEventRepositoryTest {

    @Autowired
    BehaviorEventRepository behaviorEventRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void 저장하고_읽으면_네_칸이_그대로다() {
        LocalDateTime 시각 = LocalDateTime.of(2026, 9, 4, 14, 22);
        BehaviorEvent 저장됨 = behaviorEventRepository.saveAndFlush(
                new BehaviorEvent(7L, 217L, BehaviorKind.CART, 시각));

        Optional<BehaviorEvent> 조회 = behaviorEventRepository.findById(저장됨.getId());

        assertThat(조회).isPresent();
        assertThat(조회.get().getMemberId()).isEqualTo(7L);
        assertThat(조회.get().getGoodsId()).isEqualTo(217L);
        assertThat(조회.get().getKind()).isEqualTo(BehaviorKind.CART);
        assertThat(조회.get().getCreatedAt()).isEqualTo(시각);
    }

    @Test
    void kind는_문자열로_저장된다() {
        // ORDINAL이면 "2"가 나온다. 나중에 enum 상수 순서가 바뀌면 과거 데이터가 통째로
        // 다른 종류로 뒤집히므로 저장 형태를 여기서 못 박는다.
        BehaviorEvent 저장됨 = behaviorEventRepository.saveAndFlush(
                new BehaviorEvent(7L, 217L, BehaviorKind.CART, LocalDateTime.of(2026, 9, 4, 14, 22)));

        String kind = jdbcTemplate.queryForObject(
                "SELECT kind FROM behavior_event WHERE id = ?", String.class, 저장됨.getId());

        assertThat(kind).isEqualTo("CART");
    }

    @Test
    void 회원별_건수를_센다() {
        LocalDateTime 시각 = LocalDateTime.of(2026, 9, 4, 14, 22);
        behaviorEventRepository.save(new BehaviorEvent(7L, 217L, BehaviorKind.VIEW, 시각));
        behaviorEventRepository.save(new BehaviorEvent(7L, 218L, BehaviorKind.WISH, 시각));
        behaviorEventRepository.saveAndFlush(new BehaviorEvent(8L, 219L, BehaviorKind.CART, 시각));

        assertThat(behaviorEventRepository.countByMemberId(7L)).isEqualTo(2);
        assertThat(behaviorEventRepository.countByMemberId(9L)).isZero();
    }
}
