import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { isAxiosError } from 'axios';
import './Auth.css';
import { fetchMe, login } from '../api/auth';
import { useAuthStore } from '../stores/authStore';
import { AuthLayout } from '../components/auth/AuthLayout';
import { Button } from '../components/ui/Button';
import { Field } from '../components/ui/Field';
import { useTitle } from '../hooks/useTitle';

/**
 * 로그인 페이지.
 * 로그인 응답에는 닉네임이 없으므로(accessToken만) 성공 직후 `/members/me`를 호출해
 * Header에 표시할 회원 정보를 스토어에 채운다.
 * 성공 후에는 랜딩(/)이 아니라 루틴 메인(/main)으로 보낸다 — 랜딩은 비로그인 유입 화면이다.
 */
export function Login() {
  useTitle('로그인');
  const navigate = useNavigate();
  const setAuth = useAuthStore((state) => state.setAuth);

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const { accessToken } = await login({ email, password });
      setAuth(accessToken);
      const me = await fetchMe();
      setAuth(accessToken, me);
      navigate('/main');
    } catch (err) {
      if (isAxiosError(err) && err.response?.data?.message) {
        setError(err.response.data.message as string);
      } else {
        setError('로그인에 실패했습니다. 잠시 후 다시 시도해주세요.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout>
      <section className="bb-auth bb-auth--narrow">
        <p className="bb-auth__eyebrow">LOGIN</p>
        <h1 className="bb-auth__title">로그인</h1>

        <form className="bb-auth__form" onSubmit={handleSubmit}>
        <Field
          id="login-email"
          label="이메일"
          type="email"
          inputMode="email"
          required
          autoComplete="email"
          value={email}
          onChange={setEmail}
        />

        <Field
          id="login-password"
          label="비밀번호"
          type="password"
          required
          autoComplete="current-password"
          value={password}
          onChange={setPassword}
        />

        {error && <p className="bb-auth__error">{error}</p>}

          <Button type="submit" loading={submitting}>
            로그인
          </Button>
        </form>

        <p className="bb-auth__switch">
          아직 계정이 없으신가요? <Link to="/signup">회원가입</Link>
        </p>
      </section>
    </AuthLayout>
  );
}
