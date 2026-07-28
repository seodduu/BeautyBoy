import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { fetchVerdicts } from '../../api/compat';
import { fetchGoodsDetail, fetchGoodsList } from '../../api/goods';
import { fetchMe } from '../../api/member';
import { useAuthStore } from '../../stores/authStore';
import { ROUTINE_STEPS, type RoutineStep } from '../routine/steps';
import { readEvents, toCat3 } from './events';
import { EMPTY_RULES, loadFlowRules } from './flowRules';
import { aggregate, effectiveConcerns, preferredTextures } from './profile';
import type { DerivedConcern } from './profile';
import { POOL_SIZE, composeStep } from './composer';
import type { ComposerSignals, PrevPick, StepComposition } from './composer';
import type { SkinType } from '../../api/routine';
import type { GoodsListItem } from '../../types/goods';
import type { FlowRulesResponse } from '../../types/routine';

/**
 * 루틴 조합기 체인 — 5단계를 위에서부터 순차로 확정한다(설계 §3).
 *
 * 단계 s의 조합은 이전 단계 픽이 확정되어야 시작된다. 그래서 화면은 위에서부터 점진적으로
 * 채워지고(설계 §3.4), 스크롤 방향과 확정 순서가 같아 지연이 기다림으로 체감되지 않는다.
 *
 *   step[0]: 풀 조회 → composeStep(prevPick=null, verdicts=null)
 *   step[k]: 풀 조회 ∥ verdicts 조회(base=pick[k-1], candidates=풀) → composeStep
 *
 * **이 훅은 던지지 않는다.** 규칙을 못 받으면 reason 없이, 궁합 판정을 못 받으면 게이트 없이
 * 진행한다 — 부가 정보의 실패로 메인이 멈추면 안 된다(설계 §3.3).
 *
 * concernOverride — 세트 탭이 지정한 컨셉. 단독 대체라 세트 간 구성이 겹치지 않는다(스펙 §5).
 */

/** 규칙의 category_code 길이(중분류 7자). 단계 코드가 이보다 짧으면 픽의 중분류를 따로 알아내야 한다. */
const CAT3_LENGTH = 7;

export interface StepState {
  /** 확정된 조합. `undefined`면 **미확정**이라 섹션은 스켈레톤을 유지한다. */
  composition?: StepComposition;
  /** 이 단계의 후보 풀(서버 인기순). 픽이 없을 때 기준선 그리드로 폴백하는 데 쓴다. */
  pool: GoodsListItem[];
  /** 풀 조회 자체가 실패했다. 이 섹션만 에러 문구를 내고 체인은 계속된다. */
  isError: boolean;
}

interface StepLink extends StepState {
  /**
   * 이 단계가 확정됐는가 — 다음 단계를 열어 주는 열쇠다. 픽이 없는 것으로 확정된 경우
   * (풀 비었음·조회 실패)도 확정이다: 앵커만 사라질 뿐 체인은 멈추지 않는다.
   */
  settled: boolean;
  /** 다음 단계가 궁합 게이트·전이 규칙에 쓸 앵커. */
  anchor: PrevPick | null;
}

/** 한 단계의 조합 — 풀 조회 ∥ 궁합 판정 → `composeStep`. 체인의 한 마디다. */
function useStepComposition(input: {
  step: RoutineStep;
  /** 이전 단계가 확정됐는가(첫 단계는 신호가 갖춰졌는가). */
  chainReady: boolean;
  anchor: PrevPick | null;
  signals: ComposerSignals;
  rules: FlowRulesResponse;
}): StepLink {
  const { step, chainReady, anchor, signals, rules } = input;

  // 기존 ['routine-goods', …] 캐시와 분리한다 — size가 다르다(4 vs POOL_SIZE).
  const poolQuery = useQuery({
    queryKey: ['routine-pool', step.categoryCode],
    queryFn: () => fetchGoodsList({ page: 0, size: POOL_SIZE, categoryCode: step.categoryCode }),
  });
  const pool = poolQuery.data?.content ?? [];

  const base = chainReady && anchor ? anchor.goodsNo : null;
  const gateEnabled = base !== null && poolQuery.isSuccess && pool.length > 0;
  const verdictsQuery = useQuery({
    queryKey: ['compat-verdicts', base, step.categoryCode],
    queryFn: () => fetchVerdicts(base as number, pool.map((item) => item.goodsNo)),
    enabled: gateEnabled,
    // 궁합은 부가 정보다. 재시도로 체인을 붙잡아 두느니 게이트를 생략하고 넘어간다.
    retry: false,
  });
  // 실패도 "정해진 것"으로 친다 — 게이트만 생략하고 조합은 그대로 진행한다(설계 §3.3).
  const gateSettled = !gateEnabled || verdictsQuery.isSuccess || verdictsQuery.isError;

  const composition =
    chainReady && poolQuery.isSuccess && gateSettled
      ? composeStep({
          step,
          candidates: pool,
          signals,
          prevPick: anchor,
          flowRules: rules.flowRules,
          concernRules: rules.concernRules,
          verdicts: verdictsQuery.isSuccess ? (verdictsQuery.data ?? null) : null,
        })
      : undefined;

  // 다음 단계가 쓸 앵커. 전이 규칙의 from_category_code가 중분류 7자라 픽의 7자 코드가 필요한데
  // GoodsListItem에는 카테고리가 없다(동결 계약). 단계 코드가 이미 7자면 풀이 그 접두사로 걸러
  // 온 것이므로 그대로 쓰고, **클렌징만 대분류 4자(C002)라**(steps.ts "단계 깊이 혼재") 그
  // 단계에서만 픽 상세를 한 번 더 본다. 상세는 바로 담기(PickCard)가 같은 쿼리키로 쓰는 값이라
  // 실제 추가 왕복은 캐시가 흡수한다.
  const pick = composition?.pick ?? null;
  const needsCat3 = pick !== null && step.categoryCode.length < CAT3_LENGTH;
  const detailQuery = useQuery({
    queryKey: ['goods-detail', pick?.goodsNo ?? null],
    queryFn: () => fetchGoodsDetail(pick?.goodsNo as number),
    enabled: needsCat3,
  });
  // 상세를 못 받으면 단계 코드로 폴백한다 — 전이 규칙 하나를 놓칠 뿐 체인은 멈추지 않는다.
  const cat3Settled = !needsCat3 || detailQuery.isSuccess || detailQuery.isError;
  const cat3 = detailQuery.isSuccess ? detailQuery.data.categoryCode : step.categoryCode;

  return {
    composition,
    pool,
    isError: poolQuery.isError,
    settled: chainReady && (poolQuery.isError || (composition !== undefined && cat3Settled)),
    anchor: pick
      ? { goodsNo: pick.goodsNo, cat3: toCat3(cat3), tags: pick.tags.map((tag) => tag.slug) }
      : null,
  };
}

