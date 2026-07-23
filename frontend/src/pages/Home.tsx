import { Link } from 'react-router-dom';
import { WaveCanvas } from '../components/landing/WaveCanvas';
import './Home.css';

/**
 * 랜딩 페이지. 상품·검색·장바구니 등 실제 기능은 이후 웨이브에서 채운다.
 * 이 태스크에서는 셸의 시각 방향을 세우는 것이 목표.
 */
export function Home() {
  return (
    <>
      {/* 랜딩 히어로: 검정 캔버스 + 물결 리본(canvas) 위에 워드마크를 얹는다.
          {landing-hero} 사양은 DESIGN.md 참조. */}
      <section className="bb-hero">
        <WaveCanvas />

        <p className="bb-hero__lede">
          성분 궁합부터 피부타입별 루틴까지,
          <br />
          근거 있는 선택만 남깁니다.
        </p>

        <h1 className="bb-hero__wordmark">
          <span className="bb-hero__wordmark-line">Beauty</span>
          <span className="bb-hero__wordmark-line">Boy</span>
        </h1>

        <div className="bb-hero__cta">
          <Link to="/login" className="bb-hero__cta-link">
            Get started
            <span className="bb-hero__cta-chevron" aria-hidden="true">
              ›
            </span>
          </Link>
        </div>
      </section>

      <section className="bb-features" aria-label="뷰티보이 소개">
        <div className="bb-feature">
          <p className="bb-feature__eyebrow">SKIN</p>
          <h2 className="bb-feature__title">성분 궁합 진단</h2>
          <p className="bb-feature__desc">함께 쓰면 자극이 되는 성분 조합을 미리 알려드립니다.</p>
        </div>
        <div className="bb-feature">
          <p className="bb-feature__eyebrow">ROUTINE</p>
          <h2 className="bb-feature__title">피부타입 루틴</h2>
          <p className="bb-feature__desc">지성·건성·민감성에 맞춘 아침저녁 루틴을 제안합니다.</p>
        </div>
        <div className="bb-feature">
          <p className="bb-feature__eyebrow">DELIVERY</p>
          <h2 className="bb-feature__title">오늘드림 배송</h2>
          <p className="bb-feature__desc">필요한 순간, 가장 빠르게 도착하는 배송을 지원합니다.</p>
        </div>
      </section>
    </>
  );
}
