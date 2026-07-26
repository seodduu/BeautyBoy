package com.beautyboy.routine;

import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.catalog.dto.GoodsListItem;
import com.beautyboy.compat.CompatQueryService;
import com.beautyboy.routine.dto.NextStepBlock;
import com.beautyboy.routine.dto.NextStepResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V75 시드 검증 — 실 MySQL + Flyway 전체 로드(V1~V75). 설계 §9의 DoD 두 축을 확인한다:
 * (1) 전 상품을 순회해도 궁합 게이트를 뚫고 CONFLICT 후보가 노출되는 경우가 한 건도 없는지,
 * (2) 규칙이 실제로 얼마나 많은 상품을 커버하는지(정확도 주장은 하지 않는다).
 *
 * <p>{@code NextStepServiceTest}(단위)는 픽스처를 자가 주입해 서비스 로직만 검증하지만, 이 클래스는
 * V75가 심은 실제 12행 규칙과 V65 벌크 상품(150개)까지 포함한 실 데이터를 대상으로 돌린다 — 시드
 * 자체가 규칙 취지대로 동작하는지는 실데이터가 아니면 확인할 수 없다.
 *
 * <p>컨테이너 구성은 {@code MysqlFulltextSearchIntegrationTest}와 같은 패턴이다
 * (mysql:8.4 + Flyway 실제 적용 + {@code ddl-auto=validate}). {@code @ActiveProfiles}는 "test"만 쓴다
 * — {@code mysql-search}는 검색 전용 프로필이라 이 테스트와 무관하다.
 *
 * <p>실행: {@code ./gradlew integrationTest}
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class NextStepSeedIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    @DynamicPropertySource
    static void 실_MySQL로_바꿔_끼운다(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    NextStepService nextStepService;
    @Autowired
    CompatQueryService compatQueryService;
    @Autowired
    GoodsRepository goodsRepository;

    private List<Long> 비HIDDEN_전상품_goodsNo() {
        return goodsRepository.findAll().stream()
                .filter(g -> !"HIDDEN".equals(g.getStatus()))
                .map(com.beautyboy.catalog.Goods::getId)
                .toList();
    }

    @Test
    void 전_상품에서_궁합_CONFLICT_후보가_한_건도_없다() {
        for (Long goodsNo : 비HIDDEN_전상품_goodsNo()) {
            NextStepResponse res = nextStepService.find(goodsNo, null);
            for (NextStepBlock block : res.blocks()) {
                List<Long> itemNos = block.items().stream().map(GoodsListItem::goodsNo).toList();
                Map<Long, String> verdicts = compatQueryService.worstVerdicts(goodsNo, itemNos);
                assertThat(verdicts.values()).noneMatch("CONFLICT"::equals);
            }
        }
    }

    @Test
    void 대표_데모_케이스가_시드에서_성립한다() {
        // goods 2(AHA 딥모이스처 토너): V72 태그 확장 이후 soothe 세럼이 이미 4개 이상이라
        // 태그 매칭만으로 폴백 없이 블록이 채워진다. 후보 중 RETINOID/BHA 함유 세럼은
        // AHA와 CONFLICT라 게이트에서 빠지고, goods 5(RETINOID, soothe 태그 없음)는 애초에
        // 태그 매칭 후보에도 오르지 않는다 — 둘 다 "게이트가 CONFLICT 후보를 제거했다"는
        // 결론은 같으므로 goods 4 포함 여부와 goods 5 부재만 실데이터에 강하게 단언한다.
        NextStepResponse res = nextStepService.find(2L, null);
        assertThat(res.blocks()).isNotEmpty();
        assertThat(res.blocks().get(0).edgeKind()).isEqualTo("BUFFER");
        assertThat(res.blocks().get(0).items()).extracting(GoodsListItem::goodsNo)
                .contains(4L)
                .doesNotContain(5L);

        // goods 21(무기자차 선크림): 순방향(애프터선 soothe) + PAIRED_REMOVAL(클렌징오일/밤) 2블록.
        // 어느 상품이 각 블록의 카드로 뽑히는지는 V65 벌크 인기 순위에 따라 바뀔 수 있으므로
        // 블록 구조(edgeKind 순서)만 데이터 변동에 강하게 단언한다.
        NextStepResponse sun = nextStepService.find(21L, null);
        assertThat(sun.blocks()).extracting(NextStepBlock::edgeKind)
                .containsExactly("NEXT_STEP", "PAIRED_REMOVAL");
    }

    @Test
    void 규칙_커버리지를_출력한다_정확도는_주장하지_않는다() {
        long total = 0;
        long covered = 0;
        for (Long goodsNo : 비HIDDEN_전상품_goodsNo()) {
            total++;
            if (!nextStepService.find(goodsNo, null).blocks().isEmpty()) {
                covered++;
            }
        }
        double coverage = (double) covered / total;
        System.out.printf("next-step 규칙 커버리지: %.1f%% (%d/%d)%n", coverage * 100, covered, total);
        if (coverage < 0.40) {
            System.out.println("WARN: 커버리지 40% 미만 — 규칙 추가 검토");
        }
        assertThat(covered).isGreaterThan(0);   // 유일한 하드 단언: 완전 공백이면 시드가 깨진 것
    }
}
