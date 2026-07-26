package com.beautyboy.catalog;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GoodsRepository extends JpaRepository<Goods, Long> {

    /**
     * 상세 조회 전용. brand를 join fetch해 1쿼리로 끝낸다(옵션은 컬렉션이라 별도 조회 —
     * 컬렉션 fetch join을 브랜드 fetch join과 함께 쓰면 카테시안 곱이 생긴다).
     * HIDDEN 상품은 목록에서 숨긴 것을 URL로 직접 보면 안 되므로 조회 대상에서 제외한다.
     */
    @Query("select g from Goods g join fetch g.brand where g.id = :id and g.status <> :hidden")
    Optional<Goods> findDetailById(@Param("id") Long id, @Param("hidden") String hidden);

    boolean existsByIdAndStatusNot(Long id, String status);

    /**
     * 옵션은 엔티티(GoodsOption)가 아니라 스칼라 프로젝션으로 조회한다. 이 리포지토리의 관리 대상
     * 타입은 Goods인데, {@code @Query}가 다른 엔티티 전체(o)를 그대로 select하면 Spring Data JPA가
     * "DTO 프로젝션 요청"으로 오인해 {@code new GoodsOption(*)} 생성자 표현식을 시도하다 JPQL 문법
     * 오류를 낸다. 컬럼만 뽑으면 이 문제를 피한다.
     */
    /**
     * {@code id asc}를 2차 정렬 키로 더한 이유: 대표 옵션 선택({@link GoodsService}의
     * {@code 대표_옵션_순서})이 {@code sortOrder} → {@code id} 순으로 결정적이다. 이 쿼리에
     * 2차 키가 없으면 {@code sortOrder}가 동률인 옵션에서 화면 첫 옵션과 서버가 고르는 대표
     * 옵션이 엔진 순서(정렬 안정성 미보장)에 따라 어긋날 수 있다 — 그러면
     * {@link GoodsService#findOrderSnapshot}의 Javadoc이 말하는 "화면 첫 옵션 = 서버 대표 옵션"
     * 전제가 깨진다.
     */
    @Query("select o.id, o.name, o.addPrice, o.stock, o.sortOrder "
            + "from GoodsOption o where o.goods.id = :goodsId order by o.sortOrder asc, o.id asc")
    List<Object[]> findOptionRowsByGoodsId(@Param("goodsId") Long goodsId);

    @Query("select g.id, b.name, g.name, g.thumbnailUrl, g.listPrice, g.salePrice "
            + "from Goods g join g.brand b "
            + "where g.categoryCode = :categoryCode and g.status <> :hidden and g.id <> :excludeId "
            + "order by g.viewCount desc, g.id desc")
    List<Object[]> findRecommendedRows(@Param("categoryCode") String categoryCode,
                                        @Param("hidden") String hidden,
                                        @Param("excludeId") Long excludeId,
                                        Pageable pageable);

    /**
     * 조회수 누적 증가. 엔티티를 읽어 더하지 않고 벌크 UPDATE 하나로 끝낸다 —
     * 읽고-쓰기 사이에 다른 조회가 끼어들면 증가분이 사라지는데(lost update),
     * {@code view_count = view_count + :delta}는 DB가 원자적으로 처리한다.
     *
     * <p>delta가 1이 아닐 수 있는 이유: Redis 버퍼를 쓰면 1분치가 한 번에 모여서 들어온다
     * ({@link ViewCountFlushScheduler}). DB 폴백 경로는 delta=1로 호출한다.
     */
    @Modifying
    @Query("update Goods g set g.viewCount = g.viewCount + :delta where g.id = :id")
    void addViewCount(@Param("id") Long id, @Param("delta") int delta);

    /**
     * "다음 단계" 추천(routine)의 태그 무관 후보 조회. 같은 카테고리 코드 접두사 아래에서
     * 조회수 내림차순, 자기 자신·HIDDEN은 제외한다. {@code id desc}를 2차 정렬 키로 두는
     * 이유는 {@link #findRecommendedRows}와 같다 — 조회수가 동률일 때도 결정적이어야 한다.
     */
    @Query("select g.id from Goods g "
            + "where g.categoryCode like concat(:prefix, '%') and g.status <> :hidden and g.id <> :excludeId "
            + "order by g.viewCount desc, g.id desc")
    List<Long> findCandidateIds(@Param("prefix") String prefix, @Param("hidden") String hidden,
                                @Param("excludeId") Long excludeId, Pageable pageable);

    /**
     * 위와 같은 후보 조회에 태그 슬러그 조건을 더한 버전. goods_tag·tag를 세타 조인해
     * 지정한 슬러그가 달린 상품만 남긴다(패키지 경계상 GoodsTag/Tag 엔티티는 이 catalog
     * 패키지 안에서만 다루므로 여기서 직접 조인해도 규칙 위반이 아니다).
     */
    @Query("select g.id from Goods g "
            + "where g.categoryCode like concat(:prefix, '%') and g.status <> :hidden and g.id <> :excludeId "
            + "and exists (select 1 from GoodsTag gt, Tag t where gt.tagId = t.id and gt.goodsId = g.id and t.slug = :tagSlug) "
            + "order by g.viewCount desc, g.id desc")
    List<Long> findCandidateIdsByTag(@Param("prefix") String prefix, @Param("tagSlug") String tagSlug,
                                     @Param("hidden") String hidden, @Param("excludeId") Long excludeId,
                                     Pageable pageable);
}
