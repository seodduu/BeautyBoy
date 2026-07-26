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
  /**
   * 권한(USER/ADMIN) — `grade`(등급: BABY 등)와 다른 축이다. 출처는 오직
   * `GET /members/me` 응답이다. JWT를 프론트에서 디코드해 꺼내지 않는다 —
   * 토큰 파싱을 클라이언트가 시작하면 신뢰 경계가 흐려진다(RequireAdmin 참고).
   */
  role?: string;
}

interface AuthState {
  /**
   * 액세스 토큰. 메모리에만 보관한다 — localStorage/sessionStorage 저장 금지.
   * XSS로 탈취되면 재사용될 수 있으므로, 새로고침 시에는 리프레시 쿠키 +
   * `/auth/refresh`로 세션을 복구하는 것이 이 설계의 의도다.
   */
  accessToken: string | null;
  member: MemberInfo | null;
  /**
   * 앱 부트스트랩(최초 `/auth/refresh` 시도) 진행 중 여부.
   * Header는 이 값이 true인 동안 로그인/로그아웃 영역 대신 스켈레톤을 보여준다 —
   * 복구 완료 전에 "로그인" 링크를 먼저 보여줬다가 닉네임으로 바뀌는 깜빡임을 막기 위함.
   */
  isBootstrapping: boolean;
  setAuth: (accessToken: string, member?: MemberInfo) => void;
  clear: () => void;
  setBootstrapping: (value: boolean) => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  member: null,
  isBootstrapping: true,
  setAuth: (accessToken, member) =>
    set((state) => ({
      accessToken,
      member: member ?? state.member,
    })),
  clear: () => set({ accessToken: null, member: null }),
  setBootstrapping: (value) => set({ isBootstrapping: value }),
}));
