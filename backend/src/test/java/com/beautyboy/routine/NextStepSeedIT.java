package com.beautyboy.routine;

import com.beautyboy.catalog.CategoryRepository;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.catalog.TagRepository;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    @Autowired
    RoutineFlowRuleRepository routineFlowRuleRepository;
    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    TagRepository tagRepository;

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
        // goods 2(AHA 딥모이스처 토너) → BUFFER 블록(진정 세럼).
        //
        // 이 테스트가 보려는 것은 "궁합 게이트가 CONFLICT 후보를 걷어냈는가"다. 어떤 상품이
        // 카드로 뽑히는지는 태그 파생 규칙이 바뀔 때마다 달라지므로 특정 id 포함을 단언하지
        // 않는다 — V83(태그 희석화 해소) 전에는 goods 4를 단언했는데, goods 4의 soothe는
        // 미량 병풀(is_key=0)에서 온 것이라 V83이 걷어냈다. 그 상품의 소구 성분은
        // 나이아신아마이드(피지·모공)이므로 진정 세럼이 아닌 것이 맞다.
        //
        // 대신 "AHA와 CONFLICT인 후보가 빠졌다"를 직접 단언한다:
        //   goods 159(RETINOID 함유)·190(BHA 함유) — 둘 다 soothe 태그를 갖고 있어 후보에는
        //   오르지만 AHA와 CONFLICT라 게이트에서 제거돼야 한다.
        //   goods 5(RETINOID)는 soothe 태그가 없어 애초에 후보에도 오르지 않는다.
        NextStepResponse res = nextStepService.find(2L, null);
        assertThat(res.blocks()).isNotEmpty();
        assertThat(res.blocks().get(0).edgeKind()).isEqualTo("BUFFER");
        assertThat(res.blocks().get(0).items())
                .as("게이트를 통과한 후보가 하나도 없으면 이 테스트는 아무것도 증명하지 못한다")
                .isNotEmpty();
        assertThat(res.blocks().get(0).items()).extracting(GoodsListItem::goodsNo)
                .doesNotContain(159L, 190L, 5L);

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

    /**
     * tag slug·category code에 물리 FK를 걸지 않는 설계(§3)의 대가 — 오타는 이 테스트가 잡는다.
     * {@code routine_flow_rule}은 from/to_category_code·tag_slug를 문자열로만 참조하므로 DB 제약이
     * 오타·삭제된 참조를 막아주지 않는다. 전 행을 순회해 category·tag 실재 여부와 edge_kind 값,
     * 그리고 같은 from_category_code에서 순방향(NEXT_STEP·BUFFER)과 PAIRED_REMOVAL이 같은
     * to_category_code를 가리키지 않는지(블록 중복 게이트, §4)를 단언한다.
     */
    @Test
    void 시드_전_행이_category_tag_참조를_지키고_블록이_중복되지_않는다() {
        List<RoutineFlowRule> rules = routineFlowRuleRepository.findAll();
        assertThat(rules).isNotEmpty();

        Set<String> categoryCodes = categoryRepository.findAll().stream()
                .map(com.beautyboy.catalog.Category::getCode)
                .collect(Collectors.toSet());
        Set<String> tagSlugs = tagRepository.findAll().stream()
                .map(com.beautyboy.catalog.Tag::getSlug)
                .collect(Collectors.toSet());
        Set<String> validEdgeKinds = Set.of("NEXT_STEP", "PAIRED_REMOVAL", "BUFFER");

        for (RoutineFlowRule rule : rules) {
            assertThat(rule.getFromCategoryCode())
                    .as("rule#%d from_category_code는 중분류(7자)여야 한다", rule.getId())
                    .hasSize(7);
            assertThat(categoryCodes)
                    .as("rule#%d from_category_code=%s가 category 테이블에 없다", rule.getId(), rule.getFromCategoryCode())
                    .contains(rule.getFromCategoryCode());

            assertThat(rule.getToCategoryCode())
                    .as("rule#%d to_category_code는 중분류(7자)여야 한다", rule.getId())
                    .hasSize(7);
            assertThat(categoryCodes)
                    .as("rule#%d to_category_code=%s가 category 테이블에 없다", rule.getId(), rule.getToCategoryCode())
                    .contains(rule.getToCategoryCode());

            if (rule.getFromTagSlug() != null) {
                assertThat(tagSlugs)
                        .as("rule#%d from_tag_slug=%s가 tag.slug에 없다", rule.getId(), rule.getFromTagSlug())
                        .contains(rule.getFromTagSlug());
            }
            if (rule.getToTagSlug() != null) {
                assertThat(tagSlugs)
                        .as("rule#%d to_tag_slug=%s가 tag.slug에 없다", rule.getId(), rule.getToTagSlug())
                        .contains(rule.getToTagSlug());
            }

            assertThat(validEdgeKinds)
                    .as("rule#%d edge_kind=%s가 허용 값 밖이다", rule.getId(), rule.getEdgeKind())
                    .contains(rule.getEdgeKind());
        }

        // 블록 중복 게이트: 같은 from_category_code에서 순방향(NEXT_STEP·BUFFER) 규칙과
        // PAIRED_REMOVAL 규칙이 같은 to_category_code를 가리키면, §4가 한 화면에 뽑는 두 블록의
        // 카드 후보가 서로 겹쳐 "다른 단계로 넘겨주는 두 갈래"라는 전제가 무너진다.
        Map<String, Set<String>> forwardTargetsByFrom = new HashMap<>();
        Map<String, Set<String>> removalTargetsByFrom = new HashMap<>();
        for (RoutineFlowRule rule : rules) {
            Map<String, Set<String>> bucket = "PAIRED_REMOVAL".equals(rule.getEdgeKind())
                    ? removalTargetsByFrom
                    : forwardTargetsByFrom;
            bucket.computeIfAbsent(rule.getFromCategoryCode(), k -> new HashSet<>())
                    .add(rule.getToCategoryCode());
        }
        for (Map.Entry<String, Set<String>> entry : forwardTargetsByFrom.entrySet()) {
            Set<String> removalTargets = removalTargetsByFrom.get(entry.getKey());
            if (removalTargets == null) {
                continue;
            }
            Set<String> overlap = new HashSet<>(entry.getValue());
            overlap.retainAll(removalTargets);
            assertThat(overlap)
                    .as("from_category_code=%s의 순방향/PAIRED_REMOVAL이 같은 to_category_code를 가리키면 안 된다",
                            entry.getKey())
                    .isEmpty();
        }
    }
}
