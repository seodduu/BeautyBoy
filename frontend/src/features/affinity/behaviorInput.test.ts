import { describe, expect, it } from 'vitest';
import type { AffinityEvent } from './events';
import { WEIGHT } from './events';
import { behaviorInput } from './behaviorInput';
import { MODEL_TAG_SLUGS } from '../../types/model';

function event(partial: Partial<AffinityEvent> = {}): AffinityEvent {
  return { goodsNo: 1, cat3: 'C001002', tags: ['moisture'], w: WEIGHT.view, ...partial };
}

function slotOf(slug: string): number {
  return (MODEL_TAG_SLUGS as readonly string[]).indexOf(slug);
}

describe('behaviorInput — 행동 50건에서 18칸 입력 벡터', () => {
  it('이벤트가_없으면_18칸_전부_0', () => {
    const vector = behaviorInput([]);

    expect(vector).toHaveLength(18);
    expect(vector.every((value) => value === 0)).toBe(true);
  });

  it('카테고리가_달라도_같은_태그는_합산된다', () => {
    const vector = behaviorInput([
      event({ cat3: 'C002001', tags: ['moisture'], w: WEIGHT.view }),
      event({ cat3: 'C001001', tags: ['moisture'], w: WEIGHT.cart }),
    ]);

    expect(vector[slotOf('moisture')]).toBe(1);
    expect(vector.filter((value) => value !== 0)).toHaveLength(1);
  });

  it('최대값으로_정규화된다', () => {
    // sebum 6점(담기 2회), moisture 3점(담기 1회)
    const vector = behaviorInput([
      event({ tags: ['sebum'], w: WEIGHT.cart }),
      event({ tags: ['sebum'], w: WEIGHT.cart }),
      event({ tags: ['moisture'], w: WEIGHT.cart }),
    ]);

    expect(vector[slotOf('sebum')]).toBe(1);
    expect(vector[slotOf('moisture')]).toBe(0.5);
  });

  it('순서는_MODEL_TAG_SLUGS다', () => {
    const vector = behaviorInput([event({ tags: ['gentle'] })]);

    expect(slotOf('gentle')).toBe(17);
    expect(vector[17]).toBe(1);
    expect(vector.slice(0, 17).every((value) => value === 0)).toBe(true);
  });

  it('모르는_태그는_버린다', () => {
    const vector = behaviorInput([event({ tags: ['unknown'] })]);

    expect(vector).toHaveLength(18);
    expect(vector.every((value) => value === 0)).toBe(true);
  });
});
