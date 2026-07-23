import { create } from 'zustand';

/**
 * 로그인한 회원의 요약 정보.
 * `GET /api/v1/members/me` 응답 형태를 따른다.
 */
export interface MemberInfo {
  id: number;
  email: string;
  nickname: string;
  grade: string;
  skinType?: string;
  concerns?: string[];
  ageBand?: string;
}

interface AuthState {
  /**
   * 액세스 토큰. 메모리에만 보관한다 — localStorage/sessionStorage 저장 금지.
   * XSS로 탈취되면 재사용될 수 있으므로, 새로고침 시에는 리프레시 쿠키 +
   * `/auth/refresh`로 세션을 복구하는 것이 이 설계의 의도다.
   */
  accessToken: string | null;
  member: MemberInfo | null;
  setAuth: (accessToken: string, member?: MemberInfo) => void;
  clear: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  member: null,
  setAuth: (accessToken, member) =>
    set((state) => ({
      accessToken,
      member: member ?? state.member,
    })),
  clear: () => set({ accessToken: null, member: null }),
}));
