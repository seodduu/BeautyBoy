package com.beautyboy.search;

import com.beautyboy.search.dto.SearchCondition;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * MySQL FULLTEXT(ngram) 기반 검색 구현 — 운영 경로.
 *
 * <p>JPQL에는 {@code MATCH ... AGAINST}가 없으므로 네이티브 쿼리를 쓴다.
 * 그래서 이 클래스만 컬럼명(snake_case)을 직접 안다 — 스키마 변경 시 여기가 같이 바뀐다.
 *
 * <p>브랜드명이 OR LIKE인 이유: FULLTEXT 인덱스는 한 테이블 안에서만 걸린다.
 * goods와 brand에 각각 인덱스를 두고 UNION하는 방법도 있지만, 브랜드가 수십 개 규모라
 * 조인 후 LIKE가 더 단순하고 충분히 빠르다.
 *
 * <p>BOOLEAN MODE를 쓰는 이유: 자연어 모드는 전체 행의 50% 이상에 나타나는 토큰을 통째로 버린다.
 * 상품 150개 규모에서 "토너"처럼 흔한 단어가 그 문턱을 넘기 쉬워, 검색이 조용히 0건을 내는 사고가 난다.
 */
@Repository
@Profile("mysql-search")
public class MysqlFulltextGoodsSearchRepository implements GoodsSearchRepository {

    private static final String HIDDEN = "HIDDEN";

    private static final String FROM_WHERE = """
             from goods g join brand b on g.brand_id = b.id
             where g.status <> :hidden
               and (match(g.name) against (:booleanQuery in boolean mode) or b.name like :likePattern)
            """;

    private final EntityManager em;

    public MysqlFulltextGoodsSearchRepository(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<SearchRow> search(SearchCondition condition) {
        String sql = "select g.id, b.name, g.name, g.thumbnail_url, g.list_price, g.sale_price"
                + FROM_WHERE
                + " order by " + orderBy(condition.sort())
                + " limit :size offset :offset";

        Query query = em.createNativeQuery(sql);
        bind(query, condition);
        query.setParameter("size", condition.size());
        query.setParameter("offset", condition.page() * condition.size());

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        return rows.stream()
                .map(row -> new SearchRow(
                        ((Number) row[0]).longValue(),
                        (String) row[1],
                        (String) row[2],
                        (String) row[3],
                        ((Number) row[4]).intValue(),
                        ((Number) row[5]).intValue()))
                .toList();
    }

    @Override
    public long count(SearchCondition condition) {
        Query query = em.createNativeQuery("select count(*)" + FROM_WHERE);
        bind(query, condition);
        return ((Number) query.getSingleResult()).longValue();
    }

    @Override
    public List<String> autocomplete(String prefix, int limit) {
        // 자동완성은 관련도가 아니라 "빨리 뜨는 접두사 후보"라 prefix LIKE가 맞다(설계 7장).
        // 뒤쪽 와일드카드만 있으므로 name 인덱스를 탄다.
        Query query = em.createNativeQuery(
                "select distinct g.name from goods g "
                        + "where g.status <> :hidden and g.name like :prefix "
                        + "order by g.name asc limit :limit");
        query.setParameter("hidden", HIDDEN);
        query.setParameter("prefix", prefix + "%");
        query.setParameter("limit", limit);

        @SuppressWarnings("unchecked")
        List<String> names = query.getResultList();
        return names;
    }

    private void bind(Query query, SearchCondition condition) {
        query.setParameter("hidden", HIDDEN);
        query.setParameter("booleanQuery", toBooleanQuery(condition.keyword()));
        query.setParameter("likePattern", "%" + condition.keyword() + "%");
    }

    /**
     * 사용자 입력을 BOOLEAN MODE 질의로 바꾼다.
     *
     * <p>연산자 문자(+ - &gt; &lt; ( ) ~ * " @)를 제거하는 이유가 둘이다.
     * (1) 사용자가 무심코 넣은 하이픈이 "제외" 연산자로 해석돼 결과가 사라지는 것을 막는다.
     * (2) 질의 문법 오류로 500이 나가는 것을 막는다.
     * 그 다음 각 토큰에 {@code +}를 붙여 전부 포함(AND)으로 만든다 — OR면 한 글자만 겹쳐도 걸려 노이즈가 커진다.
     */
    private String toBooleanQuery(String keyword) {
        String sanitized = keyword.replaceAll("[+\\-><()~*\"@]", " ").trim();
        String[] tokens = sanitized.split("\\s+");

        StringBuilder builder = new StringBuilder();
        for (String token : tokens) {
            if (!token.isBlank()) {
                builder.append('+').append(token).append(' ');
            }
        }
        return builder.toString().trim();
    }

    private String orderBy(SearchSort sort) {
        return switch (sort) {
            // MATCH를 select 절에 또 쓰지 않고 order by에서 직접 점수를 쓴다 —
            // MySQL이 같은 MATCH 표현식을 재사용하므로 추가 비용이 없다.
            case ACCURACY -> "match(g.name) against (:booleanQuery in boolean mode) desc, g.id desc";
            case POPULAR -> "g.view_count desc, g.id desc";
            case NEW -> "g.created_at desc, g.id desc";
            case PRICE_ASC -> "g.sale_price asc, g.id desc";
        };
    }
}
