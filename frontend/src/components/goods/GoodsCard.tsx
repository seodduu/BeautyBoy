import { Link } from 'react-router-dom';
import type { SearchResultItem } from '../../types/search';
import { Badge, TodayDreamBadge } from '../ui/Badge';
import { Price } from '../ui/Price';
import { Rating } from '../ui/Rating';
import { Tag } from '../ui/Tag';
import { WEIGHT, recordEvent, toCat3 } from '../../features/affinity/events';
import { useWishedState } from '../../features/wishlist/wishStore';
import './GoodsCard.css';

interface GoodsCardProps {
  /**
   * SearchResultItem(tags optional)을 받는다 — 실서버 검색 결과는 tags 필드 자체가 없을 수 있다.
   * GoodsListItem(tags required)도 구조적으로 호환되므로 목록·랭킹·추천 등 다른 화면은 그대로 넘긴다.
   */
  item: SearchResultItem;
  /**
   * 하트를 누를 때 호출된다. 두 번째 인자는 **누르기 직전의 찜 여부**로, 받는 쪽이
   * 찜(POST)인지 해제(DELETE)인지 판단할 근거다 — 카드가 그 판단까지 하지는 않는다.
   */
  onWishToggle: (goodsNo: number, wished: boolean) => void;
  /**
   * 품절 신호. GoodsListItem에는 status 필드가 없어(Wave 1 범위 밖) 옵션 prop으로 둔다.
   * DESIGN.md 사양: 썸네일 opacity 0.45 + "품절" 라벨(색만으로 알리지 않는다).
   * Wave 2 이후 서버가 재고/판매상태를 내려주면 이 prop을 GoodsListItem 파생값으로 채운다.
   */
  soldOut?: boolean;
  /**
   * 이 카드가 놓인 화면 문맥의 카테고리 코드. 찜을 관심 이벤트로 기록할 때만 쓴다.
   *
   * GoodsListItem에는 categoryCode가 없고 **이 타입은 동결 계약이라 필드를 추가하지 않는다.**
   * 대신 카테고리를 아는 화면(루틴 섹션=step.categoryCode, 목록=필터 카테고리)만 문맥으로
   * 넘긴다. 검색 결과·추천 레일처럼 문맥이 없는 곳의 찜은 기록하지 않는다 — 태그만으로 기록하면
   * "각질 클렌징"과 "각질 토너"를 구분할 수 없어 단계 축이 무너진다(설계 §6.1).
   */
  categoryCode?: string;
}

/**
 * DESIGN.md {goods-card} 사양의 구현.
 * 목록·검색·랭킹·추천·루틴이 전부 재사용하는 단일 상품 카드.
 * 테두리·그림자·라운딩 없음 — 카드 구분은 여백뿐.
 */
export function GoodsCard({
  item,
  onWishToggle,
  soldOut = false,
  categoryCode,
}: GoodsCardProps) {
  // 서버가 준 item.wished 위에 이 세션의 토글 결과를 덧씌운 값이 화면의 진실이다 —
  // 목록을 다시 받아오기 전에도 방금 누른 하트가 켜져 있어야 한다.
  const wished = useWishedState(item.goodsNo, item.wished);

  const handleWishClick = (event: React.MouseEvent<HTMLButtonElement>) => {
    event.preventDefault();
    event.stopPropagation();
    // 찜은 관심 신호(2점)다. 카테고리 문맥이 있을 때만 기록한다 — 없으면 조용히 건너뛴다.
    // 찜 해제도 그대로 기록한다: "이 상품을 두 번 건드렸다"는 관심의 신호라는 점은 변하지 않고,
    // 해제까지 되돌리려면 이벤트 로그가 아니라 상태 저장소가 되어 링버퍼 설계가 무너진다.
    if (categoryCode) {
      recordEvent({
        goodsNo: item.goodsNo,
        cat3: toCat3(categoryCode),
        tags: (item.tags ?? []).map((tag) => tag.slug),
        w: WEIGHT.wish,
      });
    }
    onWishToggle(item.goodsNo, wished);
  };

  // 표시 규칙(전역): 카드에는 효과(EFFECT) 태그만, 최대 2개 — 사용감(TEXTURE)은 상세에서만 보여준다.
  // 카드는 훑어보는 자리라 태그가 배지보다 눈에 띄면 안 되므로 개수를 badges보다 더 좁게 잡는다.
  // 실서버 검색 결과는 tags 필드 자체가 없을 수 있다(SearchResultItem.tags는 optional) — 방어적으로 처리.
  const effectTags = (item.tags ?? []).filter((tag) => tag.kind === 'EFFECT').slice(0, 2);

  return (
    <div className="bb-goods-card">
      <Link to={`/goods/${item.goodsNo}`} className="bb-goods-card__link">
        <div className="bb-goods-card__thumbnail-wrap">
          <img
            src={item.thumbnailUrl}
            alt={item.name}
            loading="lazy"
            className={`bb-goods-card__thumbnail${soldOut ? ' bb-goods-card__thumbnail--sold-out' : ''}`}
            onError={(event) => {
              event.currentTarget.classList.add('bb-goods-card__thumbnail--fallback');
              event.currentTarget.removeAttribute('src');
            }}
          />
          {soldOut && <span className="bb-goods-card__sold-out-label">품절</span>}
        </div>

        {/* 배지가 없어도 줄 자체는 항상 렌더한다 — 빠지면 그 카드만 아래 내용이 위로 당겨져
            한 줄에 늘어선 카드들의 가격·평점이 어긋난다(높이는 CSS min-height가 잡는다). */}
        <div className="bb-goods-card__badges">
          {item.badges.map((badge) => (
            <Badge key={badge} type={badge} />
          ))}
          {item.todayDreamAvailable && <TodayDreamBadge />}
        </div>

        {effectTags.length > 0 && (
          <div className="bb-goods-card__tags">
            {/* 카드 전체가 이미 상세로 가는 <Link>다 — Tag에 to를 주면 <a> 안에 <a>가 중첩돼
                무효한 HTML이 되고 클릭 타깃이 꼬인다. 카드의 태그는 표시 전용으로 두고,
                태그 클릭 필터는 중첩 걱정이 없는 상세 화면(Detail.tsx)에서만 제공한다. */}
            {effectTags.map((tag) => (
              <Tag key={tag.slug} view={tag} />
            ))}
          </div>
        )}

        <span className="bb-goods-card__brand">{item.brandName}</span>
        <span className="bb-goods-card__name">{item.name}</span>
        <Price
          listPrice={item.listPrice}
          salePrice={item.salePrice}
          discountRate={item.discountRate}
        />
        <div className="bb-goods-card__rating-row">
          <Rating rating={item.rating} reviewCount={item.reviewCount} />
        </div>
      </Link>

      <button
        type="button"
        className="bb-goods-card__wish"
        aria-pressed={wished}
        aria-label={wished ? '찜 해제' : '찜하기'}
        onClick={handleWishClick}
      >
        {wished ? '♥' : '♡'}
      </button>
    </div>
  );
}
