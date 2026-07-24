/**
 * GET /rankings 응답의 개별 랭킹 아이템.
 * 계획 스케치는 PageResponse<RankingItem>을 가정했지만 실제 API는 바로 List<RankingItem>을
 * 반환하므로(랭킹은 페이지네이션 대상이 아님) 이 타입만 두고 PageResponse는 쓰지 않는다.
 */
export interface RankingItem {
  rank: number;
  goodsNo: number;
  brandName: string;
  name: string;
  thumbnailUrl: string;
  listPrice: number;
  salePrice: number;
  discountRate: number;
  score: number;
}
