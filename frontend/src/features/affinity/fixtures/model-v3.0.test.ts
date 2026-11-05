import { describe, expect, it } from 'vitest';
import fixture from './model-v3.0.json';
import {
  EMB_DIM,
  HIDDEN_DIM,
  MODEL_CONCERNS,
  MODEL_SKIN_TYPES,
  MODEL_TAG_SLUGS,
  MODEL_TEXTURES,
  USER_INPUT_DIM,
} from '../../../types/model';

/**
 * 픽스처는 tools/recsys/schema/make_fixture.py 산출물이다. 손으로 고치지 않는다.
 * 여기서 보는 것은 "생성기가 만든 파일이 TS 쪽 상수와 같은 계약을 말하는가"다.
 * 실제 API는 두 파일로 나뉘고 픽스처만 하나로 합쳐 둔다.
 */
const { itemEmbeddings, userTower } = fixture;

describe('model-v3.0 픽스처 — 모델 파일 계약', () => {
  it('두_파일_version이_같다', () => {
    expect(itemEmbeddings.version).toBe(userTower.version);
    expect(itemEmbeddings.version).toBe('v3.0');
  });

  it('tagSlugs가_MODEL_TAG_SLUGS와_같다', () => {
    expect(itemEmbeddings.tagSlugs).toEqual([...MODEL_TAG_SLUGS]);
  });

  it('inputOrder_네_배열이_상수와_같다', () => {
    expect(userTower.inputOrder.skinType).toEqual([...MODEL_SKIN_TYPES]);
    expect(userTower.inputOrder.concerns).toEqual([...MODEL_CONCERNS]);
    expect(userTower.inputOrder.textures).toEqual([...MODEL_TEXTURES]);
    expect(userTower.inputOrder.behaviorTags).toEqual([...MODEL_TAG_SLUGS]);
  });

  it('layers_모양이_34x48_48x32다', () => {
    const [hidden, output] = userTower.layers;

    expect(hidden.w).toHaveLength(USER_INPUT_DIM);
    expect(hidden.w.every((row) => row.length === HIDDEN_DIM)).toBe(true);
    expect(hidden.b).toHaveLength(HIDDEN_DIM);

    expect(output.w).toHaveLength(HIDDEN_DIM);
    expect(output.w.every((row) => row.length === EMB_DIM)).toBe(true);
    expect(output.b).toHaveLength(EMB_DIM);
  });

  it('items_각_벡터_길이가_32다', () => {
    const vectors = Object.values(itemEmbeddings.items);

    expect(vectors.length).toBeGreaterThan(0);
    expect(vectors.every((vector) => vector.length === EMB_DIM)).toBe(true);
  });

  it('USER_INPUT_DIM이_inputOrder_합과_같다', () => {
    const { skinType, concerns, textures, behaviorTags } = userTower.inputOrder;
    const sum = skinType.length + concerns.length + textures.length + behaviorTags.length;

    expect(sum).toBe(USER_INPUT_DIM);
    expect(USER_INPUT_DIM).toBe(34);
  });
});
