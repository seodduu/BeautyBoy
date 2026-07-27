import { beforeEach, describe, expect, it } from 'vitest';
import { MAX_EVENTS, WEIGHT, clearEvents, readEvents, recordEvent, toCat3 } from './events';

const STORAGE_KEY = 'bb.affinity.v1';

function sampleEvent(goodsNo: number) {
  return { goodsNo, cat3: 'C001002', tags: ['moisture'], w: WEIGHT.view };
}

beforeEach(() => {
  localStorage.clear();
});

describe('toCat3 — leaf 코드 절단', () => {
  it('leaf 10자 카테고리가 cat3 7자로 절단된다', () => {
    expect(toCat3('C001001001')).toBe('C001001');
  });

  it('이미 7자 이하면 그대로 둔다 — 클렌징(C002)처럼 대분류가 곧 한 단계인 경우가 있다', () => {
    expect(toCat3('C002')).toBe('C002');
  });
});

describe('recordEvent — 링버퍼', () => {
  it('기록한 이벤트를 읽어올 수 있다', () => {
    recordEvent(sampleEvent(1));

    expect(readEvents()).toEqual([sampleEvent(1)]);
  });

  it('링버퍼가 50개를 넘으면 오래된 것부터 버린다', () => {
    for (let i = 1; i <= MAX_EVENTS + 3; i++) {
      recordEvent(sampleEvent(i));
    }

    const events = readEvents();
    expect(events).toHaveLength(MAX_EVENTS);
    // 앞에서 3개(1·2·3번)가 잘려 나가고 마지막이 남는다.
    expect(events[0].goodsNo).toBe(4);
    expect(events[events.length - 1].goodsNo).toBe(MAX_EVENTS + 3);
  });

  it('cat3가 빈 문자열이면 기록하지 않는다 — 문맥 없는 찜(설계 §6.1)', () => {
    recordEvent({ goodsNo: 7, cat3: '', tags: ['moisture'], w: WEIGHT.wish });

    expect(readEvents()).toEqual([]);
  });
});

describe('readEvents — 손상 내성', () => {
  it('저장된 값이 없으면 빈 배열을 돌려준다', () => {
    expect(readEvents()).toEqual([]);
  });

  it('손상된 JSON을 읽으면 빈 배열을 돌려주고 던지지 않는다', () => {
    localStorage.setItem(STORAGE_KEY, '{이건 JSON이 아니다');

    expect(() => readEvents()).not.toThrow();
    expect(readEvents()).toEqual([]);
  });

  it('배열이 아닌 값은 통째로 폐기한다', () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ goodsNo: 1 }));

    expect(readEvents()).toEqual([]);
  });

  it('원소 하나라도 형태가 어긋나면 통째로 폐기한다 — 반쪽 프로필로 계산하지 않는다', () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify([sampleEvent(1), { goodsNo: 2 }]));

    expect(readEvents()).toEqual([]);
  });

  it('w가 1·2·3 밖의 값이면 폐기한다', () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify([{ ...sampleEvent(1), w: 9 }]));

    expect(readEvents()).toEqual([]);
  });

  it('폐기 후 다시 기록하면 빈 상태에서 새로 쌓인다', () => {
    localStorage.setItem(STORAGE_KEY, '깨진 값');

    recordEvent(sampleEvent(1));

    expect(readEvents()).toEqual([sampleEvent(1)]);
  });
});

describe('clearEvents', () => {
  it('저장된 이벤트를 비운다', () => {
    recordEvent(sampleEvent(1));

    clearEvents();

    expect(readEvents()).toEqual([]);
  });
});
