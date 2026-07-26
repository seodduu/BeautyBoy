import type { GoodsListItem, TagView } from './goods';

/**
 * 검색 결과 아이템. 나머지 필드는 GoodsListItem과 동일(goodsNo, brandName, name, thumbnailUrl,
 * listPrice, salePrice, discountRate, badges, rating, reviewCount, wished, todayDreamAvailable)하지만
 * 서버 `SearchResultItem`(backend/src/main/java/com/beautyboy/search/dto/SearchResultItem.java)에는
 * `tags` 필드가 아직 없다 — GoodsListItem을 그대로 별칭 쓰면 tags가 required로 거짓 선언돼
 * 실서버 응답에서 GoodsCard가 `item.tags.filter(...)`로 TypeError를 던진다.
 * tags를 optional로 낮춰 실 계약을 정직하게 반영한다(도메인 간 인터페이스 확장은 후속 웨이브 소관).
 */
export type SearchResultItem = Omit<GoodsListItem, 'tags'> & { tags?: TagView[] };
