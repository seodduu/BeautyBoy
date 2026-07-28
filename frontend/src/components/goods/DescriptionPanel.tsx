import { useQuery } from '@tanstack/react-query';
import { fetchGoodsDescription } from '../../api/goods';
import { EmptyState } from '../common/EmptyState';
import { ErrorState } from '../common/ErrorState';
import { Skeleton } from '../ui/Skeleton';

interface DescriptionPanelProps {
  goodsNo: number;
  /** 설명 탭이 활성 상태일 때만 true — 비활성 탭에서는 fetch를 막는다(enabled). */
  active: boolean;
}

/**
 * 설명 탭 — GET /goods/:goodsNo/description 지연 로딩.
 * summary는 첫 화면의 한 줄 평이 쓰므로 본문은 이 엔드포인트에서만 가져온다.
 */
export function DescriptionPanel({ goodsNo, active }: DescriptionPanelProps) {
  const descriptionQuery = useQuery({
    queryKey: ['goods-description', goodsNo],
    queryFn: () => fetchGoodsDescription(goodsNo),
    enabled: active,
  });

  if (!active) {
    return null;
  }

  if (descriptionQuery.isLoading) {
    return (
      <div aria-hidden="true">
        <Skeleton ratio="6 / 1" />
      </div>
    );
  }

  if (descriptionQuery.isError) {
    return <ErrorState title="상품 설명을 불러오지 못했어요" onRetry={() => descriptionQuery.refetch()} />;
  }

  const description = descriptionQuery.data?.description?.trim() ?? '';

  if (!description) {
    return <EmptyState title="아직 등록된 상품 설명이 없어요" />;
  }

  return <p className="bb-detail-tabs__summary">{description}</p>;
}
