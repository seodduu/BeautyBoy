package com.beautyboy.catalog;

import com.beautyboy.catalog.dto.GoodsSearchCondition;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * QueryDSL 미도입(빌드 설정 동결) — JPQL + EntityManager로 조건을 직접 조립한다.
 * 목록은 스칼라 프로젝션(select g.id, b.name, ...)으로 1쿼리, count는 별도,
 * 배지는 조회된 goodsId 목록으로 프로모션을 1쿼리 일괄 조회해 메모리에서 매핑한다.
 * 총 쿼리 수: 목록 1 + count 1 + 배지 1 = 3.
 * <p>
 * 목록 쿼리가 {@code select g from Goods g ...}로 엔티티를 통째로 로딩하지 않는 이유:
 * JPA에서 엔티티를 select하면 select 절에 컬럼을 안 적어도 매핑된 컬럼이 전부(=description
 * TEXT 포함) 함께 로딩된다 — SQL 프로젝션이 아니라 엔티티 로딩이기 때문이다. 그래서 여기서는
 * 처음부터 {@code Object[]}로 필요한 컬럼만 뽑아 {@link GoodsRow}로 옮겨 담는다.
 */
@Repository
public class GoodsQueryRepository {

    private final EntityManager em;

    public GoodsQueryRepository(EntityManager em) {
        this.em = em;
    }

    public List<GoodsRow> findList(GoodsSearchCondition condition) {
        StringBuilder jpql = new StringBuilder(
                "select g.id, b.name, g.name, g.thumbnailUrl, g.listPrice, g.salePrice "
                        + "from Goods g join g.brand b where g.status <> :hidden");
        appendFilters(jpql, condition);
        jpql.append(" order by ").append(condition.sort().orderByClause());

        TypedQuery<Object[]> query = em.createQuery(jpql.toString(), Object[].class);
        bindParameters(query, condition);
        query.setFirstResult(condition.page() * condition.size());
        query.setMaxResults(condition.size());

        return query.getResultList().stream()
                .map(row -> new GoodsRow(
                        (Long) row[0],
                        (String) row[1],
                        (String) row[2],
                        (String) row[3],
                        (Integer) row[4],
                        (Integer) row[5]))
                .toList();
    }

    public long count(GoodsSearchCondition condition) {
        StringBuilder jpql = new StringBuilder(
                "select count(g) from Goods g where g.status <> :hidden");
        appendFilters(jpql, condition);

        TypedQuery<Long> query = em.createQuery(jpql.toString(), Long.class);
        bindParameters(query, condition);
        return query.getSingleResult();
    }

    /**
     * 조회된 상품 goodsId 목록으로 유효기간 내(starts_at <= now <= ends_at) 프로모션 배지를
     * 1쿼리 일괄 조회해 goodsId -> badgeType 목록으로 매핑한다.
     */
    public Map<Long, List<String>> findValidBadges(List<Long> goodsIds, LocalDateTime now) {
        if (goodsIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = em.createQuery(
                        "select pg.goodsId, p.badgeType from PromotionGoods pg "
                                + "join Promotion p on pg.promotionId = p.id "
                                + "where pg.goodsId in :goodsIds and p.startsAt <= :now and p.endsAt >= :now",
                        Object[].class)
                .setParameter("goodsIds", goodsIds)
                .setParameter("now", now)
                .getResultList();

        Map<Long, List<String>> badgesByGoodsId = new HashMap<>();
        for (Object[] row : rows) {
            Long goodsId = (Long) row[0];
            String badgeType = (String) row[1];
            badgesByGoodsId.computeIfAbsent(goodsId, id -> new java.util.ArrayList<>()).add(badgeType);
        }
        return badgesByGoodsId;
    }

    /**
     * id 목록으로 경량 프로젝션을 1쿼리 일괄 조회한다. HIDDEN은 목록·상세와 같은 기준으로 제외.
     * 입력 순서를 보존하지 않는다(IN 조회 결과 순서). description(TEXT)은 프로젝션에 없다.
     */
    public List<GoodsRow> findByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return em.createQuery(
                        "select g.id, b.name, g.name, g.thumbnailUrl, g.listPrice, g.salePrice "
                                + "from Goods g join g.brand b where g.id in :ids and g.status <> :hidden",
                        Object[].class)
                .setParameter("ids", ids)
                .setParameter("hidden", Goods.STATUS_HIDDEN)
                .getResultList().stream()
                .map(row -> new GoodsRow(
                        (Long) row[0],
                        (String) row[1],
                        (String) row[2],
                        (String) row[3],
                        (Integer) row[4],
                        (Integer) row[5]))
                .toList();
    }

