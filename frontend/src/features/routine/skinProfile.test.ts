import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as memberApi from '../../api/member';
import type { Me } from '../../api/member';
import {
  clearLocalSkinType,
  promoteLocalSkinTypeIfNeeded,
  readLocalSkinType,
  writeLocalSkinType,
} from './skinProfile';

function buildMe(overrides: Partial<Me> = {}): Me {
  return {
    id: 1,
    email: 'mock@beautyboy.dev',
    nickname: '민수',
    grade: 'BRONZE',
    skinType: null,
    concerns: [],
    ageBand: null,
    ...overrides,
  };
}

beforeEach(() => {
  localStorage.clear();
  vi.restoreAllMocks();
});

describe('skinProfile — localStorage 읽기/쓰기', () => {
  it('저장한 적 없으면 null을 돌려준다', () => {
    expect(readLocalSkinType()).toBeNull();
  });

  it('쓰고 읽으면 그 값 그대로 돌아온다', () => {
    writeLocalSkinType('DRY');
    expect(readLocalSkinType()).toBe('DRY');
  });

  it('비우면 다시 null이 된다', () => {
    writeLocalSkinType('OILY');
    clearLocalSkinType();
    expect(readLocalSkinType()).toBeNull();
  });

  it('유효하지 않은 값이 저장돼 있으면 null로 취급한다', () => {
    localStorage.setItem('bb.skinType', 'NOT_A_SKIN_TYPE');
    expect(readLocalSkinType()).toBeNull();
  });
});

describe('promoteLocalSkinTypeIfNeeded — 로그인 시 로컬 퀴즈 결과 승격', () => {
  it('로그인했고 서버 프로필이 비어 있으면 로컬 결과를 한 번 승격하고 로컬을 비운다', async () => {
    writeLocalSkinType('DRY');
    const updateProfileSpy = vi.spyOn(memberApi, 'updateProfile').mockResolvedValue(undefined);

    await promoteLocalSkinTypeIfNeeded(buildMe({ skinType: null }));

    expect(updateProfileSpy).toHaveBeenCalledWith(expect.objectContaining({ skinType: 'DRY' }));
    expect(readLocalSkinType()).toBeNull();
  });

  it('서버 프로필에 이미 skinType이 있으면 승격하지 않는다', async () => {
    writeLocalSkinType('OILY');
    const updateProfileSpy = vi.spyOn(memberApi, 'updateProfile').mockResolvedValue(undefined);

    await promoteLocalSkinTypeIfNeeded(buildMe({ skinType: 'SENSITIVE' }));

    expect(updateProfileSpy).not.toHaveBeenCalled();
    // 서버가 진실이므로 로컬 값도 그대로 둔다(승격 대상이 아니었을 뿐 삭제할 이유는 없다).
    expect(readLocalSkinType()).toBe('OILY');
  });

  it('로컬에도 결과가 없으면 아무것도 하지 않는다', async () => {
    const updateProfileSpy = vi.spyOn(memberApi, 'updateProfile').mockResolvedValue(undefined);

    await promoteLocalSkinTypeIfNeeded(buildMe({ skinType: null }));

    expect(updateProfileSpy).not.toHaveBeenCalled();
  });
});
