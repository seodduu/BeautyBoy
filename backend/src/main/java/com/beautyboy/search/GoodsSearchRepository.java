package com.beautyboy.search;

import com.beautyboy.search.dto.SearchCondition;

import java.util.List;

/**
 * 검색 질의의 교체 지점.
 *
 * <p>설계 8장이 "검색 모듈 인터페이스 분리 → 2차 Elasticsearch 교체 지점"을 요구한다.
 * 동시에 현실 제약이 같은 답을 가리킨다: 유닛테스트는 H2이고 H2에는 FULLTEXT가 없다.
 * 그래서 구현을 둘 둔다 — 운영은 {@link MysqlFulltextGoodsSearchRepository},
 * 테스트/H2는 {@link LikeGoodsSearchRepository}.
 *
 * <p>어느 구현이 뜰지는 프로필로 정한다. 이 인터페이스 밖에는 SQL이 없어야 한다 —
 * 서비스가 MATCH 문법을 알게 되는 순간 교체 지점이 사라진다.
 */
public interface GoodsSearchRepository {

    /** 조건에 맞는 상품 한 페이지. 숨김(HIDDEN) 상품은 어떤 구현에서도 제외한다. */
    List<SearchRow> search(SearchCondition condition);

    /** 조건에 맞는 전체 건수. {@code PageResponse.of}에 그대로 들어간다. */
    long count(SearchCondition condition);

    /**
     * 자동완성 후보 상품명. prefix 일치 상위 {@code limit}개.
     *
     * @return 상품명 목록. 중복은 제거된 상태로 반환한다.
     */
    List<String> autocomplete(String prefix, int limit);

    /**
     * 검색 결과 1행. 목록 화면에 필요한 컬럼만 담는다 —
     * description(TEXT)이 없으므로 검색 경로에서는 애초에 조회되지 않는다.
     */
    record SearchRow(
            Long goodsId,
            String brandName,
            String name,
            String thumbnailUrl,
            int listPrice,
            int salePrice) {
    }
}