    /**
     * 관리자 목록. 일반 목록({@link #findList})과의 차이는 둘: {@code g.status <> :hidden} 필터가
     * 없다는 것(숨긴 상품을 관리자가 못 보면 되살릴 방법이 없다) — 그리고 {@code status} 컬럼을
     * 함께 프로젝션한다는 것(Task 4-14a: admin 화면의 "숨김" 배지가 실 API로 동작하려면 필요하다).
     * 반환 타입이 공용 {@link GoodsRow}가 아니라 {@link AdminGoodsRow}인 이유도 그래서다 —
     * 일반 목록·검색·랭킹·루틴이 의존하는 {@code GoodsRow}/{@code GoodsListItem}은 건드리지 않는다.
     * q는 상품명 부분 일치.
     */
    public List<AdminGoodsRow> findAdminList(String q, int page, int size) {
        StringBuilder jpql = new StringBuilder(
                "select g.id, b.name, g.name, g.thumbnailUrl, g.listPrice, g.salePrice, g.status "
                        + "from Goods g join g.brand b where 1 = 1");
        if (q != null && !q.isBlank()) {
            jpql.append(" and g.name like :q");
        }
        jpql.append(" order by g.id desc");

        TypedQuery<Object[]> query = em.createQuery(jpql.toString(), Object[].class);
        if (q != null && !q.isBlank()) {
            query.setParameter("q", "%" + q + "%");
        }
        query.setFirstResult(page * size);
        query.setMaxResults(size);

        return query.getResultList().stream()
                .map(row -> new AdminGoodsRow(
                        (Long) row[0],
                        (String) row[1],
                        (String) row[2],
                        (String) row[3],
                        (Integer) row[4],
                        (Integer) row[5],
                        (String) row[6]))
                .toList();
    }

    public long countAdmin(String q) {
        StringBuilder jpql = new StringBuilder("select count(g) from Goods g where 1 = 1");
        if (q != null && !q.isBlank()) {
            jpql.append(" and g.name like :q");
        }
        TypedQuery<Long> query = em.createQuery(jpql.toString(), Long.class);
        if (q != null && !q.isBlank()) {
            query.setParameter("q", "%" + q + "%");
        }
        return query.getSingleResult();
    }

    private void appendFilters(StringBuilder jpql, GoodsSearchCondition condition) {
        if (condition.categoryCode() != null && !condition.categoryCode().isBlank()) {
            jpql.append(" and g.categoryCode like :categoryPrefix");
        }
        if (condition.brandId() != null && !condition.brandId().isEmpty()) {
            jpql.append(" and g.brand.id in :brandIds");
        }
        if (condition.minPrice() != null) {
            jpql.append(" and g.salePrice >= :minPrice");
        }
        if (condition.maxPrice() != null) {
            jpql.append(" and g.salePrice <= :maxPrice");
        }
    }

    private void bindParameters(TypedQuery<?> query, GoodsSearchCondition condition) {
        query.setParameter("hidden", Goods.STATUS_HIDDEN);
        if (condition.categoryCode() != null && !condition.categoryCode().isBlank()) {
            query.setParameter("categoryPrefix", condition.categoryCode() + "%");
        }
        if (condition.brandId() != null && !condition.brandId().isEmpty()) {
            query.setParameter("brandIds", condition.brandId());
        }
        if (condition.minPrice() != null) {
            query.setParameter("minPrice", condition.minPrice());
        }
        if (condition.maxPrice() != null) {
            query.setParameter("maxPrice", condition.maxPrice());
        }
    }

    /**
     * 목록 화면에 필요한 컬럼만 담는 경량 프로젝션 결과. description(TEXT)은 여기 없으므로
     * 목록 조회 경로에서는 애초에 조회되지 않는다.
     */
    public record GoodsRow(
            Long goodsId,
            String brandName,
            String name,
            String thumbnailUrl,
            int listPrice,
            int salePrice) {
    }

    /**
     * admin 목록 전용 프로젝션 — {@link GoodsRow}와 같은 6개 필드에 {@code status}만 더한다.
     * admin 화면만 이 타입을 쓴다({@link #findAdminList}).
     */
    public record AdminGoodsRow(
            Long goodsId,
            String brandName,
            String name,
            String thumbnailUrl,
            int listPrice,
            int salePrice,
            String status) {
    }
}
