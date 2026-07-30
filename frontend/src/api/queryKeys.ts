/**
 * react-query 키를 만드는 유일한 곳. 화면이 키 배열을 직접 적지 않는다.
 *
 * 이전에는 ['cart']가 8개 파일에 하드코딩돼 있었고 "Cart.tsx와 같은 키를 써라"라는 주석으로
 * 정합성을 지켰다. 주석은 안 읽은 사람이 깨므로, 무효화하는 쪽과 조회하는 쪽이 같은 함수를
 * 부르게 해서 오타 클래스를 구조적으로 없앤다.
 */
export const queryKeys = {
  cart: () => ['cart'] as const,
  wishlist: () => ['wishlist'] as const,
  myReviews: () => ['myReviews'] as const,

  /**
   * 성분 궁합. 상품 번호 집합이 같으면 **고른 순서가 달라도 같은 캐시 엔트리**여야 한다 —
   * 궁합 판정은 조합의 함수이지 순서의 함수가 아니다. 중복제거 후 정렬해서 그 성질을 키에 넣는다.
   * (이전 Cart.tsx·Routine.tsx는 goodsNos.join(',')을 그대로 써서 1→2와 2→1이 서로 다른
   *  엔트리가 됐고, 같은 조합에 대해 서버를 두 번 불렀다.)
   */
  compat: (goodsNos: readonly number[]) =>
    ['compat', [...new Set(goodsNos)].sort((a, b) => a - b).join(',')] as const,
} as const;
