import './Auth.css';

/**
 * 로그인 페이지 플레이스홀더.
 * 실제 폼·API 연동은 Task 8 소유 — 이 태스크는 라우트만 확보한다.
 */
export function Login() {
  return (
    <section className="bb-auth-placeholder">
      <p className="bb-auth-placeholder__eyebrow">LOGIN</p>
      <h1 className="bb-auth-placeholder__title">로그인</h1>
      <p className="bb-auth-placeholder__desc">로그인 폼은 곧 채워집니다.</p>
    </section>
  );
}
