package com.beautyboy.experiment.dto;

import java.util.List;

/**
 * 개인화 조합 요청 — 클라이언트 링버퍼 내용물과 단계별 후보 풀을 그대로 실어 보낸다.
 *
 * <p>후보 풀을 바디로 받는 이유: 비교 실험이므로 클라이언트 {@code composeStep}과 입력이 한 글자도
 * 달라선 안 된다. 서버가 카탈로그를 직접 읽으면 (a) 입력이 갈라져 동등성 주장이 무너지고,
 * (b) 측정값에 조회 비용이 섞여 "개인화 계산의 비용"이 아니게 된다. 그 대가로 이 수치가 서버 쪽에
 * 유리한 하한이라는 사실은 리포트 한계 절에 적혀 있다.
 *
 * <p>후보의 필드가 셋뿐인 것도 같은 이유다 — 조합기가 실제로 읽는 것은 {@code goodsNo}·{@code tags}이고
 * 체인의 앵커 계산에 {@code cat3}가 더 필요하다. 가격·썸네일을 실어 페이로드를 부풀리면 JSON 직렬화
 * 비용이 계산 비용을 가린다.
 */
public record AffinityNextStepRequest(List<Step> steps, Signals signals, List<Event> events,
                                      List<Conflict> conflicts) {

    /** 루틴 단계 하나. 순서대로 확정되며 앞 단계의 픽이 뒤 단계의 앵커가 된다. */
    public record Step(String id, String categoryCode, List<Candidate> candidates) {
    }

    /** 후보 한 줄. {@code cat3}는 중분류 7자(규칙의 category_code 길이). */
    public record Candidate(long goodsNo, String cat3, List<String> tags) {
    }

    /**
     * 매칭 신호. {@code concerns}는 피부타입 파생까지 끝난 값(클라 {@code effectiveConcerns} 결과)이고
     * <b>선택 순서가 의미를 갖는다</b>(고민 규칙 폴백이 이 순서를 훑는다).
     */
    public record Signals(List<String> concerns, List<String> textures, boolean concernOverride) {
    }

    /** 행동 이벤트 한 건. {@code w}는 조회 1·찜 2·담기 3. */
    public record Event(long goodsNo, String cat3, List<String> tags, int w) {
    }

    /** 궁합 CONFLICT 쌍. {@code conflicts}가 null이면 게이트 없음(클라 {@code verdicts=null}과 같다). */
    public record Conflict(long base, long goodsNo) {
    }
}
