import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { isAxiosError } from 'axios';
import './Auth.css';
import { SkinProfileStep } from '../components/signup/SkinProfileStep';
import { fetchMe, login, signup, type AgeBand, type Concern, type SkinType } from '../api/auth';
import { useAuthStore } from '../stores/authStore';

interface AccountFields {
  email: string;
  password: string;
  nickname: string;
}

/**
 * 회원가입 페이지 — 2스텝.
 * ① 계정(이메일/비밀번호/닉네임) → ② 피부 프로필(선택, 건너뛰기 가능).
 * 가입 성공 후 자동 로그인 + `/members/me` 조회로 스토어를 채우고 홈으로 이동한다.
 */
export function Signup() {
  const navigate = useNavigate();
  const setAuth = useAuthStore((state) => state.setAuth);

  const [step, setStep] = useState<1 | 2>(1);
  const [account, setAccount] = useState<AccountFields>({ email: '', password: '', nickname: '' });
  const [skinType, setSkinType] = useState<SkinType | undefined>(undefined);
  const [concerns, setConcerns] = useState<Concern[]>([]);
  const [ageBand, setAgeBand] = useState<AgeBand | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function goToProfileStep(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setStep(2);
  }

  function toggleConcern(value: Concern) {
    setConcerns((prev) => (prev.includes(value) ? prev.filter((c) => c !== value) : [...prev, value]));
  }

  async function completeSignup(includeProfile: boolean) {
    setSubmitting(true);
    setError(null);
    try {
      await signup({
        email: account.email,
        password: account.password,
        nickname: account.nickname,
        ...(includeProfile && skinType ? { skinType } : {}),
        ...(includeProfile && concerns.length > 0 ? { concerns } : {}),
        ...(includeProfile && ageBand ? { ageBand } : {}),
      });

      const { accessToken } = await login({ email: account.email, password: account.password });
      setAuth(accessToken);
      const me = await fetchMe();
      setAuth(accessToken, me);

      navigate('/');
    } catch (err) {
      if (isAxiosError(err) && err.response?.data?.message) {
        setError(err.response.data.message as string);
      } else {
        setError('가입에 실패했습니다. 잠시 후 다시 시도해주세요.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="bb-auth">
      <p className="bb-auth__eyebrow">SIGN UP</p>
      <h1 className="bb-auth__title">회원가입</h1>

      <div className="bb-auth__steps" aria-hidden="true">
        <span className={`bb-auth__step${step === 1 ? ' bb-auth__step--active' : ''}`}>01 계정</span>
        <span className="bb-auth__step-divider" />
        <span className={`bb-auth__step${step === 2 ? ' bb-auth__step--active' : ''}`}>02 피부 프로필</span>
      </div>

      {step === 1 ? (
        <form className="bb-auth__form" onSubmit={goToProfileStep}>
          <div className="bb-auth__field">
            <label htmlFor="signup-email">이메일</label>
            <input
              id="signup-email"
              type="email"
              required
              autoComplete="email"
              value={account.email}
              onChange={(e) => setAccount((prev) => ({ ...prev, email: e.target.value }))}
            />
          </div>

          <div className="bb-auth__field">
            <label htmlFor="signup-password">비밀번호</label>
            <input
              id="signup-password"
              type="password"
              required
              minLength={8}
              autoComplete="new-password"
              value={account.password}
              onChange={(e) => setAccount((prev) => ({ ...prev, password: e.target.value }))}
            />
            <p className="bb-auth__hint">8자 이상 입력해주세요.</p>
          </div>

          <div className="bb-auth__field">
            <label htmlFor="signup-nickname">닉네임</label>
            <input
              id="signup-nickname"
              type="text"
              required
              autoComplete="nickname"
              value={account.nickname}
              onChange={(e) => setAccount((prev) => ({ ...prev, nickname: e.target.value }))}
            />
          </div>

          {error && <p className="bb-auth__error">{error}</p>}

          <button type="submit" className="bb-auth__submit">
            다음
          </button>
        </form>
      ) : (
        <div className="bb-auth__form">
          <h2 className="bb-auth__section-title">피부 프로필</h2>
          <p className="bb-auth__section-desc">
            선택 입력입니다. 지금 채워두면 가입 직후부터 맞춤 추천을 받을 수 있어요.
          </p>

          <SkinProfileStep
            skinType={skinType}
            concerns={concerns}
            ageBand={ageBand}
            onChangeSkinType={setSkinType}
            onToggleConcern={toggleConcern}
            onChangeAgeBand={setAgeBand}
          />

          {error && <p className="bb-auth__error">{error}</p>}

          <div className="bb-auth__actions">
            <button
              type="button"
              className="bb-auth__submit bb-auth__submit--ghost"
              disabled={submitting}
              onClick={() => completeSignup(false)}
            >
              건너뛰기
            </button>
            <button
              type="button"
              className="bb-auth__submit"
              disabled={submitting}
              onClick={() => completeSignup(true)}
            >
              가입 완료
            </button>
          </div>
        </div>
      )}

      <p className="bb-auth__switch">
        이미 계정이 있으신가요? <Link to="/login">로그인</Link>
      </p>
    </section>
  );
}
