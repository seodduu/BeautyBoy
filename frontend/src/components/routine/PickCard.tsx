import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { fetchGoodsDetail } from '../../api/goods';
import { addCartItem } from '../../api/cart';
import { Button } from '../ui/Button';
import { Price } from '../ui/Price';
import { Tag } from '../ui/Tag';
import { useToast } from '../ui/useToast';
import type { GoodsDetail } from '../../types/detail';
import type { GoodsListItem } from '../../types/goods';
import './PickCard.css';

/** 근거 칩 상한. 넘치면 카드가 태그 나열로 읽히고 "왜 이것인가"가 흐려진다(DESIGN.md). */
const MAX_CHIPS = 4;

/**
 * 담을 옵션을 고른다 — **재고 있는 첫 옵션**(설계 §4.2).
 * `null` = 담을 수 없음(전 옵션 품절). 옵션이 아예 없는 상품은 `{ optionNo: null }`로 담는다
 * (Detail.tsx와 같은 규칙 — 옵션 없는 상품의 장바구니 계약이 그렇다).
 */
export function pickOption(detail: GoodsDetail): { optionNo: number | null } | null {
  if (detail.options.length === 0) {
    return { optionNo: null };
  }
  const inStock = detail.options.find((option) => !option.soldOut && option.stock > 0);
  return inStock ? { optionNo: inStock.optionNo } : null;
}

/**
 * 픽 하나를 장바구니에 담는다. 상세는 Detail.tsx와 같은 쿼리키를 쓰므로 이미 본 상품이면
 * 왕복이 0이다(설계 §4.2 "캐시되면 0회").
 *
 * 실패하면 던진다 — 개별 담기는 토스트로, 전체 담기는 집계로 각자 다르게 다뤄야 하기 때문에
 * 여기서 삼키지 않는다.
 */
export async function addPickToCart(queryClient: QueryClient, goodsNo: number): Promise<void> {
  const detail = await queryClient.fetchQuery({
    queryKey: ['goods-detail', goodsNo],
    queryFn: () => fetchGoodsDetail(goodsNo),
  });
  const option = pickOption(detail);
  if (!option) {
    throw new Error(`SOLD_OUT:${goodsNo}`);
  }
  await addCartItem(goodsNo, option.optionNo, 1);
}

interface PickCardProps {
  pick: GoodsListItem;
  /** 발동 규칙의 원문. null이면 문장 줄 자체를 렌더하지 않는다 — 빈 자리를 문구로 메우지 않는다. */
  reason: string | null;
  /** 점수에 실제 기여한 태그 슬러그(설계 §5). 칩은 문구가 아니라 태그 데이터다. */
  matched: { concerns: string[]; behaviors: string[] };
}

/**
 * DESIGN.md `{pick-card}` 사양의 구현 — 단계별 대표 픽 한 장.
 *
 * 이 카드가 v2의 전부다: "이 단계에서 당신에게는 이것"을 **이유와 함께** 내고, 옵션 선택 없이
 * 바로 담게 한다. 대안 3개는 아래 그리드가 기존 `{goods-card}` 그대로 그린다.
 */
export function PickCard({ pick, reason, matched }: PickCardProps) {
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const [adding, setAdding] = useState(false);

  // 재고 판정용. 클릭 시점에는 이 캐시를 그대로 재사용한다(addPickToCart의 fetchQuery).
  const detailQuery = useQuery({
    queryKey: ['goods-detail', pick.goodsNo],
    queryFn: () => fetchGoodsDetail(pick.goodsNo),
  });
  // 상세를 아직 못 받았으면 품절이라고 단정하지 않는다 — 담기를 막는 것은 "품절임을 아는" 경우뿐이다.
  const soldOut = detailQuery.isSuccess && pickOption(detailQuery.data) === null;

  // 근거 칩 — 점수에 기여한 슬러그만 상품 태그에서 되찾아 pill로 낸다. 고민 일치가 먼저다.
  const chipSlugs = [...new Set([...matched.concerns, ...matched.behaviors])];
  const chips = chipSlugs
    .map((slug) => pick.tags.find((tag) => tag.slug === slug))
    .filter((tag): tag is NonNullable<typeof tag> => tag !== undefined)
    .slice(0, MAX_CHIPS);

  async function handleAddToCart() {
    setAdding(true);
    try {
      await addPickToCart(queryClient, pick.goodsNo);
      // Header.tsx의 ['cart'] 쿼리(장바구니 배지)를 무효화해 새로고침 없이 갱신되게 한다
      // (Detail.tsx와 같은 패턴).
      queryClient.invalidateQueries({ queryKey: ['cart'] });
      toast('담았어요 — 옵션 변경은 장바구니에서');
    } catch {
      toast('담기에 실패했어요. 다시 시도해 주세요', { tone: 'danger' });
    } finally {
      setAdding(false);
    }
  }

  return (
    <article className="bb-pick">
      <Link className="bb-pick__media" to={`/goods/${pick.goodsNo}`} tabIndex={-1} aria-hidden="true">
        <img className="bb-pick__image" src={pick.thumbnailUrl} alt="" loading="lazy" />
      </Link>

      <div className="bb-pick__info">
        <p className="bb-pick__eyebrow">TODAY&apos;S PICK</p>
        <p className="bb-pick__brand">{pick.brandName}</p>
        <h3 className="bb-pick__name">
          <Link className="bb-pick__name-link" to={`/goods/${pick.goodsNo}`}>
            {pick.name}
          </Link>
        </h3>

        {/* 문구의 출처는 규칙 테이블뿐이다 — 없으면 줄을 없앤다. */}
        {reason && <p className="bb-pick__reason">{reason}</p>}

        {chips.length > 0 && (
          <div className="bb-pick__chips" data-testid="pick-card-chips" aria-label="추천 근거 태그">
            {chips.map((tag) => (
              <Tag key={tag.slug} view={tag} />
            ))}
          </div>
        )}

        <Price
          listPrice={pick.listPrice}
          salePrice={pick.salePrice}
          discountRate={pick.discountRate}
        />

        <Button
          className="bb-pick__cta"
          variant="primary"
          loading={adding}
          disabled={soldOut}
          onClick={handleAddToCart}
        >
          바로 담기
        </Button>
        {/* 색·비활성만으로 알리지 않는다(DESIGN.md 품절 규칙). */}
        {soldOut && <p className="bb-pick__soldout">일시품절</p>}
      </div>
    </article>
  );
}
