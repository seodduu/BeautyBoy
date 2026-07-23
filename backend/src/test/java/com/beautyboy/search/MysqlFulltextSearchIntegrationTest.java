package com.beautyboy.search;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.search.dto.SearchCondition;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FULLTEXT 구현의 유일한 검증 지점.
 *
 * <p>{@code SearchApiTest}는 H2라서 LIKE 구현만 돈다 — 그 테스트가 녹색이어도
 * {@code MATCH ... AGAINST} 문법 오류나 ngram 인덱스 누락은 전혀 드러나지 않는다.
 * 실 MySQL이 아니면 검증 자체가 불가능하므로 {@code @Tag("integration")}으로 분리한다.
 *
 * <p>실행: {@code ./gradlew integrationTest}
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles({"test", "mysql-search"})
@Testcontainers
class MysqlFulltextSearchIntegrationTest {

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
    GoodsSearchRepository goodsSearchRepository;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;

    @Test
    void 주입된_구현이_FULLTEXT_구현이다() {
        // 프로필이 어긋나 LIKE 구현이 주입되면 아래 테스트들이 통과해도 아무 의미가 없다.
        assertThat(goodsSearchRepository).isInstanceOf(MysqlFulltextGoodsSearchRepository.class);
    }

    @Test
    void 한글_부분어절로_상품명이_매칭된다() {
        Brand brand = brandRepository.save(new Brand("테스트브랜드", null));
        goodsRepository.save(new Goods(brand, "C001001001", "그린티 수분 토너", null, "https://img/1.jpg", 20000, 16000));
        goodsRepository.flush();

        // ngram 파서라 "수분"처럼 어절 일부로도 걸린다. 공백 토큰 방식이면 여기서 0건이 나온다.
        List<GoodsSearchRepository.SearchRow> rows =
                goodsSearchRepository.search(new SearchCondition("수분", SearchSort.ACCURACY, 0, 20));

        assertThat(rows).extracting(GoodsSearchRepository.SearchRow::name).contains("그린티 수분 토너");
    }

    @Test
    void 브랜드명은_FULLTEXT가_아니라_조인_LIKE로_걸린다() {
        // FULLTEXT는 한 테이블 안에서만 걸린다. 브랜드명 매칭이 죽지 않았는지 확인한다.
        Brand brand = brandRepository.save(new Brand("닥터지", null));
        goodsRepository.save(new Goods(brand, "C001003001", "레드블레미쉬 크림", null, "https://img/2.jpg", 30000, 24000));
        goodsRepository.flush();

        List<GoodsSearchRepository.SearchRow> rows =
                goodsSearchRepository.search(new SearchCondition("닥터지", SearchSort.ACCURACY, 0, 20));

        assertThat(rows).extracting(GoodsSearchRepository.SearchRow::brandName).contains("닥터지");
    }

    @Test
    void count가_search와_같은_조건을_센다() {
        Brand brand = brandRepository.save(new Brand("브랜드", null));
        goodsRepository.save(new Goods(brand, "C001001001", "수분 토너 하나", null, "https://img/3.jpg", 10000, 10000));
        goodsRepository.save(new Goods(brand, "C001001001", "수분 토너 둘", null, "https://img/4.jpg", 10000, 10000));
        goodsRepository.flush();

        SearchCondition condition = new SearchCondition("수분 토너", SearchSort.ACCURACY, 0, 20);

        // 조건이 어긋나면 "총 5개"인데 2개만 나오는 페이징 버그가 된다.
        assertThat(goodsSearchRepository.count(condition))
                .isEqualTo(goodsSearchRepository.search(condition).size());
    }

    @Test
    void 자동완성은_접두사로_매칭된다() {
        // 이 클래스는 테스트 메서드 간 트랜잭션 롤백 없이 같은 MySQL 컨테이너를 공유한다.
        // count 테스트와 브랜드명이 겹치면 brand.name unique 제약 위반이 나므로 이름을 분리한다.
        Brand brand = brandRepository.save(new Brand("브랜드자동완성", null));
        goodsRepository.save(new Goods(brand, "C001001001", "수분폭탄 토너", null, "https://img/5.jpg", 10000, 10000));
        goodsRepository.flush();

        assertThat(goodsSearchRepository.autocomplete("수분", 10)).contains("수분폭탄 토너");
    }
}
