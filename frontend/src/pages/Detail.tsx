import { useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchGoodsDetail } from '../api/goods';
import { fetchAssessment } from '../api/assessment';
import { addCartItem } from '../api/cart';
import { Price } from '../components/ui/Price';
import { Rating } from '../components/ui/Rating';
import { Badge, TodayDreamBadge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { Skeleton } from '../components/ui/Skeleton';
import { Tag } from '../components/ui/Tag';
import { AssessmentCard } from '../components/goods/AssessmentCard';
import { CautionPanel } from '../components/goods/CautionPanel';
import { DetailTabs } from '../components/goods/DetailTabs';
import { OptionSelector } from '../components/goods/OptionSelector';
import { NextStepSection } from '../components/goods/NextStepSection';
import { QuantityStepper } from '../components/goods/QuantityStepper';
import { RecommendedSection } from '../components/goods/RecommendedSection';
import { useToast } from '../components/ui/useToast';
import { WEIGHT, recordEvent, toCat3 } from '../features/affinity/events';
import './Detail.css';

// 태그 표시 정렬 순서(태그확장) — 렌더마다 새로 만들 필요가 없는 고정 상수라 모듈 스코프로 뺀다.
const TAG_KIND_ORDER: Record<string, number> = { EFFECT: 0, PROPERTY: 1, TEXTURE: 2 };

/**
 * 상세 페이지 `/goods/:goodsNo` — 읽기 전용 + "장바구니 담기" 한 버튼만 실동작.
 * 설계 6장 상세 화면: 기본 정보 헤더 → 성분 배지 → 설명/리뷰/Q&A 탭.
 */
export function Detail() {
  const { goodsNo: goodsNoParam } = useParams<{ goodsNo: string }>();
  const goodsNo = Number(goodsNoParam);
  const hasValidGoodsNo = Number.isFinite(goodsNo);
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const [adding, setAdding] = useState(false);
  const [panelOpen, setPanelOpen] = useState(false);
  const [selectedOptionNo, setSelectedOptionNo] = useState<number | null>(null);
  const [quantity, setQuantity] = useState(1);

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

  // 조회 이벤트(1점) — PDP가 실제로 그려졌을 때 한 번만. 주 신호가 이것이다(설계 §6.1).
  // StrictMode의 이중 마운트와 리렌더가 같은 상품을 여러 번 세지 않도록 goodsNo로 가드한다.
  const viewedGoodsNoRef = useRef<number | null>(null);
  useEffect(() => {
    const goods = detailQuery.data;
    if (!goods || viewedGoodsNoRef.current === goods.goodsNo) {
      return;
    }
    viewedGoodsNoRef.current = goods.goodsNo;
    recordEvent({
      goodsNo: goods.goodsNo,
      cat3: toCat3(goods.categoryCode),
      tags: goods.tags.map((tag) => tag.slug),
      w: WEIGHT.view,
    });
  }, [detailQuery.data]);

  const options = detailQuery.data?.options ?? [];
  // 옵션이 하나뿐이면 고를 것이 없다 — 선택을 강요하면 클릭만 늘어나므로 자동 선택한다.
  // 옵션이 0개(없음)이거나 2개 이상이면 사용자가 고르기 전까지는 미선택 상태를 유지한다.
  const autoOptionNo = options.length === 1 ? options[0].optionNo : null;
  const effectiveOptionNo = selectedOptionNo ?? autoOptionNo;
  const selectedOption = options.find((option) => option.optionNo === effectiveOptionNo) ?? null;
  // 옵션이 있는데(2개 이상) 아직 아무것도 안 골랐으면 담기를 막는다. 0개거나 자동선택된 경우는 통과.
  const optionRequired = options.length > 0 && effectiveOptionNo === null;

  async function handleAddToCart() {
    if (!detailQuery.data || optionRequired) {
      return;
    }

    setAdding(true);
    try {
      await addCartItem(goodsNo, effectiveOptionNo, quantity);
      // 담기 이벤트(3점) — 성공한 뒤에만 기록한다. 실패한 담기는 구매 의도의 증거가 아니다.
      recordEvent({
        goodsNo,
        cat3: toCat3(detailQuery.data.categoryCode),
        tags: detailQuery.data.tags.map((tag) => tag.slug),
        w: WEIGHT.cart,
      });
      // Header.tsx의 ['cart'] 쿼리(장바구니 배지)를 무효화해, 새로고침 없이 즉시 갱신되게 한다
      // (Routine.tsx:94와 같은 패턴).
      queryClient.invalidateQueries({ queryKey: ['cart'] });
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
  // 표시 가격 규칙: salePrice + selectedOption.addPrice. 금액의 최종 진실은 여전히 서버(주문 시 재계산)이며
  // 이 값은 화면 안내용이다.
  const displaySalePrice = goods.salePrice + (selectedOption?.addPrice ?? 0);
  // 표시 규칙(태그확장): 상세는 전부 표시한다(최대 개수 제한 없음, flex-wrap으로 줄바꿈).
  // 정렬만 EFFECT → PROPERTY → TEXTURE 순으로 하고, 동순위 안에서는 백엔드가 내려준 원래 순서를 지킨다.
  const displayTags = [...goods.tags].sort(
    (a, b) => (TAG_KIND_ORDER[a.kind] ?? 99) - (TAG_KIND_ORDER[b.kind] ?? 99),
  );

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
          {displayTags.length > 0 && (
            <div className="bb-detail__tags">
              {displayTags.map((tag) => (
                <Tag key={tag.slug} view={tag} to={`/goods?tag=${tag.slug}`} />
              ))}
            </div>
          )}
          <Rating rating={goods.rating} reviewCount={goods.reviewCount} />
          <div className="bb-detail__badges">
            {goods.badges.map((type) => (
              <Badge key={type} type={type} />
            ))}
            {goods.todayDreamAvailable && <TodayDreamBadge />}
          </div>
          <div data-testid="detail-price">
            <Price listPrice={goods.listPrice} salePrice={displaySalePrice} discountRate={goods.discountRate} />
          </div>
          {/* 성분 종합판정 — 가격 아래, 장바구니 버튼 위(설계 §0.4). */}
          {assessment && (
            <AssessmentCard assessment={assessment} onOpenPanel={() => setPanelOpen(true)} />
          )}
          {options.length > 1 && (
            <OptionSelector
              options={options}
              selectedOptionNo={effectiveOptionNo}
              onSelect={(optionNo) => {
                setSelectedOptionNo(optionNo);
                setQuantity(1);
              }}
            />
          )}
          <QuantityStepper
            quantity={quantity}
            max={selectedOption?.stock ?? 1}
            onChange={setQuantity}
          />
          <Button
            className="bb-detail__cta"
            variant="primary"
            loading={adding}
            disabled={optionRequired}
            aria-describedby={optionRequired ? 'detail-option-hint' : undefined}
            onClick={handleAddToCart}
          >
            장바구니 담기
          </Button>
          {/* 버튼이 왜 비활성인지 알려준다 — 자동 선택 확대는 하지 않는다(§2 결정 6: 가격 혼란). */}
          {optionRequired && (
            <p id="detail-option-hint" className="bb-detail__option-hint">
              옵션을 선택해주세요
            </p>
          )}
        </div>
      </header>

      <DetailTabs goodsNo={goodsNo} />

      <NextStepSection goodsNo={goodsNo} />

      <RecommendedSection goodsNo={goodsNo} />

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
