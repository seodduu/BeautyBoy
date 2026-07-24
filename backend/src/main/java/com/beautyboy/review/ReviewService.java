package com.beautyboy.review;

import com.beautyboy.catalog.GoodsQueryService;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.common.PageResponse;
import com.beautyboy.order.OrderQueryService;
import com.beautyboy.review.dto.ReviewCreateRequest;
import com.beautyboy.review.dto.ReviewResponse;
import com.beautyboy.review.dto.ReviewStatResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 리뷰 작성·조회 + 평점 통계 재집계.
 *
 * <p>핵심 판단 둘: (1) 구매인증 — order 테이블을 직접 보지 않고 OrderQueryService로 확인한다.
 * (2) 평점 통계 — 리뷰가 바뀔 때마다 그 상품의 리뷰를 통째로 다시 집계해 upsert한다.
 * 증분(+1, 평균 재계산)을 쓰지 않는 이유는 동시 작성 시 값이 어긋나기 때문이다 —
 * MVP 규모(상품당 리뷰 수십 개)에서 재집계 비용은 무시할 만하다.
 */
@Service
public class ReviewService {

    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final ReviewRepository reviewRepository;
    private final GoodsReviewStatRepository goodsReviewStatRepository;
    private final OrderQueryService orderQueryService;
    private final GoodsQueryService goodsQueryService;

    public ReviewService(ReviewRepository reviewRepository,
                         GoodsReviewStatRepository goodsReviewStatRepository,
                         OrderQueryService orderQueryService,
                         GoodsQueryService goodsQueryService) {
        this.reviewRepository = reviewRepository;
        this.goodsReviewStatRepository = goodsReviewStatRepository;
        this.orderQueryService = orderQueryService;
        this.goodsQueryService = goodsQueryService;
    }

    @Transactional
    public void create(Long memberId, ReviewCreateRequest request) {
        if (request.rating() < MIN_RATING || request.rating() > MAX_RATING) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (!goodsQueryService.exists(request.goodsNo())) {
            throw new BusinessException(ErrorCode.GOODS_NOT_FOUND);
        }
        // 구매인증: 산 사람만 쓴다. order 테이블을 직접 보지 않는 유일한 통로.
        if (!orderQueryService.hasPurchased(memberId, request.goodsNo())) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_PURCHASED);
        }
        // 상품당 1리뷰. DB 유니크 제약이 최종 방어선이지만 여기서 먼저 걸러 409를 명확히 준다.
        if (reviewRepository.existsByMemberIdAndGoodsId(memberId, request.goodsNo())) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_WRITTEN);
        }

        // 피부타입 스냅샷: member 조회 통로가 없으면 null로 둔다(T3-7 보고).
        reviewRepository.save(new Review(memberId, request.goodsNo(), request.rating(), request.content(), null));

        recalculateStat(request.goodsNo());
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> list(Long goodsNo, int page) {
        List<Review> reviews = reviewRepository.findByGoodsIdOrderByCreatedAtDesc(
                goodsNo, PageRequest.of(page, DEFAULT_PAGE_SIZE));
        long total = reviewRepository.countByGoodsId(goodsNo);
        List<ReviewResponse> items = reviews.stream().map(this::toResponse).toList();
        return PageResponse.of(items, page, DEFAULT_PAGE_SIZE, total);
    }

    @Transactional(readOnly = true)
    public ReviewStatResponse stat(Long goodsNo) {
        // 통계 테이블을 읽는다(매번 AVG를 돌리지 않는다). 없으면 0건으로 응답한다.
        return goodsReviewStatRepository.findById(goodsNo)
                .map(s -> new ReviewStatResponse(s.getReviewCount(), s.average()))
                .orElse(new ReviewStatResponse(0, 0.0));
    }

    /**
     * 그 상품의 리뷰를 통째로 재집계해 goods_review_stat을 upsert한다.
     * 작성·삭제 어느 경로에서도 이 한 메서드만 부르면 통계가 항상 리뷰와 일치한다.
     */
    private void recalculateStat(Long goodsNo) {
        // [count, sum]을 한 쿼리로. 리뷰가 0건이면 count=0, sum=0.
        // Spring Data가 다중 컬럼 결과를 List<Object[]>로 반환한 뒤 반환 타입(Object[])에 맞춰
        // 컬렉션 자체를 배열로 변환하므로, agg[0]에 실제 [count, sum] 행이 한 번 더 감싸여 온다.
        Object[] raw = reviewRepository.aggregate(goodsNo);
        Object[] agg = (Object[]) raw[0];
        int count = ((Number) agg[0]).intValue();
        int sum = ((Number) agg[1]).intValue();

        GoodsReviewStat stat = goodsReviewStatRepository.findById(goodsNo)
                .orElseGet(() -> new GoodsReviewStat(goodsNo));
        stat.update(count, sum, LocalDateTime.now());
        goodsReviewStatRepository.save(stat);
    }

    private ReviewResponse toResponse(Review r) {
        return new ReviewResponse(r.getId(), r.getMemberId(), r.getRating(), r.getContent(),
                r.getSkinTypeSnapshot(), r.getHelpfulCount(), r.getCreatedAt());
    }
}
