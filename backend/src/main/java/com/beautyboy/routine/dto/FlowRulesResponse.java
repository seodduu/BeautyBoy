package com.beautyboy.routine.dto;

import java.util.List;

/**
 * 규칙 전량 배포 응답(설계 §5.2).
 *
 * @param version      두 테이블을 정렬 직렬화한 SHA-256의 앞 16자. ETag 헤더와 같은 값이라
 *                     클라이언트가 본문만 보고도 다음 요청의 If-None-Match를 만들 수 있다.
 * @param flowRules    routine_flow_rule 전량
 * @param concernRules concern_target_rule 전량
 */
public record FlowRulesResponse(String version, List<FlowRuleView> flowRules,
                                List<ConcernRuleView> concernRules) {
}
