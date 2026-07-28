import type { QueryClient } from '@tanstack/react-query';
import { addPickToCart } from '../../components/routine/PickCard';

/**
 * 세트/루틴 일괄 담기 — 픽을 순차로 담고 **실패는 건너뛰고 집계로 돌려준다.**
 *
 * 전량 롤백하지 않는 이유: 장바구니는 편집 가능한 중간 상태라, 4개를 담아 두는 편이
 * "하나가 품절이라 아무것도 못 담았다"보다 항상 낫다(선행 설계 §4.3의 판단을 그대로 옮김).
 *
 * 문구는 화면이 정한다 — 이 함수는 결과만 돌려주고 토스트를 띄우지 않는다.
 * 순차로 도는 이유: 장바구니는 서버 상태라 동시 요청이 수량 병합 순서를 흔들 수 있다.
 */
export async function addSetToCart(
  queryClient: QueryClient,
  goodsNos: number[],
): Promise<{ added: number; skipped: number }> {
  let added = 0;
  for (const goodsNo of goodsNos) {
    try {
      await addPickToCart(queryClient, goodsNo);
      added += 1;
    } catch {
      // 품절·네트워크 실패 모두 이 픽만 건너뛴다.
    }
  }
  if (added > 0) {
    queryClient.invalidateQueries({ queryKey: ['cart'] });
  }
  return { added, skipped: goodsNos.length - added };
}
