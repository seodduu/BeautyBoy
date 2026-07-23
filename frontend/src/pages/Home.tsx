import { Link } from 'react-router-dom';
import './Home.css';

const SCALE_TICKS = [
  { ml: '30ML', major: true },
  { ml: null, major: false },
  { ml: null, major: false },
  { ml: '80ML', major: true },
  { ml: null, major: false },
  { ml: null, major: false },
  { ml: '150ML', major: true },
  { ml: null, major: false },
  { ml: null, major: false },
  { ml: '200ML', major: true },
];

/**
 * 랜딩 페이지. 상품·검색·장바구니 등 실제 기능은 이후 웨이브에서 채운다.
 * 이 태스크에서는 셸의 시각 방향을 세우는 것이 목표.
 */
export function Home() {
  return (
    <>
      <section className="bb-hero">
        <p className="bb-hero__eyebrow">MEN&apos;S SKINCARE, MEASURED</p>
        <h1 className="bb-hero__title">피부도 데이터로 관리할 시간</h1>
        <p className="bb-hero__desc">
          성분 궁합부터 피부타입별 루틴까지, 근거 있는 선택만 남깁니다. 뷰티보이는
          남성 피부 데이터를 기준으로 제품을 추천하는 커머스입니다.
        </p>
        <div className="bb-hero__cta">
          <Link to="/signup" className="bb-hero__cta-link">
            무료로 시작하기
          </Link>
        </div>
      </section>

      <div className="bb-scale" role="presentation" aria-hidden="true">
        <div className="bb-scale__row">
          {SCALE_TICKS.map((tick, i) => (
            <span
              key={i}
              className={`bb-scale__tick${tick.major ? ' bb-scale__tick--major' : ''}`}
            >
              {tick.ml ? <span className="bb-scale__label">{tick.ml}</span> : null}
            </span>
          ))}
        </div>
      </div>

      <section className="bb-features" aria-label="뷰티보이 소개">
        <div>
          <p className="bb-feature__index">SKIN</p>
          <h2 className="bb-feature__title">성분 궁합 진단</h2>
          <p className="bb-feature__desc">함께 쓰면 자극이 되는 성분 조합을 미리 알려드립니다.</p>
        </div>
        <div>
          <p className="bb-feature__index">ROUTINE</p>
          <h2 className="bb-feature__title">피부타입 루틴</h2>
          <p className="bb-feature__desc">지성·건성·민감성에 맞춘 아침저녁 루틴을 제안합니다.</p>
        </div>
        <div>
          <p className="bb-feature__index">DELIVERY</p>
          <h2 className="bb-feature__title">오늘드림 배송</h2>
          <p className="bb-feature__desc">필요한 순간, 가장 빠르게 도착하는 배송을 지원합니다.</p>
        </div>
      </section>
    </>
  );
}
