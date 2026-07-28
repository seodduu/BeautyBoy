import { describe, expect, it, vi } from 'vitest';
import { QueryClient } from '@tanstack/react-query';
import * as pickCard from '../../components/routine/PickCard';
import { addSetToCart } from './addSetToCart';
import { queryKeys } from '../../api/queryKeys';

function clientOf() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

describe('addSetToCart — 세트 일괄 담기 집계', () => {
  it('전량 성공하면 added가 개수와 같고 skipped는 0이다', async () => {
    const spy = vi.spyOn(pickCard, 'addPickToCart').mockResolvedValue(undefined);
    const result = await addSetToCart(clientOf(), [101, 202, 303]);
    expect(result).toEqual({ added: 3, skipped: 0 });
    expect(spy).toHaveBeenCalledTimes(3);
    spy.mockRestore();
  });

  it('일부가 실패해도 나머지는 담기고 skipped로 집계된다 (롤백하지 않는다)', async () => {
    const spy = vi
      .spyOn(pickCard, 'addPickToCart')
      .mockResolvedValueOnce(undefined)
      .mockRejectedValueOnce(new Error('품절'))
      .mockResolvedValueOnce(undefined);
    const result = await addSetToCart(clientOf(), [101, 202, 303]);
    expect(result).toEqual({ added: 2, skipped: 1 });
    expect(spy).toHaveBeenCalledTimes(3); // 실패 후에도 멈추지 않는다
    spy.mockRestore();
  });

  it('전량 실패하면 added가 0이다', async () => {
    const spy = vi.spyOn(pickCard, 'addPickToCart').mockRejectedValue(new Error('실패'));
    const result = await addSetToCart(clientOf(), [101, 202]);
    expect(result).toEqual({ added: 0, skipped: 2 });
    spy.mockRestore();
  });

  it('빈 배열이면 담기를 시도하지 않는다', async () => {
    const spy = vi.spyOn(pickCard, 'addPickToCart').mockResolvedValue(undefined);
    const result = await addSetToCart(clientOf(), []);
    expect(result).toEqual({ added: 0, skipped: 0 });
    expect(spy).not.toHaveBeenCalled();
    spy.mockRestore();
  });

  it('전량 실패해도 장바구니 캐시를 무효화한다 (추출 전 동작 보존)', async () => {
    const spy = vi.spyOn(pickCard, 'addPickToCart').mockRejectedValue(new Error('실패'));
    const client = clientOf();
    const invalidate = vi.spyOn(client, 'invalidateQueries');
    await addSetToCart(client, [101, 202]);
    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.cart() });
    spy.mockRestore();
  });
});
