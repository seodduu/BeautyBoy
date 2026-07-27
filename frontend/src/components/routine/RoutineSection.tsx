import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { fetchGoodsList } from '../../api/goods';
import { GoodsGrid } from '../goods/GoodsGrid';
import { rankByTexture } from '../../features/affinity/match';
import { ROUTINE_SECTION_SIZE, type RoutineStep } from '../../features/routine/steps';
import './RoutineSection.css';

/**
 * 개인화된 섹션이 받아 오는 후보 수. 4개를 그리는데 8개를 받는 이유는 사용감 tie-break에 쓸
 * 여유분이다 — 4개만 받으면 정렬해도 결과가 같아 tie-break가 무의미해진다(설계 §6.5).
 */
const PERSONALIZED_FETCH_SIZE = 8;

interface RoutineSectionProps {
  step: RoutineStep;
  /** 0-based 순서. 짝수면 타이포가 왼쪽, 홀수면 오른쪽 — 스크롤에 리듬을 준다. */
  index: number;
  /**
   * 개인화 목표. Main이 티어별 매칭 결과에서 이 단계 몫만 골라 내려준다.
   * `tag`가 null인 규칙도 있다(routine_flow_rule의 to_tag_slug는 NULL 허용) — 그때는 목록이
   * 기본 쿼리 그대로이고 이유 문장만 붙는다.
   */
  override?: { tag: string | null; reason: string };
  /** 프로필의 선호 사용감 슬러그. 개인화된 섹션의 후보 tie-break에만 쓴다. */
  preferredTextures?: string[];
}

/**
 * 루틴 한 단계를 그리는 섹션.
 *
 * 시각 형식은 pangram 레퍼런스의 "거대 타이포 ↔ 면으로 나뉜 카드" 대비를 따른다:
 * 큰 타이포 블록과 이미지 박스(surface + rounded.lg)가 나란히 서고, 아래에 상품 4개가 붙는다.
 * 섹션이 자기 데이터를 직접 가져오므로(부모가 5개를 모아 내려주지 않는다) 단계를 추가·제거해도
 * Main은 상수 배열만 map하면 된다.
 *
 * 개인화(override)가 붙어도 컴포넌트 구조는 그대로다 — 쿼리에 tag가 붙고 제목 아래 이유 문장
 * 한 줄이 늘어날 뿐이다(설계 §7).
 */