export function useComposer(concernOverride: DerivedConcern | null = null): StepState[] {
  const accessToken = useAuthStore((state) => state.accessToken);

  // 마운트 시점 스냅샷. localStorage는 이벤트를 발생시키지 않으므로 렌더 중에 다시 읽어봐야
  // 얻을 것이 없고, 스크롤 도중 구성이 바뀌는 것은 오히려 혼란스럽다(Routine.tsx와 같은 판단).
  const [events] = useState(() => readEvents());

  const meQuery = useQuery({ queryKey: ['me'], queryFn: fetchMe, enabled: !!accessToken });
  const rulesQuery = useQuery({ queryKey: ['flow-rules'], queryFn: loadFlowRules });

  const signals: ComposerSignals = useMemo(() => {
    const profileConcerns = meQuery.data?.concerns ?? [];
    return {
      concerns: concernOverride
        ? [concernOverride]
        : effectiveConcerns(
            profileConcerns,
            (meQuery.data?.skinType ?? null) as SkinType | null,
          ),
      textures: preferredTextures(profileConcerns),
      affinity: aggregate(events),
      // 단독 대체 여부 — composer.ts가 고민 캡을 다중 고민과 동급으로 정규화하는 데 쓴다.
      concernOverride: !!concernOverride,
    };
  }, [events, meQuery.data, concernOverride]);

  // 신호가 도착하기 전에 조합하면 프로필이 붙는 순간 픽이 갈아엎어진다 — 확정을 한 번만 한다.
  // loadFlowRules는 던지지 않으므로(실패 시 EMPTY_RULES) 규칙 쿼리는 사실상 항상 성공한다.
  const signalsReady =
    (!accessToken || meQuery.isSuccess || meQuery.isError) &&
    (rulesQuery.isSuccess || rulesQuery.isError);
  const rules = rulesQuery.data ?? EMPTY_RULES;

  // 체인을 **펼쳐서** 쓴다. 훅은 루프에 넣을 수 없고(호출 순서가 고정돼야 한다), 여기서는 그
  // 제약이 오히려 설계를 그대로 드러낸다 — 각 줄의 `prev.settled`·`prev.anchor`가 곧 "앞 단계가
  // 다음 단계를 끌고 간다"는 §3의 체인이다.
  // ROUTINE_STEPS에 단계를 더하면 이 줄들도 함께 늘려야 한다(Main.test.tsx의 '루틴 5단계를 상수
  // 순서대로 렌더한다'가 어긋남을 잡는다).
  const chain = { signals, rules };
  const step1 = useStepComposition({
    ...chain,
    step: ROUTINE_STEPS[0],
    chainReady: signalsReady,
    anchor: null,
  });
  const step2 = useStepComposition({
    ...chain,
    step: ROUTINE_STEPS[1],
    chainReady: step1.settled,
    anchor: step1.anchor,
  });
  const step3 = useStepComposition({
    ...chain,
    step: ROUTINE_STEPS[2],
    chainReady: step2.settled,
    anchor: step2.anchor,
  });
  const step4 = useStepComposition({
    ...chain,
    step: ROUTINE_STEPS[3],
    chainReady: step3.settled,
    anchor: step3.anchor,
  });
  const step5 = useStepComposition({
    ...chain,
    step: ROUTINE_STEPS[4],
    chainReady: step4.settled,
    anchor: step4.anchor,
  });

  return [step1, step2, step3, step4, step5].map(({ composition, pool, isError }) => ({
    composition,
    pool,
    isError,
  }));
}
