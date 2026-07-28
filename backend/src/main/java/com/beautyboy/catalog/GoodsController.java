package com.beautyboy.catalog;

import com.beautyboy.catalog.dto.GoodsDescriptionResponse;
import com.beautyboy.catalog.dto.GoodsDetailResponse;
import com.beautyboy.catalog.dto.GoodsListItem;
import com.beautyboy.catalog.dto.GoodsSearchCondition;
import com.beautyboy.common.ApiResponse;
import com.beautyboy.common.PageRequests;
import com.beautyboy.common.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class GoodsController {

    private final GoodsService goodsService;
    private final ViewCountRecorder viewCountRecorder;

    public GoodsController(GoodsService goodsService, ViewCountRecorder viewCountRecorder) {
        this.goodsService = goodsService;
        this.viewCountRecorder = viewCountRecorder;
    }

    @GetMapping("/api/v1/goods")
    public ResponseEntity<ApiResponse<PageResponse<GoodsListItem>>> list(
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) List<Long> brandId,
            @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice,
            @RequestParam(defaultValue = "popular") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String tag,
            @AuthenticationPrincipal Long memberId) {

        GoodsSort goodsSort = GoodsSort.fromParam(sort);
        int clampedPage = PageRequests.clampPage(page);
        int clampedSize = PageRequests.clampSize(size);

        GoodsSearchCondition condition = new GoodsSearchCondition(
                categoryCode, brandId, minPrice, maxPrice, goodsSort, clampedPage, clampedSize, tag);

        return ResponseEntity.ok(ApiResponse.ok(goodsService.list(condition, memberId)));
    }

    /**
     * 조회수 기록을 서비스 <b>바깥</b>에서 하는 이유: {@code goodsService.detail}은
     * {@code @Transactional(readOnly = true)}이라 커넥션을 하나 쥐고 있다. 그 안에서 기록이
     * REQUIRES_NEW로 두 번째 커넥션을 잡으면 요청 하나가 커넥션 2개를 동시에 점유한다.
     * Hikari 기본 풀이 10이라 동시 상세 10건이면 각자 바깥 커넥션을 쥔 채 안쪽 커넥션을 기다려
     * 서로 막히는 교착이 된다. 트랜잭션이 끝나 커넥션이 반납된 뒤에 기록하면 중첩 자체가 사라진다.
     *
     * <p>detail이 먼저 성공해야 기록한다 — 404(없는/숨긴 상품)는 예외로 빠져 여기 도달하지 않으므로
     * 존재하지 않는 goods_id로 카운터가 오염되지 않는다.
     */
    @GetMapping("/api/v1/goods/{goodsNo}")
    public ResponseEntity<ApiResponse<GoodsDetailResponse>> detail(@PathVariable Long goodsNo,
                                                                    @AuthenticationPrincipal Long memberId) {
        GoodsDetailResponse response = goodsService.detail(goodsNo, memberId);
        viewCountRecorder.record(goodsNo);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/api/v1/goods/{goodsNo}/description")
    public ResponseEntity<ApiResponse<GoodsDescriptionResponse>> description(@PathVariable Long goodsNo) {
        return ResponseEntity.ok(ApiResponse.ok(goodsService.description(goodsNo)));
    }

    @GetMapping("/api/v1/goods/{goodsNo}/recommended")
    public ResponseEntity<ApiResponse<List<GoodsListItem>>> recommended(@PathVariable Long goodsNo,
                                                                         @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(goodsService.recommended(goodsNo, memberId)));
    }
}
