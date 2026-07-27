package com.beautyboy.catalog;

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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V83 — 태그 희석화 해소 검증.
 * 설계: docs/superpowers/specs/2026-07-27-tag-dilution-design.md
 *
 * <p>왜 실 MySQL인가: V83은 DDL이 아니라 DELETE 두 방이고, 순위 계산에 윈도우 함수를 쓴다.
 * H2 + {@code create-drop}에서는 아예 실행되지 않아(테스트 프로필은 Flyway off) 시드(V71·V72)와
 * 정리(V83)가 같은 순서로 얹힌 clean 로드에서만 결과를 말할 수 있다.
 *
 * <p>Docker가 필요하므로 {@code @Tag("integration")} — 실행: {@code ./gradlew integrationTest}
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PruneDilutedTagsIT {

    /** 부형제 6종 — 글리세린·판테놀·알란토인·토코페롤·아데노신·알파비사보롤(설계 §3.1). */
    private static final String EXCIPIENT_IDS = "25, 26, 27, 28, 29, 30";

    /** 상품당 태그 상한(설계 §3). */
    private static final int TAG_CAP = 3;

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
    JdbcTemplate jdbcTemplate;

    @Test
    void 부형제가_보조성분으로_만든_태그가_한_건도_남지_않는다() {
        Integer 남은_행 = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM goods_tag gt
                JOIN goods_ingredient gi
                  ON gi.goods_id = gt.goods_id AND gi.ingredient_id = gt.source_ingredient_id
                WHERE gt.source_ingredient_id IN (""" + EXCIPIENT_IDS + """
                ) AND gi.is_key = 0
                """, Integer.class);

        assertThat(남은_행).isZero();
    }

    @Test
    void 부형제라도_is_key면_태그가_남는다() {
        // (a)가 조건 없이 부형제를 다 지웠다면 이 값이 0이 된다 — "제조사가 소구점으로 내세운
        // 글리세린"까지 잃으면 보습 태그가 과하게 줄어든다.
        Integer 남은_행 = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM goods_tag gt
                JOIN goods_ingredient gi
                  ON gi.goods_id = gt.goods_id AND gi.ingredient_id = gt.source_ingredient_id
                WHERE gt.source_ingredient_id IN (""" + EXCIPIENT_IDS + """
                ) AND gi.is_key = 1
                """, Integer.class);

        assertThat(남은_행).isPositive();
    }

    @Test
    void 상품당_태그가_상한을_넘지_않는다() {
        Integer 최대_태그수 = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(c), 0) FROM (SELECT COUNT(*) c FROM goods_tag GROUP BY goods_id) z",
                Integer.class);

        assertThat(최대_태그수).isLessThanOrEqualTo(TAG_CAP);
    }

    @Test
    void 카테고리_기본_태그는_지워지지_않는다() {
        // "클렌징폼이 세정"·"선크림이 자외선차단"은 성분과 무관하게 참이라, 상한에 밀려나면
        // 그 카테고리 전체가 자기 정체성 태그를 잃는다. V83의 COALESCE(is_key, 1)이 이걸 막는다.
        Integer 클렌징_cleanse = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM goods g
                JOIN goods_tag gt ON gt.goods_id = g.id
                JOIN tag t ON t.id = gt.tag_id
                WHERE g.category_code LIKE 'C002%' AND t.slug = 'cleanse'
                """, Integer.class);

        Integer 선케어_uv = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM goods g
                JOIN goods_tag gt ON gt.goods_id = g.id
                JOIN tag t ON t.id = gt.tag_id
                WHERE g.category_code LIKE 'C004%' AND t.slug = 'uv'
                """, Integer.class);

        assertThat(클렌징_cleanse).isPositive();
        assertThat(선케어_uv).isPositive();
    }

    @Test
    void V71_수동_보정이_유지된다() {
        // V71이 상품명과의 모순 때문에 일부러 지운 태그다. V83이 재파생이 아니라 정리(prune)인
        // 이유가 이것 — 재파생하면 이 보정이 되살아난다.
        Integer goods1 = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM goods_tag gt JOIN tag t ON t.id = gt.tag_id
                WHERE gt.goods_id = 1 AND t.slug IN ('exfoliate', 'anti-aging')
                """, Integer.class);

        Integer goods5 = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM goods_tag gt JOIN tag t ON t.id = gt.tag_id
                WHERE gt.goods_id = 5 AND t.slug = 'anti-aging'
                """, Integer.class);

        assertThat(goods1).isZero();
        assertThat(goods5).isZero();
    }

    @Test
    void 어떤_태그도_카탈로그에서_통째로_사라지지_않는다() {
        // 상한을 전역 고정 순서로 자르면 순서가 뒤인 태그가 모든 상품에서 동시에 잘려나간다 —
        // 실제로 sort_order 기준일 때 trouble이 0개가 되고 concern 규칙 2행이 죽었다.
        // 희소 우선 정렬이 이걸 막는다. EFFECT/PROPERTY 태그는 저마다 최소 한 상품은 붙들어야
        // 그 태그를 겨냥하는 규칙이 살아 있다.
        List<String> 사라진_태그 = jdbcTemplate.queryForList("""
                SELECT t.slug FROM tag t
                WHERE t.kind IN ('EFFECT', 'PROPERTY')
                  AND NOT EXISTS (SELECT 1 FROM goods_tag gt WHERE gt.tag_id = t.id)
                ORDER BY t.slug
                """, String.class);

        assertThat(사라진_태그).isEmpty();
    }

    @Test
    void 희석도가_목표_구간에_든다_정확값은_출력한다() {
        // 정확값을 단언하지 않는 이유: 시드(V12·V65)가 바뀌면 같이 바뀌는 수치다. 상한만 걸고
        // 실측값을 출력해, 나중에 시드를 손볼 때 무엇이 얼마나 움직였는지 보이게 한다.
        Double 평균_태그수 = jdbcTemplate.queryForObject(
                "SELECT AVG(c) FROM (SELECT COUNT(*) c FROM goods_tag GROUP BY goods_id) z",
                Double.class);

        Integer moisture_보유 = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT gt.goods_id) FROM goods_tag gt
                JOIN tag t ON t.id = gt.tag_id WHERE t.slug = 'moisture'
                """, Integer.class);

        Integer 전체_상품 = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM goods", Integer.class);

        Integer 태그_없는_상품 = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM goods g
                WHERE NOT EXISTS (SELECT 1 FROM goods_tag gt WHERE gt.goods_id = g.id)
                """, Integer.class);

        System.out.printf("[V83] 평균 태그 %.2f개 / moisture %d개(전체 %d) / 태그 없는 상품 %d개%n",
                평균_태그수, moisture_보유, 전체_상품, 태그_없는_상품);

        assertThat(평균_태그수).isLessThanOrEqualTo(3.2);
        assertThat(moisture_보유).isLessThanOrEqualTo(60);
    }

    @Test
    void 정리_결과가_결정적이다() {
        // ROW_NUMBER의 ORDER BY에 tag_id까지 넣지 않으면 동점에서 삭제 대상이 옵티마이저 재량이
        // 된다. 같은 순위 계산을 다시 돌려 "상한을 넘는 행"이 하나도 안 나오면, 남은 집합이
        // 그 정렬 기준의 상위 N과 정확히 일치한다는 뜻이다.
        Integer 상한_초과 = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM (
                  SELECT ROW_NUMBER() OVER (
                           PARTITION BY x.goods_id
                           ORDER BY COALESCE(x.is_key, 1) DESC,
                                    f.freq ASC,
                                    COALESCE(x.ing_sort, 0) ASC,
                                    x.tag_id ASC
                         ) AS rn
                  FROM (
                    SELECT gt.goods_id, gt.tag_id, gi.is_key, gi.sort_order AS ing_sort
                    FROM goods_tag gt
                    LEFT JOIN goods_ingredient gi
                      ON gi.goods_id = gt.goods_id AND gi.ingredient_id = gt.source_ingredient_id
                  ) x
                  JOIN (SELECT tag_id, COUNT(*) AS freq FROM goods_tag GROUP BY tag_id) f
                    ON f.tag_id = x.tag_id
                ) r WHERE r.rn > """ + TAG_CAP, Integer.class);

        assertThat(상한_초과).isZero();
    }
}
