import type { GoodsListItem } from './goods';

/**
 * 검색 결과 아이템은 GoodsListItem과 필드가 동일하므로(goodsNo, brandName, name, thumbnailUrl,
 * listPrice, salePrice, discountRate, badges, rating, reviewCount, wished, todayDreamAvailable)
 * 별도 인터페이스를 새로 정의하지 않고 GoodsListItem에 대한 별칭으로만 둔다.
 */
export type SearchResultItem = GoodsListItem;
