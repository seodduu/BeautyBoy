import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { addWish, removeWish } from '../../api/wishlist';
import { queryKeys } from '../../api/queryKeys';
import { useToast } from '../../components/ui/useToast';
import { useAuthStore } from '../../stores/authStore';
import { useWishStore } from './wishStore';

interface ToggleInput {
  goodsNo: number;
  /** 누르기 직전의 찜 여부. true면 해제(DELETE), false면 찜(POST)이다. */
  wished: boolean;
}

/**
 * 상품 카드 하트의 유일한 배선.
 *
 * 이 훅이 생기기 전에는 목록·검색·랭킹·루틴의 하트가 전부 `() => {}`에 물려 있었고,
 * 찜을 **추가할 경로가 앱에 없었다**(해제만 마이페이지에서 가능). 화면마다 뮤테이션을
 * 따로 적으면 같은 누락이 다시 생기므로 한 곳으로 모은다.
 *
 * 표시는 낙관적으로 먼저 바꾼다(누른 티가 나야 한다) — 실패하면 되돌리고 토스트로 알린다.
 * 낙관적 값의 보관처는 `wishStore`다(그 파일의 주석 참고).
 */
export function useWishToggle(): (goodsNo: number, wished: boolean) => void {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { toast } = useToast();
  const member = useAuthStore((state) => state.member);
  const setOverride = useWishStore((state) => state.set);

  const mutation = useMutation({
    mutationFn: ({ goodsNo, wished }: ToggleInput) =>
      wished ? removeWish(goodsNo) : addWish(goodsNo),
    onError: (_error, { goodsNo, wished }) => {
      // 낙관적으로 바꿔둔 표시를 누르기 직전 값으로 되돌린다.
      setOverride(goodsNo, wished);
      toast(wished ? '찜 해제에 실패했어요. 다시 시도해 주세요' : '찜에 실패했어요. 다시 시도해 주세요', {
        tone: 'danger',
      });
    },
    // 성공이든 실패든 마이페이지 찜 목록은 다시 받아온다 — 이 화면의 오버레이와 달리
    // 그 목록은 "무엇이 들어 있는가" 자체가 서버 데이터다.
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.wishlist() });
    },
  });

  return function toggleWish(goodsNo: number, wished: boolean) {
    if (!member) {
      // 비회원도 볼 수 있는 화면(/routine)에서 하트를 누를 수 있다. 401을 맞고 실패
      // 토스트를 띄우는 대신 할 일을 알려주고 로그인으로 보낸다.
      toast('찜은 로그인 후 이용할 수 있어요');
      navigate('/login');
      return;
    }
    setOverride(goodsNo, !wished);
    mutation.mutate({ goodsNo, wished });
  };
}
