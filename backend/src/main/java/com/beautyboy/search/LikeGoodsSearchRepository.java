package com.beautyboy.search;

import com.beautyboy.search.dto.SearchCondition;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * LIKE 기반 검색 구현.
 *
 * <p>테스트 프로필(H2)의 기본 구현이다. H2에는 MySQL FULLTEXT가 없어
 * {@code MATCH ... AGAINST}를 실행할 수 없기 때문이다.
 *
 * <p>운영에서 이 구현을 쓰지 않는 이유: {@code like '%키워드%'}는 선행 와일드카드라
 * 인덱스를 타지 못해 항상 풀스캔이다. 상품 150개인 MVP에서는 문제가 없지만,
 * 그 사실이 "괜찮은 설계"라는 뜻은 아니라서 운영 경로는 FULLTEXT로 간다.
 *
 * <p>ACCURACY(관련도)는 LIKE로는 점수를 낼 수 없어 "상품명 일치를 브랜드명 일치보다 앞"으로만 근사한다.
 * 관련도의 진짜 정의는 FULLTEXT 구현에 있다.
 */
@Repository
@Profile("!mysql-search")
public class LikeGoodsSearchRepository implements GoodsSearchRepository {

    private static final String HIDDEN = "HIDDEN";

    private final EntityManager em;

    public LikeGoodsSearchRepository(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<SearchRow> search(SearchCondition condition) {
        String jpql = "select g.id, b.name, g.name, g.thumbnailUrl, g.listPrice, g.salePrice "
                + "from Goods g join g.brand b "
                + "where g.status <> :hidden and (g.name like :pattern or b.name like :pattern) "
                + "order by " + orderBy(condition);

        TypedQuery<Object[]> query = em.createQuery(jpql, Object[].class)
                .setParameter("hidden", HIDDEN)
                .setParameter("pattern", "%" + condition.keyword() + "%");
        query.setFirstResult(condition.page() * condition.size());
        query.setMaxResults(condition.size());

        return query.getResultList().stream()
                .map(row -> new SearchRow(
                        (Long) row[0],
                        (String) row[1],
                        (String) row[2],
                        (String) row[3],
                        (Integer) row[4],
                        (Integer) row[5]))
                .toList();
    }

    @Override
    public long count(SearchCondition condition) {
        return em.createQuery(
                        "select count(g) from Goods g join g.brand b "
                                + "where g.status <> :hidden and (g.name like :pattern or b.name like :pattern)",
                        Long.class)
                .setParameter("hidden", HIDDEN)
                .setParameter("pattern", "%" + condition.keyword() + "%")
                .getSingleResult();
    }

    @Override
    public List<String> autocomplete(String prefix, int limit) {
        return em.createQuery(
                        "select distinct g.name from Goods g "
                                + "where g.status <> :hidden and g.name like :prefix order by g.name asc",
                        String.class)
                .setParameter("hidden", HIDDEN)
                .setParameter("prefix", prefix + "%")
                .setMaxResults(limit)
                .getResultList();
    }

    private String orderBy(SearchCondition condition) {
        return switch (condition.sort()) {
            // 상품명에 걸린 것을 브랜드명만 걸린 것보다 앞에 둔다 — LIKE로 낼 수 있는 최선의 관련도 근사.
            case ACCURACY -> "case when g.name like :pattern then 0 else 1 end asc, g.id desc";
            case POPULAR -> "g.viewCount desc, g.id desc";
            case NEW -> "g.createdAt desc, g.id desc";
            case PRICE_ASC -> "g.salePrice asc, g.id desc";
        };
    }
}
