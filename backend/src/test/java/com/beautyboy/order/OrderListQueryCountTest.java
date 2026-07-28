package com.beautyboy.order;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주문 목록의 N+1 회귀 방어. 주문 건수가 늘어도 쿼리 수가 일정해야 한다.
 * 응답만 단언하는 테스트는 N+1이 되살아나도 녹색이라 이 클래스가 유일한 증거다.
 * 통계를 켜야 해서 별도 프로퍼티 → 별도 컨텍스트다(그 비용을 알고 감수한다).
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@Transactional
class OrderListQueryCountTest {

    private static final Long 회원_1건 = 101L;
    private static final Long 회원_5건 = 102L;

    @Autowired
    OrderRepository orderRepository;
    @Autowired
    EntityManagerFactory entityManagerFactory;
    @Autowired
    EntityManager entityManager;

    @Test
    @DisplayName("주문 5건 목록과 1건 목록의 쿼리 수가 같다 — default_batch_fetch_size가 항목을 IN으로 모은다")
    void 건수가_늘어도_쿼리_수는_같다() {
        주문_저장(회원_1건, 1);
        주문_저장(회원_5건, 5);
        entityManager.flush();
        entityManager.clear();

        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();

        statistics.clear();
        조회하고_항목까지_읽는다(회원_1건);
        long n1 = statistics.getPrepareStatementCount();

        entityManager.clear();
        statistics.clear();
        조회하고_항목까지_읽는다(회원_5건);
        long n5 = statistics.getPrepareStatementCount();

        assertThat(n5).isEqualTo(n1);
    }

    private void 조회하고_항목까지_읽는다(Long memberId) {
        orderRepository.findByMemberIdOrderByOrderedAtDescIdDesc(memberId, PageRequest.of(0, 10))
                .getContent()
                .forEach(order -> order.getItems().size());
    }

    private void 주문_저장(Long memberId, int orderCount) {
        for (int i = 0; i < orderCount; i++) {
            Order order = new Order("ORD-" + System.nanoTime(), memberId, "홍길동", "010-0000-0000",
                    "06234", "서울시", "101호", "NORMAL", LocalDateTime.now());
            order.addItem(new OrderItem(1L, null, "상품" + i, null, 16000, 1));
            orderRepository.save(order);
        }
    }
}
