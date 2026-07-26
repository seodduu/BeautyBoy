import { Link } from 'react-router-dom';
import type { GoodsListItem } from '../../types/goods';
import { Badge, TodayDreamBadge } from '../ui/Badge';
import { Price } from '../ui/Price';
import { Rating } from '../ui/Rating';
import { Tag } from '../ui/Tag';
import './GoodsCard.css';

interface GoodsCardProps {
  item: GoodsListItem;
  onWishToggle: (goodsNo: number) => void;
  /**
   * 품절 신호. GoodsListItem에는 status 필드가 없어(Wave 1 범위 밖) 옵션 prop으로 둔다.
   * DESIGN.md 사양: 썸네일 opacity 0.45 + "품절" 라벨(색만으로 알리지 않는다).
   * Wave 2 이후 서버가 재고/판매상태를 내려주면 이 prop을 GoodsListItem 파생값으로 채운다.
   */
  soldOut?: boolean;
}

/**
 * DESIGN.md {goods-card} 사양의 구현.
 * 목록·검색·랭킹·추천·루틴이 전부 재사용하는 단일 상품 카드.
 * 테두리·그림자·라운딩 없음 — 카드 구분은 여백뿐.
 */
export function GoodsCard({ item, onWishToggle, soldOut = false }: GoodsCardProps) {
  const handleWishClick = (event: React.MouseEvent<HTMLButtonElement>) => {
    event.preventDefault();
    event.stopPropagation();
    onWishToggle(item.goodsNo);
  };

  // 표시 규칙(전역): 카드에는 효과(EFFECT) 태그만, 최대 2개 — 사용감(TEXTURE)은 상세에서만 보여준다.
  // 카드는 훑어보는 자리라 태그가 배지보다 눈에 띄면 안 되므로 개수를 badges보다 더 좁게 잡는다.
  const effectTags = item.tags.filter((tag) => tag.kind === 'EFFECT').slice(0, 2);

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
        aria-pressed={item.wished}
        aria-label={item.wished ? '찜 해제' : '찜하기'}
        onClick={handleWishClick}
      >
        {item.wished ? '♥' : '♡'}
      </button>
    </div>
  );
}
