import { Link } from 'react-router-dom';
import type { GoodsListItem } from '../../types/goods';
import { Badge, TodayDreamBadge } from '../ui/Badge';
import { Price } from '../ui/Price';
import { Rating } from '../ui/Rating';
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

        {item.badges.length > 0 || item.todayDreamAvailable ? (
          <div className="bb-goods-card__badges">
            {item.badges.map((badge) => (
              <Badge key={badge} type={badge} />
            ))}
            {item.todayDreamAvailable && <TodayDreamBadge />}
          </div>
        ) : null}

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
