import { create } from 'zustand';
import { useAuthStore } from '../../stores/authStore';

interface WishState {
  /**
   * 이 세션에서 사용자가 직접 토글한 결과. 키는 goodsNo, 값은 찜 여부다.
   * 서버 응답값 위에 덧씌우는 오버레이이지 캐시가 아니다 — 여기 없는 상품은 서버값을 그대로 쓴다.
   */
  overrides: Record<number, boolean>;
  set: (goodsNo: number, wished: boolean) => void;
  reset: () => void;
}

/**
 * 찜 상태 오버레이.
 *
 * 왜 별도 저장소인가: 찜 여부(`wished`)는 목록·검색·상세·찜목록 등 **여러 쿼리 캐시에 흩어져**
 * 있고, 랭킹 응답(RankingItem)에는 필드 자체가 없다(Ranking.tsx의 `wished: false` 중립값).
 * 그래서 토글 결과를 각 캐시에 일일이 심는 대신 한 곳에 모으고, 카드가 서버값 위에 덧씌워
 * 읽는다. 서버가 여전히 진실이고 이 값은 "방금 내가 누른 것"만 담는다.
 */
export const useWishStore = create<WishState>((set) => ({
  overrides: {},
  set: (goodsNo, wished) =>
    set((state) => ({ overrides: { ...state.overrides, [goodsNo]: wished } })),
  reset: () => set({ overrides: {} }),
}));

/**
 * 로그아웃하면 오버레이를 비운다 — 안 비우면 다음 사용자의 화면에 이전 사용자가 누른 하트가
 * 켜진 채로 남는다. authStore를 고치지 않고 이 파일 안에서 구독만 건다(의존 방향: wish → auth).
 */
useAuthStore.subscribe((state, prev) => {
  if (prev.member && !state.member) {
    useWishStore.getState().reset();
  }
});

/** 서버가 준 wished 위에 이 세션의 토글 결과를 덧씌운 최종 표시값. */
export function useWishedState(goodsNo: number, serverWished: boolean): boolean {
  return useWishStore((state) => state.overrides[goodsNo] ?? serverWished);
}
