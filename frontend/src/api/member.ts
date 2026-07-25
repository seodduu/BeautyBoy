import { api } from './client';
import type { ApiEnvelope } from '../types/goods';

/** GET /members/me/addresses 응답 라인. 백엔드 AddressResponse와 동일한 필드셋(위경도 제외). */
export interface Address {
  id: number;
  receiver: string;
  phone: string;
  zipcode: string;
  address1: string;
  address2: string;
  isDefault: boolean;
}

/** POST /members/me/addresses 요청 바디. */
export interface AddressInput {
  receiver: string;
  phone: string;
  zipcode: string;
  address1: string;
  address2: string;
  isDefault: boolean;
}

/** GET /members/me/addresses */
export async function fetchAddresses(): Promise<Address[]> {
  const response = await api.get<ApiEnvelope<Address[]>>('/members/me/addresses');
  return response.data.data;
}

/**
 * POST /members/me/addresses — 서버는 생성된 주소(AddressResponse)를 돌려주지만,
 * 호출부(마이페이지 주소록)는 항상 목록을 재조회해 최신 상태를 그리므로 응답값을 쓰지 않는다.
 */
export async function createAddress(address: AddressInput): Promise<void> {
  await api.post('/members/me/addresses', address);
}

/** GET /members/me 응답. */
export interface Me {
  id: number;
  email: string;
  nickname: string;
  grade: string;
  skinType: string | null;
  concerns: string[];
  ageBand: string | null;
}

/** GET /members/me */
export async function fetchMe(): Promise<Me> {
  const response = await api.get<ApiEnvelope<Me>>('/members/me');
  return response.data.data;
}

/** PUT /members/me/profile 요청 바디 — 세 필드 모두 선택. */
export interface ProfileInput {
  skinType?: string;
  concerns?: string[];
  ageBand?: string;
}

/** PUT /members/me/profile */
export async function updateProfile(profile: ProfileInput): Promise<void> {
  await api.put('/members/me/profile', profile);
}
