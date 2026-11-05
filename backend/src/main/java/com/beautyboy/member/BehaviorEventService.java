package com.beautyboy.member;

import com.beautyboy.catalog.GoodsQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * 행동 로그 수집. 저장 여부를 결정하는 순서는 레이트 리밋 판정 → 동의 조회 → 상품 존재 확인 →
 * 저장이다. 리밋은 <b>버리는 요청도 센다</b>. 리밋의 목적이 서버 일을 줄이는 것이라 동의 여부와
 * 무관하게 먼저 막아야 한다.
 *
 * <p>동의하지 않은 회원, 없거나 HIDDEN인 상품은 둘 다 <b>조용히 버린다</b>(예외 없이 그냥 반환).
 * 클라이언트가 자기 동의 상태를 몰라도 되게 하는 것이 이 API의 설계 목적이고, 없는 상품에 404를
 * 주면 카탈로그의 존재 여부가 익명 요청에도 새어 나간다. 상품 존재 확인은
 * {@link GoodsQueryService#exists(Long)}만 쓴다(패키지 = 서비스 경계, GoodsRepository 직접
 * import 금지).
 */
@Service
public class BehaviorEventService {

    private final BehaviorEventRepository behaviorEventRepository;
    private final MemberRepository memberRepository;
    private final GoodsQueryService goodsQueryService;
    private final BehaviorEventRateLimiter rateLimiter;

    public BehaviorEventService(BehaviorEventRepository behaviorEventRepository,
                                 MemberRepository memberRepository,
                                 GoodsQueryService goodsQueryService,
                                 BehaviorEventRateLimiter rateLimiter) {
        this.behaviorEventRepository = behaviorEventRepository;
        this.memberRepository = memberRepository;
        this.goodsQueryService = goodsQueryService;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public void record(Long memberId, Long goodsNo, BehaviorKind kind) {
        if (!rateLimiter.tryAcquire(memberId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        }

        Member member = memberRepository.findById(memberId).orElse(null);
        MemberProfile profile = member == null ? null : member.getProfile();
        if (profile == null || !profile.isBehaviorConsent()) {
            return;
        }

        if (!goodsQueryService.exists(goodsNo)) {
            return;
        }

        behaviorEventRepository.save(new BehaviorEvent(memberId, goodsNo, kind, LocalDateTime.now()));
    }
}
