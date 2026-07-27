import type { TagView } from '../../types/goods';

/**
 * 후보 정렬 보조 — 순수 함수만 둔다.
 *
 * v1의 티어 매칭(`matchByBehavior`/`matchByProfile`/`takeTopPerStep`)은 **`composer.ts`의 점수
 * 공식으로 대체돼 삭제됐다**(루틴 조합기 설계 §7). 티어 분기가 사라지면서 "개인화 섹션 최대 2개"
 * 상한과 `Target` 개념도 함께 폐기됐다 — v2는 5단계 전부가 조합 대상이다.
 */

/**
 * 사용감 tie-break — 선호 사용감을 많이 가진 후보를 앞으로 당기는 **안정 정렬**.
 * 일치 개수가 같으면 서버가 준 인기순을 그대로 둔다.
 *
 * 메인의 배선에서는 빠졌다 — 점수 공식의 textureMatch 항이 같은 일을 흡수했다(설계 §7).
 * 점수 없이 "받아 온 목록을 사용감으로만 재정렬"하는 화면을 위해 함수 자체는 남겨 둔다.
 */
export function rankByTexture<T extends { tags?: TagView[] }>(items: T[], textures: string[]): T[] {
  if (textures.length === 0) {
    return items;
  }
  const matches = (item: T) =>
    (item.tags ?? []).filter((tag) => textures.includes(tag.slug)).length;
  // Array.prototype.sort는 안정 정렬이다(ES2019) — 동점이면 입력 순서가 유지된다.
  return [...items].sort((a, b) => matches(b) - matches(a));
}
