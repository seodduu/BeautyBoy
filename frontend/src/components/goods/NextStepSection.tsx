import { useQuery } from '@tanstack/react-query';
import { fetchNextStep } from '../../api/goods';
import { GoodsGrid } from './GoodsGrid';
import './NextStepSection.css';

interface NextStepSectionProps {
  goodsNo: number;
}

/**
 * "다음 단계" — GET /goods/:goodsNo/next-step.
 * 서버가 전이 규칙 적용·폴백·궁합 게이트까지 끝낸 결과를 받는다.
 * reason(이유 문장)은 routine_flow_rule.reason 원문이 유일한 출처 — 여기서 문구를 만들지 않는다.
 * blocks가 비면 사용자가 뭔가 해야 하는 상태가 아니므로 섹션 자체를 렌더하지 않는다
 * (RecommendedSection과 동일 원칙).
 */
export function NextStepSection({ goodsNo }: NextStepSectionProps) {
  const nextStepQuery = useQuery({
    queryKey: ['goods-next-step', goodsNo],
    queryFn: () => fetchNextStep(goodsNo),
  });

  if (nextStepQuery.isLoading) {
    return (
      <section className="bb-next-step">
        <h2 className="bb-next-step__title">다음 단계</h2>
        <GoodsGrid items={[]} loading skeletonCount={4} />
      </section>
    );
  }

  const blocks = nextStepQuery.data ?? [];

  if (blocks.length === 0) {
    return null;
  }

  return (
    <section className="bb-next-step">
      <h2 className="bb-next-step__title">다음 단계</h2>
      {blocks.map((block, index) => (
        <div className="bb-next-step__block" key={`${block.edgeKind}-${index}`}>
          <p className="bb-next-step__reason">{block.reason}</p>
          <GoodsGrid items={block.items} />
        </div>
      ))}
    </section>
  );
}
