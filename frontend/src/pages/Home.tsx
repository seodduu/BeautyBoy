import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { WaveCanvas } from '../components/landing/WaveCanvas';
import './Home.css';

/**
 * 랜딩 페이지. 상품·검색·장바구니 등 실제 기능은 이후 웨이브에서 채운다.
 * 이 태스크에서는 셸의 시각 방향을 세우는 것이 목표.
 */
export function Home() {
  const [email, setEmail] = useState('');
  const navigate = useNavigate();

  /* 랜딩에서 받은 이메일을 가입 화면으로 넘겨 첫 칸을 채워준다 —
     여기서 회원을 만들지 않는다(가입 검증·중복확인은 가입 화면과 서버의 몫). */
  const handleStart = (event: FormEvent) => {
    event.preventDefault();
    navigate(`/signup?email=${encodeURIComponent(email)}`);
  };

  return (
    <>
      {/* 랜딩 히어로: 검정 캔버스 + 물결 리본(canvas) 위에 워드마크를 얹는다.
          {landing-hero} 사양은 DESIGN.md 참조. */}
      <section className="bb-hero">
        <WaveCanvas />

        {/* 워드마크 → 서브카피 → 폼을 한 덩어리로 가운데에 쌓는다.
            서브카피를 타이포 아래로 내려야 메뉴 부속이 아니라 이 화면의 설명으로 읽힌다. */}
        <div className="bb-hero__inner">
          <h1 className="bb-hero__wordmark">Beauty Boy</h1>

          <p className="bb-hero__lede">
            성분 궁합부터 피부타입별 루틴까지,
            <br />
            <strong className="bb-hero__lede-strong">근거 있는 선택</strong>만 남깁니다.
          </p>

          <form className="bb-hero__cta" onSubmit={handleStart}>
            <div className="bb-hero__cta-row">
              {/* 보이는 라벨 대신 aria-label을 쓴다 — 화면에선 placeholder가 이미 같은 말을 하고
                  구석의 작은 라벨이 오히려 어수선해진다. 접근성 이름은 유지된다.
                  (DESIGN.md 폼 규칙의 랜딩 예외 — 문서에 반영 필요) */}
              <input
                id="hero-email"
                className="bb-hero__cta-input"
                type="email"
                inputMode="email"
                autoComplete="email"
                required
                aria-label="이메일 주소"
                placeholder="이메일을 입력하면 피부 루틴부터 시작합니다"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
              <button type="submit" className="bb-hero__cta-submit" aria-label="시작하기">
                <svg width="20" height="20" viewBox="0 0 20 20" fill="none" aria-hidden="true">
                  <path
                    d="M17.5 2.5L9.5 10.5M17.5 2.5L12.5 17.5L9.5 10.5M17.5 2.5L2.5 7.5L9.5 10.5"
                    stroke="currentColor"
                    strokeWidth="1.5"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
              </button>
            </div>
          </form>
        </div>
      </section>
    </>
  );
}
