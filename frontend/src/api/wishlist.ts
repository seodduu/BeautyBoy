import { api } from './client';
import { fetchGoodsDetail } from './goods';
import type { ApiEnvelope, GoodsListItem } from '../types/goods';
import type { GoodsDetail } from '../types/detail';

/** GET /wishlist 응답 한 줄. */
interface WishlistItem {
  goodsNo: number;
}

/**
 * GoodsDetail → GoodsListItem 변환. wished는 이 목록에 있다는 사실 자체로 항상 true다 —
 * 백엔드 WishlistItemResponse(backend/.../wishlist/dto/WishlistItemResponse.java)가
 * goodsNo만 내려주는 이유(T3-3 결정: 옵션 단위 주문 스냅샷은 목록 카드 표시에 안 맞음)로
 * 상세를 다시 조회해 나머지 필드를 채우지만, wished만은 상세 응답값을 신뢰하지 않고
 * 이 목록에 있다는 사실로 직접 확정한다.
 */
function toGoodsListItem(detail: GoodsDetail): GoodsListItem {
  return {
    goodsNo: detail.goodsNo,
    brandName: detail.brandName,
    name: detail.name,
    thumbnailUrl: detail.thumbnailUrl,
    listPrice: detail.listPrice,
    salePrice: detail.salePrice,
    discountRate: detail.discountRate,
    badges: detail.badges,
    rating: detail.rating,
    reviewCount: detail.reviewCount,
    wished: true,
    todayDreamAvailable: detail.todayDreamAvailable,
    tags: detail.tags,
  };
}

/**
 * GET /wishlist — 마이페이지 찜 탭.
 * 백엔드는 goodsNo만 내려주므로(WishlistItemResponse) goodsNo마다 상품 상세를 다시 조회해
 * `GoodsCard`가 요구하는 GoodsListItem으로 합성한다(T3-3 결정, WishlistItemResponse Javadoc 참고).
 */
export async function fetchWishlist(): Promise<GoodsListItem[]> {
  const response = await api.get<ApiEnvelope<WishlistItem[]>>('/wishlist');
  const details = await Promise.all(
    response.data.data.map((item) => fetchGoodsDetail(item.goodsNo)),
  );
  return details.map(toGoodsListItem);
}

/** POST /wishlist/{goodsNo} */
export async function addWish(goodsNo: number): Promise<void> {
  await api.post(`/wishlist/${goodsNo}`);
}

/** DELETE /wishlist/{goodsNo} */
export async function removeWish(goodsNo: number): Promise<void> {
  await api.delete(`/wishlist/${goodsNo}`);
}
