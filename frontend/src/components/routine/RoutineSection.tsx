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

  // 후보가 한 줄도 못 채우면 개인화를 버린다. 반쯤 빈 줄을 보여주느니 기준선으로 돌아가는 편이
  // 낫다 — 폴백이 곧 현재 화면이라 사용자에게는 빈 슬롯이 생기지 않는다(설계 §6.5).
  const overrideRejected =
    overrideTag !== null &&
    primaryQuery.isSuccess &&
    primaryQuery.data.content.length < ROUTINE_SECTION_SIZE;

  // 쿼리키가 기본 쿼리와 같아, 그 섹션을 기본으로 이미 받아 둔 적이 있으면 캐시 히트다.
  const fallbackQuery = useQuery({
    queryKey: ['routine-goods', step.categoryCode, null],
    queryFn: () =>
      fetchGoodsList({ page: 0, size: ROUTINE_SECTION_SIZE, categoryCode: step.categoryCode }),
    enabled: overrideRejected,
  });

  const activeQuery = overrideRejected ? fallbackQuery : primaryQuery;
  const personalized = override !== undefined && !overrideRejected;

  const candidates = activeQuery.data?.content ?? [];
  const items = (
    personalized && overrideTag ? rankByTexture(candidates, preferredTextures) : candidates
  ).slice(0, ROUTINE_SECTION_SIZE);

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

      {activeQuery.isError ? (
        <p className="bb-routine__error">상품을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</p>
      ) : (
        <GoodsGrid
          items={items}
          loading={activeQuery.isLoading}
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
