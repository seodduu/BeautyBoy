package com.beautyboy.routine;

import com.beautyboy.catalog.TagRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V79 시드 검증 — 실 MySQL + Flyway 전체 로드(V1~V79). H2 create-drop은 스키마 불일치를 가리고,
 * 무엇보다 시드 SQL 자체가 돌지 않아 "규칙이 실제 상품을 겨냥하는가"를 물을 수 없다.
 *
 * <p>이 클래스는 예외적으로 goods·goods_tag·tag를 직접 조회한다. routine 패키지는 자기 테이블만
 * 접근한다는 경계를 프로덕션 코드에서는 지키지만, 여기서 보려는 것이 정확히 "규칙이 가리키는
 * 상품 집합이 실재하는가"라는 교차 정합이라 테스트 코드에서만 경계를 넘는다.
 *
 * <p>컨테이너 구성은 {@link NextStepSeedIT}와 같은 패턴이다(mysql:8.4 + Flyway 실제 적용 +
 * {@code ddl-auto=validate}). 실행: {@code ./gradlew integrationTest}
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ConcernTargetRuleSeedIT {

    /** 규칙 한 행이 최소한 이만큼의 후보를 가져야 한다. 이보다 적으면 화면에서 폴백만 유발한다. */
    private static final int MIN_CANDIDATES = 4;

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
    ConcernTargetRuleRepository ruleRepository;
    @Autowired
    TagRepository tagRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    EntityManager entityManager;

    @Test
    void 모든_concern_target_rule_행에_후보가_4개_이상이다() {
        List<ConcernTargetRule> rules = ruleRepository.findAll();
        assertThat(rules).isNotEmpty();

        List<String> 부족한_행 = new ArrayList<>();
        for (ConcernTargetRule rule : rules) {
            int count = 후보_수(rule.getToCategoryCode(), rule.getToTagSlug());
            System.out.printf("concern=%s → %s/%s : %d개%n",
                    rule.getConcernTagSlug(), rule.getToCategoryCode(), rule.getToTagSlug(), count);
            if (count < MIN_CANDIDATES) {
                부족한_행.add("(%s, %s, %s) = %d개".formatted(
                        rule.getConcernTagSlug(), rule.getToCategoryCode(), rule.getToTagSlug(), count));
            }
        }

        assertThat(부족한_행)
                .as("후보 %d개 미만인 규칙은 화면에서 폴백만 유발한다 — 시드에서 빼야 한다", MIN_CANDIDATES)
                .isEmpty();
    }

    /** {@code goods.category_code}는 leaf 10자, 규칙은 중분류 7자라 접두사 매칭이다. */
    private int 후보_수(String toCategoryCode, String toTagSlug) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM goods g
                  JOIN goods_tag gt ON gt.goods_id = g.id
                  JOIN tag t        ON t.id = gt.tag_id
                 WHERE g.category_code LIKE CONCAT(?, '%')
                   AND t.slug = ?
                   AND g.status <> 'HIDDEN'
                """, Integer.class, toCategoryCode, toTagSlug);
        return count == null ? 0 : count;
    }

    /**
     * 물리 FK를 걸지 않은 대가 — 슬러그 오타나 삭제된 태그 참조를 DB가 막아주지 않으므로 여기서 잡는다.
     * concern_tag_slug는 프로필 고민 어휘, to_tag_slug는 상품 태그 어휘지만 둘 다 tag.slug와 같은 집합이다.
     */
    @Test
    void concern_tag_slug와_to_tag_slug가_전부_tag_테이블에_실재한다() {
        Set<String> tagSlugs = tagRepository.findAll().stream()
                .map(com.beautyboy.catalog.Tag::getSlug)
                .collect(Collectors.toSet());

        for (ConcernTargetRule rule : ruleRepository.findAll()) {
            assertThat(tagSlugs)
                    .as("rule#%d concern_tag_slug=%s가 tag.slug에 없다", rule.getId(), rule.getConcernTagSlug())
                    .contains(rule.getConcernTagSlug());
            assertThat(tagSlugs)
                    .as("rule#%d to_tag_slug=%s가 tag.slug에 없다", rule.getId(), rule.getToTagSlug())
                    .contains(rule.getToTagSlug());
        }
    }

    /**
     * 한 고민이 같은 단계를 두 번 겨냥하면 화면에 같은 카테고리 블록이 겹쳐 나온다(설계 §5.1).
     * DDL에 UNIQUE를 적어두는 것과 실제로 걸리는 것은 다르므로 중복 삽입이 실패하는지로 확인한다.
     */
    @Test
    void UNIQUE_concern_tag_slug_to_category_code_제약이_실제로_걸려_있다() {
        ConcernTargetRule 기존 = ruleRepository.findAll().get(0);

        assertThatThrownBy(() -> {
            ruleRepository.saveAndFlush(new ConcernTargetRule(
                    null, 기존.getConcernTagSlug(), 기존.getToCategoryCode(), 기존.getToTagSlug(),
                    "중복 삽입 시도", 99));
        }).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        entityManager.clear();
    }

    /**
     * to_category_code가 leaf 10자면 {@code LIKE 'C001001001%'}가 사실상 완전일치가 되어
     * "중분류 안의 어떤 상품이든"이라는 접두사 매칭의 취지가 무너진다.
     */
    @Test
    void to_category_code가_전부_7자_중분류다() {
        for (ConcernTargetRule rule : ruleRepository.findAll()) {
            assertThat(rule.getToCategoryCode())
                    .as("rule#%d to_category_code=%s는 중분류(7자)여야 한다", rule.getId(), rule.getToCategoryCode())
                    .hasSize(7);
        }
    }
}
