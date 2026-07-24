import type { GoodsAssessment, VerdictCode } from '../../types/assessment';
import './AssessmentCard.css';

interface AssessmentCardProps {
  assessment: GoodsAssessment;
  /** "확인 성분 N개 보기" 클릭 → 확인 성분 패널 열기. */
  onOpenPanel: () => void;
}

type Tone = 'success' | 'caution' | 'danger' | 'neutral';

/**
 * 판정 코드 → 신호등 톤(DESIGN.md signal-* 만, 배경 채움 없이 점·텍스트·1px 테두리로).
 *  초록: 걱정없음·대체로무난 / 주황: 민감피부 확인필요 / 빨강: 주의필요 / 회색: 검토중(데이터 상태).
 */
function toneFor(code: VerdictCode): Tone {
  if (code === 'NO_CONCERN' || code === 'MOSTLY_FINE') return 'success';
  if (code === 'CHECK_SENSITIVE') return 'caution';
  if (code === 'CAUTION') return 'danger';
  return 'neutral'; // REVIEW — 금지 매칭 = 데이터 확인 상태, 심각도 색을 쓰지 않는다
}

/**
 * 성분 종합판정 카드 — 가격 아래·장바구니 버튼 위(설계 §0.4, UX §6).
 * 숫자 점수는 노출하지 않는다. 확인 필요(CHECK 축) 성분이 있을 때만 패널 버튼을 낸다.
 */
export function AssessmentCard({ assessment, onOpenPanel }: AssessmentCardProps) {
  const tone = toneFor(assessment.verdictCode);
  const checkCount = assessment.flagged.filter((f) => f.axis === 'CHECK').length;
  const limitCount = assessment.flagged.filter((f) => f.axis === 'INFO').length;

  return (
    <section className={`bb-assessment bb-assessment--${tone}`} aria-label="성분 종합판정">
      <p className="bb-assessment__verdict">
        <span className="bb-assessment__dot" aria-hidden="true" />
        {assessment.verdictText}
      </p>

      {limitCount > 0 && (
        <p className="bb-assessment__note">배합한도가 있는 성분 {limitCount}개가 있어요(참고).</p>
      )}

      {checkCount > 0 && (
        <button type="button" className="bb-assessment__open" onClick={onOpenPanel}>
          확인 성분 {checkCount}개 보기 →
        </button>
      )}
    </section>
  );
}
