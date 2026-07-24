import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { fetchGoodsDetail } from '../api/goods';
import { fetchAssessment } from '../api/assessment';
import { addCartItem } from '../api/cart';
import { Price } from '../components/ui/Price';
import { Rating } from '../components/ui/Rating';
import { Badge, TodayDreamBadge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { Skeleton } from '../components/ui/Skeleton';
import { AssessmentCard } from '../components/goods/AssessmentCard';
import { CautionPanel } from '../components/goods/CautionPanel';
import { DetailTabs } from '../components/goods/DetailTabs';
import { useToast } from '../components/ui/useToast';
import './Detail.css';

/**
 * 상세 페이지 `/goods/:goodsNo` — 읽기 전용 + "장바구니 담기" 한 버튼만 실동작.
 * 설계 6장 상세 화면: 기본 정보 헤더 → 성분 배지 → 설명/리뷰/Q&A 탭.
 */
export function Detail() {
  const { goodsNo: goodsNoParam } = useParams<{ goodsNo: string }>();
  const goodsNo = Number(goodsNoParam);
  const hasValidGoodsNo = Number.isFinite(goodsNo);
  const { toast } = useToast();
  const [adding, setAdding] = useState(false);
  const [panelOpen, setPanelOpen] = useState(false);

  const detailQuery = useQuery({
    queryKey: ['goods-detail', goodsNo],
    queryFn: () => fetchGoodsDetail(goodsNo),
    enabled: hasValidGoodsNo,
  });

  const assessmentQuery = useQuery({
    queryKey: ['goods-assessment', goodsNo],
    queryFn: () => fetchAssessment(goodsNo),
    enabled: hasValidGoodsNo,
  });

  async function handleAddToCart() {
    if (!detailQuery.data) {
      return;
    }
    // 옵션이 있으면 첫 옵션, 없으면 optionNo 없이(null) 담는다.
    const firstOption = detailQuery.data.options?.[0];
    const optionNo = firstOption ? firstOption.optionNo : null;

    setAdding(true);
    try {
      await addCartItem(goodsNo, optionNo, 1);
      toast('장바구니에 담았어요');
    } catch {
      toast('담기에 실패했어요. 다시 시도해 주세요', { tone: 'danger' });
    } finally {
      setAdding(false);
    }
  }

  if (detailQuery.isLoading) {
    return (
      <div className="bb-detail">
        <Skeleton ratio="1 / 1" />
      </div>
    );
  }

  if (detailQuery.isError || !detailQuery.data) {
    return <p className="bb-detail__error">상품 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</p>;
  }

  const goods = detailQuery.data;
  const assessment = assessmentQuery.data;

  return (
    <div className="bb-detail">
      <header className="bb-detail__header">
        <div className="bb-detail__media">
          {goods.thumbnailUrl ? (
            <img className="bb-detail__image" src={goods.thumbnailUrl} alt={goods.name} />
          ) : (
            <div className="bb-detail__image--placeholder" aria-hidden="true" />
          )}
        </div>

        <div className="bb-detail__info">
          <p className="bb-detail__brand">{goods.brandName}</p>
          <h1 className="bb-detail__name">{goods.name}</h1>
          {/* 한 줄 평 — 상품명 바로 아래, 가격보다 위. "이 제품을 쓰면 무엇이 좋은가"에 먼저 답한다. */}
          {goods.summary && <p className="bb-detail__summary">{goods.summary}</p>}
          <Rating rating={goods.rating} reviewCount={goods.reviewCount} />
          <div className="bb-detail__badges">
            {goods.badges.map((type) => (
              <Badge key={type} type={type} />
            ))}
            {goods.todayDreamAvailable && <TodayDreamBadge />}
          </div>
          <Price listPrice={goods.listPrice} salePrice={goods.salePrice} discountRate={goods.discountRate} />
          {/* 성분 종합판정 — 가격 아래, 장바구니 버튼 위(설계 §0.4). */}
          {assessment && (
            <AssessmentCard assessment={assessment} onOpenPanel={() => setPanelOpen(true)} />
          )}
          <Button
            className="bb-detail__cta"
            variant="primary"
            loading={adding}
            onClick={handleAddToCart}
          >
            장바구니 담기
          </Button>
        </div>
      </header>

      <DetailTabs goodsNo={goodsNo} />

      {assessment && (
        <p className="bb-detail__disclaimer">
          이 정보는 성분 표시와 식약처 공개자료를 근거로 한 안내이며, 목록에 없다고 자극이 없다는 뜻은
          아니고 의학적 판단이 아닙니다.
        </p>
      )}

      {assessment && (
        <CautionPanel
          open={panelOpen}
          flagged={assessment.flagged}
          onClose={() => setPanelOpen(false)}
        />
      )}
    </div>
  );
}
