package com.beautyboy.member;

import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.member.dto.BehaviorEventRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 행동 로그 수집 API. 동의하지 않은 회원과 없는 상품은 둘 다 204로 조용히 버린다(400도 404도
 * 아니다). 자세한 근거는 {@link BehaviorEventService}.
 *
 * <p>429는 여기서 직접 응답을 만든다. {@code GlobalExceptionHandler}(common, 동결)의
 * {@code Exception.class} 캐치올이 {@link ResponseStatusException}까지 가로채 500으로
 * 바꿔버리기 때문이다(실측: 캐치올을 거치면 429가 아니라 500이 났다). 그 파일은 이 태스크의
 * Files 밖이라 고치지 않고, 서비스가 던진 예외를 컨트롤러가 받아 상태 코드만 그대로 옮긴다.
 */
@RestController
public class BehaviorEventController {

    private final BehaviorEventService behaviorEventService;

    public BehaviorEventController(BehaviorEventService behaviorEventService) {
        this.behaviorEventService = behaviorEventService;
    }

    @PostMapping("/api/v1/events")
    public ResponseEntity<Void> record(@AuthenticationPrincipal Long memberId,
                                        @Valid @RequestBody BehaviorEventRequest request) {
        BehaviorKind kind = parseKind(request.kind());
        try {
            behaviorEventService.record(memberId, request.goodsNo(), kind);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        }
        return ResponseEntity.noContent().build();
    }

    private BehaviorKind parseKind(String raw) {
        try {
            return BehaviorKind.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