export function RoutineSection({
  step,
  index,
  override,
  preferredTextures = [],
}: RoutineSectionProps) {
  // tag가 있을 때만 다른 쿼리다. tag가 null이면 키·size가 기본 쿼리와 완전히 같아져
  // 이유 문장만 얹은 형태가 되고 캐시도 기본 쿼리와 한 칸을 공유한다.
  const overrideTag = override?.tag ?? null;

  const primaryQuery = useQuery({
    queryKey: ['routine-goods', step.categoryCode, overrideTag],
    queryFn: () =>
      fetchGoodsList({
        page: 0,
        size: overrideTag ? PERSONALIZED_FETCH_SIZE : ROUTINE_SECTION_SIZE,
        categoryCode: step.categoryCode,
        ...(overrideTag ? { tag: overrideTag } : {}),
      }),
  });

  const primaryItems = primaryQuery.data?.content ?? [];

  // "태그 일치분"이라는 개념은 tag가 붙은 경우에만 있다. tag가 null이면 primaryItems가 곧
  // 기본 목록이라 채울 것도, 앞으로 당길 것도 없다.
  const taggedItems = overrideTag ? primaryItems : [];

  // 태그 일치분이 한 줄을 못 채우면 같은 카테고리 인기순으로 뒤를 채운다(설계 §5).
  //
  // 예전에는 4개 미만이면 개인화를 통째로 버렸는데, 그 all-or-nothing 규칙이 "모든
  // (카테고리 × 태그) 칸에 4개 이상"을 태그 설계의 제약으로 만들었다. 상품 190개짜리
  // 카탈로그에서 그 제약과 "태그가 좁다"는 직접 충돌한다(태그 희석화 설계 §2.1).
  // 채워 넣으면 후보가 1개뿐인 목표도 "1개는 겨냥, 3개는 인기"로 살아남는다.
  const needsFill =
    overrideTag !== null && primaryQuery.isSuccess && taggedItems.length < ROUTINE_SECTION_SIZE;

  // 쿼리키가 기본 쿼리와 같아, 그 섹션을 기본으로 이미 받아 둔 적이 있으면 캐시 히트다.
  const fillQuery = useQuery({
    queryKey: ['routine-goods', step.categoryCode, null],
    queryFn: () =>
      fetchGoodsList({ page: 0, size: ROUTINE_SECTION_SIZE, categoryCode: step.categoryCode }),
    enabled: needsFill,
  });

  // 태그 일치분이 하나도 없으면 개인화라고 부를 근거가 없다 — 이유 문장도 감춘다.
  // 채움만으로 채워진 줄에 "각질 케어를 많이 보셨네요"가 붙으면 그건 거짓말이 된다.
  const overrideRejected =
    overrideTag !== null && primaryQuery.isSuccess && taggedItems.length === 0;
  const personalized = override !== undefined && !overrideRejected;

  // 태그 일치분을 사용감 tie-break로 정렬해 앞에 세우고, 모자란 만큼만 인기순으로 잇는다.
  // 앞자리를 태그 일치분이 차지하는 것이 개인화가 눈에 보이는 유일한 이유다 — 필터만으로는
  // 인기순이 지배해 상위 4개가 필터 없을 때와 대부분 겹친다(태그 희석화 설계 §2).
  const ranked = overrideTag ? rankByTexture(taggedItems, preferredTextures) : primaryItems;
  const alreadyShown = new Set(ranked.map((item) => item.goodsNo));
  const items = [
    ...ranked,
    ...(fillQuery.data?.content ?? []).filter((item) => !alreadyShown.has(item.goodsNo)),
  ].slice(0, ROUTINE_SECTION_SIZE);

  const orderLabel = String(step.order).padStart(2, '0');
  const sideClass = index % 2 === 0 ? 'bb-routine--text-left' : 'bb-routine--text-right';

  return (
    <section id={step.id} className={`bb-routine ${sideClass}`} aria-labelledby={`${step.id}-title`}>
      <div className="bb-routine__head">
        <div className="bb-routine__text">
          <p className="bb-routine__step">STEP {orderLabel}</p>
          <h2 id={`${step.id}-title`} className="bb-routine__title">
            {step.label}
          </h2>
          {/* 개인화 이유 한 줄. 문구는 규칙 테이블이 유일한 출처다 — 여기서 만들지 않는다. */}
          {personalized && <p className="bb-routine__reason">{override.reason}</p>}
          <p className="bb-routine__copy">{step.copy}</p>
        </div>

        <div className="bb-routine__figure">
          {/* 장식이 아니라 단계를 식별하는 이미지라 alt를 비우지 않는다. */}
          <img className="bb-routine__image" src={step.image} alt={`${step.label} 단계 이미지`} />
        </div>
      </div>

      {/* 채움 쿼리가 실패해도 에러로 처리하지 않는다 — 태그 일치분은 이미 손에 있고,
          한 줄이 덜 찬 것보다 "불러오지 못했어요"가 더 나쁘다. */}
      {primaryQuery.isError ? (
        <p className="bb-routine__error">상품을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</p>
      ) : (
        <GoodsGrid
          items={items}
          loading={primaryQuery.isLoading || (needsFill && fillQuery.isLoading)}
          skeletonCount={ROUTINE_SECTION_SIZE}
          categoryCode={step.categoryCode}
        />
      )}

      <p className="bb-routine__more">
        <Link
          className="bb-routine__more-link"
          to={`/goods?category=${encodeURIComponent(step.categoryCode)}`}
        >
          {step.label} 전체 보기
          <span aria-hidden="true"> →</span>
        </Link>
      </p>
    </section>
  );
}
