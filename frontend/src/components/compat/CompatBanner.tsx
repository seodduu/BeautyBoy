import type { CompatCheckResult, CompatVerdict } from '../../api/compat';
import './CompatBanner.css';

interface CompatBannerProps {
  result: CompatCheckResult;
}

const TONE_CLASS: Record<CompatVerdict, string> = {
  CONFLICT: 'bb-compat-banner--danger',
  CAUTION: 'bb-compat-banner--caution',
  SYNERGY: 'bb-compat-banner--success',
};

const HEADING: Record<CompatVerdict, string> = {
  CONFLICT: '함께 담은 상품끼리 성분이 부딪혀요',
  CAUTION: '함께 담은 상품, 같이 쓸 때 주의하세요',
  SYNERGY: '함께 쓰면 좋은 조합이에요',
};

const ICON: Record<CompatVerdict, string> = {
  CONFLICT: '!',
  CAUTION: '!',
  SYNERGY: '✓',
};

// 표시 순서: 나쁜 소식부터. 긍정 안내가 먼저 보이면 경고의 무게가 죽는다(계획 §2 결정 1).
const VERDICT_ORDER: CompatVerdict[] = ['CONFLICT', 'CAUTION', 'SYNERGY'];

/**
 * 궁합 경고 배너 — 설계 8장 "적용 지점 ③"(장바구니).
 * DESIGN.md 규칙: 시그널 색은 배경을 칠하지 않고 좌측 보더 + 아이콘 + 텍스트로만 표현한다.
 * overall === 'OK'면 렌더하지 않는다(호출부에서 미리 걸러도 되지만, 여기서도 한 번 더 막아
 * "OK인데 배너가 뜨는" 사고를 이 컴포넌트 하나로 방지한다).
 * findings는 verdict별 섹션으로 분리해 CONFLICT → CAUTION → SYNERGY 순으로 쌓는다 —
 * 경고와 긍정을 한 박스에 섞으면 메시지가 상쇄된다(DESIGN.md compat-banner).
 * SYNERGY는 경고가 아니므로 role="status"로 낭독한다 — 경고가 아닌 것을 assertive로 읽히지 않는다.
 * CONFLICT여도 이 컴포넌트는 아무것도 비활성화하지 않는다 — 궁합은 조언이지 금지가 아니다.
 */
export function CompatBanner({ result }: CompatBannerProps) {
  if (result.overall === 'OK') {
    return null;
  }

  return (
    <>
      {VERDICT_ORDER.map((tone) => {
        const findings = result.findings.filter((finding) => finding.verdict === tone);
        if (findings.length === 0) {
          return null;
        }
        return (
          <div
            key={tone}
            className={`bb-compat-banner ${TONE_CLASS[tone]}`}
            role={tone === 'SYNERGY' ? 'status' : 'alert'}
          >
            <span className="bb-compat-banner__icon" aria-hidden="true">
              {ICON[tone]}
            </span>
            <div className="bb-compat-banner__body">
              <p className="bb-compat-banner__heading">{HEADING[tone]}</p>
              <ul className="bb-compat-banner__list">
                {findings.map((finding, index) => (
                  <li key={`${finding.categoryA}-${finding.categoryB}-${index}`} className="bb-compat-banner__item">
                    <span className="bb-compat-banner__categories">
                      {finding.categoryA} · {finding.categoryB}
                    </span>
                    <span className="bb-compat-banner__reason">{finding.reason}</span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        );
      })}
    </>
  );
}
