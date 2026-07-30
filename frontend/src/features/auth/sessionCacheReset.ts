import type { QueryClient } from '@tanstack/react-query';
import { useAuthStore } from '../../stores/authStore';

/**
 * 세션 경계(로그아웃·계정 전환·부트스트랩 복구)에서 회원 스코프 react-query 캐시를 버린다.
 *
 * 배경(설계 §6.4~§6.6): `['cart']`를 비롯한 회원 스코프 쿼리 키는 회원 ID를 포함하지
 * 않는 고정 키다. 로그아웃/로그인 시 캐시를 지우는 코드가 프론트 전체에 한 곳도 없어서,
 * 직전 세션(다른 계정 또는 끊긴 세션)의 응답이 gcTime(5분) 동안 캐시에 남아 있다가 새
 * 로그인 직후 그대로 렌더될 수 있다 — 관측된 "장바구니 3→0" 배지가 바로 이 모양이다.
 *
 * 세션 소멸/전환 진입점은 Header 로그아웃, client.ts의 401 경로 3곳, App.tsx 부트스트랩
 * 실패까지 5곳이다. 그 호출부마다 `queryClient.clear()`를 심으면 여섯 번째 경로가 생기는
 * 날 조용히 깨진다. 그래서 호출부가 아니라 `useAuthStore`의 `member?.id` 변화를 구독해
 * 한 곳에서만 판단한다.
 */
export function installSessionCacheReset(queryClient: QueryClient): () => void {
  return useAuthStore.subscribe((state, prevState) => {
    const prevId = prevState.member?.id ?? null;
    const nextId = state.member?.id ?? null;

    // 직전 id가 non-null이고 지금 값과 다를 때만 비운다.
    // - null → id (최초 로그인·부트스트랩 복구): 버릴 회원 데이터가 애초에 없다.
    //   회원 스코프 쿼리는 전부 enabled: !!member라 member가 null인 동안 실행되지
    //   않기 때문이다. 이 조건이 없으면 로그인마다 공개 캐시(메인·랭킹·목록)까지
    //   통째로 버려 불필요한 재요청이 난다.
    // - id → 같은 id (토큰 갱신): 같은 회원이므로 비울 이유가 없다.
    if (prevId !== null && prevId !== nextId) {
      // 키를 골라 지우지 않고 clear()를 쓴다: "회원 스코프 키 목록"을 유지하면
      // 다음에 추가되는 회원 전용 쿼리를 그 목록에 넣는 것을 누군가 잊는다.
      // 세션이 바뀌는 순간의 기본값은 "전부 버린다" — 대가는 공개 데이터 재요청
      // 한 번이고, 그 대가는 남의 장바구니를 보여주는 것보다 압도적으로 싸다.
      queryClient.clear();
    }
  });
}
