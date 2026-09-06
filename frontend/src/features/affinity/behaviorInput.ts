import { MODEL_TAG_SLUGS } from '../../types/model';
import type { AffinityEvent } from './events';
import { aggregate } from './profile';

/**
 * 행동 50건 → 사용자 탑 입력 18칸(설계 §4 "행동 태그 점수").
 *
 * aggregate()는 `${cat3}|${tag}` 단위다(단계별 추천용). 사용자 탑은 사용자 한 명의 좌표를 내므로
 * 카테고리 축을 접고 태그별로 합산한다. 단계 축을 잃는 대가는 Wave C의 composeStepV3가
 * 후보를 "단계별 풀"에서 받는 것으로 상쇄한다. 어느 단계의 후보인지는 풀이 이미 안다.
 *
 * 최대값 정규화: 이벤트가 많은 사용자와 적은 사용자의 척도를 맞춘다. 전부 0이면 0(NaN 금지).
 * 서버 학습표 빌더(tools/recsys/build_table.py)가 같은 식을 쓴다. A-3 픽스처가 동등성을 고정한다.
 */
export function behaviorInput(events: AffinityEvent[]): number[] {
  const byTag = new Map<string, number>();
  for (const [key, w] of aggregate(events)) {
    const tag = key.slice(key.indexOf('|') + 1);
    byTag.set(tag, (byTag.get(tag) ?? 0) + w);
  }
  const max = Math.max(0, ...byTag.values());
  return MODEL_TAG_SLUGS.map((slug) => (max > 0 ? (byTag.get(slug) ?? 0) / max : 0));
}
