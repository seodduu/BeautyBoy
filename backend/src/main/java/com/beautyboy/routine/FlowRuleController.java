package com.beautyboy.routine;

import com.beautyboy.common.ApiResponse;
import com.beautyboy.routine.dto.FlowRulesResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 규칙 전량 배포(설계 §5.2). 기기 측이 추천 매칭을 로컬에서 돌리기 위해 앱 진입마다 부르는 자리라
 * 재방문 비용을 0에 가깝게 만드는 것이 이 컨트롤러의 목적이다 — {@code If-None-Match}가
 * 캐시된 version과 같으면 본문 없이 304로 끊는다.
 *
 * <p>ETag 헤더는 RFC 9110대로 따옴표에 싸여 나간다(Spring이 강제한다 — {@code HttpHeaders.setETag}가
 * 따옴표 없는 값을 감싼다). 반면 클라이언트는 {@code body.version}을 그대로 다음 요청의
 * {@code If-None-Match}에 넣기 마련이라, 들어오는 값은 따옴표가 있든 없든 같게 본다.
 * 서버가 관대한 쪽이 맞다 — 여기서 엄격해봐야 얻는 것 없이 304를 놓칠 뿐이다.
 */
@RestController
public class FlowRuleController {

    private final FlowRuleService flowRuleService;

    public FlowRuleController(FlowRuleService flowRuleService) {
        this.flowRuleService = flowRuleService;
    }

    @GetMapping("/api/v1/routine/flow-rules")
    public ResponseEntity<ApiResponse<FlowRulesResponse>> flowRules(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        FlowRulesResponse rules = flowRuleService.rules();
        if (rules.version().equals(unquote(ifNoneMatch))) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, rules.version())
                .body(ApiResponse.ok(rules));
    }

    /** {@code "abc"}·{@code W/"abc"}·{@code abc}를 모두 {@code abc}로 접는다. */
    private static String unquote(String etag) {
        if (etag == null) {
            return null;
        }
        String trimmed = etag.trim();
        if (trimmed.startsWith("W/")) {
            trimmed = trimmed.substring(2);
        }
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
