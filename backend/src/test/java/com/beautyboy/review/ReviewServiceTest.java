package com.beautyboy.review;

import com.beautyboy.catalog.GoodsQueryService;
import com.beautyboy.catalog.GoodsReviewCountCommand;
import com.beautyboy.catalog.dto.GoodsListItem;
import com.beautyboy.common.PageResponse;
import com.beautyboy.order.OrderQueryService;
import com.beautyboy.review.dto.MyReviewItem;
import com.beautyboy.review.dto.ReviewCreateRequest;
import com.beautyboy.review.dto.ReviewResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * `GET /reviews/me` — 마이페이지 "내 리뷰". 상품명·썸네일은 GoodsQueryService를 통해서만
 * 가져온다(review가 goods 테이블을 직접 읽지 않는다 — 경계 규칙).
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    ReviewRepository reviewRepository;
    @Mock
    GoodsReviewStatRepository goodsReviewStatRepository;
    @Mock
    OrderQueryService orderQueryService;
    @Mock
    GoodsQueryService goodsQueryService;
    @Mock
    ReviewHelpfulRepository reviewHelpfulRepository;
    @Mock
    GoodsReviewCountCommand goodsReviewCountCommand;
    @InjectMocks
    ReviewService reviewService;

    private static final Long 회원1 = 1L;
    private static final Long 회원2 = 2L;
    private static final Long 상품A = 100L;
    private static final Long 남의리뷰 = 999L;

    private static GoodsListItem 카드(Long goodsNo, String name, String thumbnailUrl) {
        return new GoodsListItem(goodsNo, "브랜드", name, thumbnailUrl, 10000, 9000, 10,
                List.of(), 0.0, 0, false, false, List.of());
    }

    @Test
    void 내_리뷰만_최신순으로_상품명과_함께_돌려준다() {
        Review 리뷰 = new Review(회원1, 상품A, 5, "좋아요", null);
        given(reviewRepository.findByMemberIdOrderByIdDesc(org.mockito.ArgumentMatchers.eq(회원1),
                org.mockito.ArgumentMatchers.any(PageRequest.class)))
                .willReturn(List.of(리뷰));
        given(reviewRepository.countByMemberId(회원1)).willReturn(1L);
        given(goodsQueryService.findListItems(anyCollection(), isNull()))
                .willReturn(List.of(카드(상품A, "그린티 토너", "https://example.com/a.jpg")));

        PageResponse<MyReviewItem> page = reviewService.myReviews(회원1, 0, 10);

        assertThat(page.content()).extracting(MyReviewItem::goodsName).containsExactly("그린티 토너");
        assertThat(page.content()).extracting(MyReviewItem::reviewId).doesNotContain(남의리뷰);
    }

    @Test
    void 리뷰가_없으면_빈_페이지다() {
        given(reviewRepository.findByMemberIdOrderByIdDesc(org.mockito.ArgumentMatchers.eq(회원2),
                org.mockito.ArgumentMatchers.any(PageRequest.class)))
                .willReturn(List.of());
        given(reviewRepository.countByMemberId(회원2)).willReturn(0L);

        assertThat(reviewService.myReviews(회원2, 0, 10).content()).isEmpty();
    }

    @Test
    void 리뷰를_쓰면_goods_review_count가_재집계된_리뷰수로_동기화된다() {
        given(goodsQueryService.exists(상품A)).willReturn(true);
        given(orderQueryService.hasPurchased(회원1, 상품A)).willReturn(true);
        given(reviewRepository.existsByMemberIdAndGoodsId(회원1, 상품A)).willReturn(false);
        // recalculateStat이 aggregate 결과를 List<Object[]>를 Object[]로 감아 반환하므로 raw[0]가 [count, sum]이다.
        given(reviewRepository.aggregate(상품A)).willReturn(new Object[] { new Object[] { 2, 9 } });
        given(goodsReviewStatRepository.findById(상품A)).willReturn(Optional.empty());

        reviewService.create(회원1, new ReviewCreateRequest(상품A, 5, "좋아요"));

        verify(goodsReviewCountCommand).syncReviewCount(eq(상품A), eq(2));
    }

    @Test
    void list_page가_음수여도_500이_아니라_0페이지를_준다() {
        given(reviewRepository.findByGoodsIdOrderByCreatedAtDesc(eq(상품A), any(PageRequest.class)))
                .willReturn(List.of());
        given(reviewRepository.countByGoodsId(상품A)).willReturn(0L);

        PageResponse<ReviewResponse> result = reviewService.list(상품A, -1);

        assertThat(result.page()).isZero();
    }

    @Test
    void myReviews_음수_page와_size를_안전한_값으로_조인다() {
        given(reviewRepository.findByMemberIdOrderByIdDesc(eq(회원1), any(PageRequest.class)))
                .willReturn(List.of());
        given(reviewRepository.countByMemberId(회원1)).willReturn(0L);

        PageResponse<MyReviewItem> result = reviewService.myReviews(회원1, -1, -5);

        assertThat(result.page()).isZero();
    }

    @Test
    void myReviews_size_상한은_100이다() {
        given(reviewRepository.countByMemberId(회원1)).willReturn(0L);

        reviewService.myReviews(회원1, 0, 100_000);

        ArgumentCaptor<PageRequest> pageRequest = ArgumentCaptor.forClass(PageRequest.class);
        verify(reviewRepository).findByMemberIdOrderByIdDesc(eq(회원1), pageRequest.capture());
        assertThat(pageRequest.getValue().getPageSize()).isEqualTo(100);
    }
}
