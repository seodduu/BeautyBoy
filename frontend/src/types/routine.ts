import type { EdgeKind } from './goods';

/**
 * 전이 규칙 한 줄 — 백엔드 FlowRuleView
 * (backend/src/main/java/com/beautyboy/routine/dto/FlowRuleView.java)와 필드를 1:1로 맞춘다.
 *
 * `fromTagSlug`/`toTagSlug`는 NULL 허용이다(V74__routine_flow_rule.sql) —
 * from이 NULL이면 "태그 무관", to가 NULL이면 "카테고리만 겨냥"이라는 뜻이라 optional이 아니라
 * nullable로 받는다. 둘을 optional로 두면 "필드가 없다"와 "NULL이다"가 섞여 매칭 분기가 흐려진다.
 */
export interface FlowRuleView {
  fromCategoryCode: string;
  fromTagSlug: string | null;
  toCategoryCode: string;
  toTagSlug: string | null;
  edgeKind: EdgeKind;
  /** 화면에 그대로 나가는 문구. DB가 유일한 출처 — 프론트가 문구를 만들지 않는다. */
  reason: string;
  /** 낮을수록 우선. BUFFER 10 < NEXT_STEP 20. */
  priority: number;
}

/**
 * 고민 → 목표 단계 규칙 한 줄 — 백엔드 ConcernRuleView와 1:1.
 * `to_*` 두 컬럼은 NOT NULL이다(V82__concern_target_rule.sql) — 앵커 상품이 없는 티어1에서
 * 태그까지 비면 겨냥할 것이 아무것도 남지 않기 때문이다.
 */
export interface ConcernRuleView {
  concernTagSlug: string;
  toCategoryCode: string;
  toTagSlug: string;
  reason: string;
  priority: number;
}

/**
 * GET /routine/flow-rules 응답 — 백엔드 FlowRulesResponse와 1:1.
 * version은 두 테이블을 정렬 직렬화한 SHA-256의 앞 16자이자 ETag 값이다. 클라이언트는 이 값을
 * 그대로 다음 요청의 If-None-Match에 실어 재방문 비용을 0으로 만든다(설계 §5.2).
 */
export interface FlowRulesResponse {
  version: string;
  flowRules: FlowRuleView[];
  concernRules: ConcernRuleView[];
}
