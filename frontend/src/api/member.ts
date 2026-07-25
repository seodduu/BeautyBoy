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

/**
 * PUT /members/me/addresses/{id} — 필드 갱신 겸 기본배송지 지정.
 *
 * 기본배송지 지정 전용 엔드포인트는 없다 — `AddressRequest`(백엔드)에 `isDefault` 필드가
 * 있고, 컨트롤러의 수정 엔드포인트가 이 값을 그대로 받는다. 그래서 "기본으로 설정"은
 * 그 배송지의 나머지 필드는 그대로 두고 `isDefault: true`로 채운 `AddressInput`을
 * 이 함수로 PUT하는 형태다.
 * 근거: backend/src/main/java/com/beautyboy/member/MemberController.java:64-69 (updateAddress),
 *       backend/src/main/java/com/beautyboy/member/dto/AddressRequest.java:8-17 (isDefault 필드).
 * 기본배송지 다중화는 4-2의 DB 유니크 제약이 막아준다 — 프론트는 신경 쓰지 않는다.
 */
export async function updateAddress(id: number, address: AddressInput): Promise<void> {
  await api.put(`/members/me/addresses/${id}`, address);
}

/** DELETE /members/me/addresses/{id} */
export async function deleteAddress(id: number): Promise<void> {
  await api.delete(`/members/me/addresses/${id}`);
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
