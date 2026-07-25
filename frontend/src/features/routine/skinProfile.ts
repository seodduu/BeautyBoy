import { updateProfile, type Me } from '../../api/member';
import type { SkinType } from '../../api/routine';

/** localStorage 키 — 비회원 퀴즈 결과 보관용(설계 8장 "비회원=3문항 퀴즈, 가입 시 승격"). */
const STORAGE_KEY = 'bb.skinType';

const VALID_SKIN_TYPES: readonly SkinType[] = ['DRY', 'OILY', 'COMBINATION', 'SENSITIVE'];

function isSkinType(value: string | null): value is SkinType {
  return value !== null && (VALID_SKIN_TYPES as readonly string[]).includes(value);
}

/** 저장된 값이 없거나 손상됐으면(과거 스키마·수동 조작 등) null을 돌려준다. */
export function readLocalSkinType(): SkinType | null {
  const value = localStorage.getItem(STORAGE_KEY);
  return isSkinType(value) ? value : null;
}

export function writeLocalSkinType(t: SkinType): void {
  localStorage.setItem(STORAGE_KEY, t);
}

export function clearLocalSkinType(): void {
  localStorage.removeItem(STORAGE_KEY);
}

/**
 * 로그인 회원의 서버 프로필에 skinType이 비어 있고 로컬에 퀴즈 결과가 남아 있으면,
 * 그 결과를 서버로 한 번 승격(PUT /members/me/profile)하고 로컬을 비운다.
 * 서버 프로필이 이미 있으면(재로그인 등) 로컬 값으로 덮어쓰지 않는다 — 서버가 진실이다.
 */
export async function promoteLocalSkinTypeIfNeeded(me: Me): Promise<void> {
  if (me.skinType) {
    return;
  }
  const local = readLocalSkinType();
  if (!local) {
    return;
  }
  await updateProfile({ skinType: local });
  clearLocalSkinType();
}
