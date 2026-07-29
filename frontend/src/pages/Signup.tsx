import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { isAxiosError } from 'axios';
import './Auth.css';
import { SkinProfileStep } from '../components/signup/SkinProfileStep';
import { fetchMe, login, signup, type AgeBand, type Concern, type SkinType } from '../api/auth';
import { useAuthStore } from '../stores/authStore';
import { AuthLayout } from '../components/auth/AuthLayout';
import { Button } from '../components/ui/Button';
import { Field } from '../components/ui/Field';

interface AccountFields {
  email: string;
  password: string;
  nickname: string;
}

/**
 * 회원가입 페이지 — 2스텝.
 * ① 계정(이메일/비밀번호/닉네임) → ② 피부 프로필(선택, 건너뛰기 가능).
 * 가입 성공 후 자동 로그인 + `/members/me` 조회로 스토어를 채우고 `/main`으로 이동한다.
 * 랜딩(`/`)이 아니라 로그인 직후와 같은 목적지다 — 방금 계정을 만든 손님에게
 * "가입하세요" 히어로를 다시 보여주는 것은 되돌아간 것처럼 읽힌다.
 */
export function Signup() {
  const navigate = useNavigate();
  const setAuth = useAuthStore((state) => state.setAuth);

  /* 랜딩 히어로에서 넘어온 이메일로 첫 칸을 채운다(?email=). 없으면 빈 값. */
  const [searchParams] = useSearchParams();

  const [step, setStep] = useState<1 | 2>(1);
  const [account, setAccount] = useState<AccountFields>({
    email: searchParams.get('email') ?? '',
    password: '',
    nickname: '',
  });
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

      navigate('/main');
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
    <AuthLayout>
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
          <Field
            id="signup-email"
            label="이메일"
            type="email"
            inputMode="email"
            required
            autoComplete="email"
            value={account.email}
            onChange={(value) => setAccount((prev) => ({ ...prev, email: value }))}
          />

          <Field
            id="signup-password"
            label="비밀번호"
            type="password"
            required
            minLength={8}
            autoComplete="new-password"
            value={account.password}
            onChange={(value) => setAccount((prev) => ({ ...prev, password: value }))}
            hint="8자 이상 입력해주세요."
          />

          <Field
            id="signup-nickname"
            label="닉네임"
            type="text"
            required
            autoComplete="nickname"
            value={account.nickname}
            onChange={(value) => setAccount((prev) => ({ ...prev, nickname: value }))}
          />

          {error && <p className="bb-auth__error">{error}</p>}

          <Button type="submit">다음</Button>
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
            <Button variant="ghost" loading={submitting} onClick={() => completeSignup(false)}>
              건너뛰기
            </Button>
            <Button loading={submitting} onClick={() => completeSignup(true)}>
              가입 완료
            </Button>
          </div>
        </div>
      )}

      <p className="bb-auth__switch">
        이미 계정이 있으신가요? <Link to="/login">로그인</Link>
      </p>
      </section>
    </AuthLayout>
  );
}
