import { useEffect, useRef } from 'react';
import type { FlaggedIngredient } from '../../types/assessment';
import './CautionPanel.css';

interface CautionPanelProps {
  open: boolean;
  flagged: FlaggedIngredient[];
  onClose: () => void;
}

/** 플래그 → "왜 넣었나/누가 확인" 사람말(UX §8). 근거(sourceRef)는 별도로 함께 보여준다. */
function describe(f: FlaggedIngredient): { tag: string; why: string; who: string } {
  if (f.flags.includes('ALLERGEN')) {
    return { tag: '착향제', why: '향을 더하기 위해 들어갔어요.', who: '향에 예민하거나 알레르기가 있다면 확인하세요.' };
  }
  if (f.flags.includes('EXFOLIANT_ACID')) {
    return { tag: '각질 관리', why: '각질과 피지를 정리하기 위해 들어갔어요.', who: '민감하거나 건조한 피부에는 자극이 될 수 있어요.' };
  }
  return { tag: '참고', why: '배합한도가 정해진 성분이에요.', who: '실제 함량은 표시되지 않아 확인할 수 없어요.' };
}

/**
 * 확인 성분 패널(UX §7·§8). 데스크톱 우측 사이드패널 / 모바일 바텀시트(CSS 미디어쿼리).
 * role=dialog + aria-modal, Esc·닫기·오버레이로 닫힘, 열릴 때 포커스 이동·닫힐 때 트리거 복귀.
 */
export function CautionPanel({ open, flagged, onClose }: CautionPanelProps) {
  const closeRef = useRef<HTMLButtonElement>(null);
  const triggerRef = useRef<Element | null>(null);

  useEffect(() => {
    if (!open) return;
    triggerRef.current = document.activeElement;
    closeRef.current?.focus();

    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      // 닫힐 때 눌렀던 버튼으로 포커스 복귀(UX §7.2)
      if (triggerRef.current instanceof HTMLElement) triggerRef.current.focus();
    };
  }, [open, onClose]);

  if (!open) return null;

  const check = flagged.filter((f) => f.axis === 'CHECK');
  const info = flagged.filter((f) => f.axis === 'INFO');

  return (
    <div className="bb-caution-overlay" onClick={onClose}>
      <aside
        className="bb-caution"
        role="dialog"
        aria-modal="true"
        aria-label={`확인 성분 ${check.length}개`}
        onClick={(e) => e.stopPropagation()}
      >
        <header className="bb-caution__head">
          <h2 className="bb-caution__title">확인 성분 {check.length}개</h2>
          <button ref={closeRef} type="button" className="bb-caution__close" onClick={onClose}>
            닫기
          </button>
        </header>

        <div className="bb-caution__body">
          {check.map((f) => {
            const d = describe(f);
            return (
              <article key={f.ingredientId} className="bb-caution__item">
                <p className="bb-caution__name">
                  {f.name} <span className="bb-caution__tag">{d.tag}</span>
                </p>
                <p className="bb-caution__line">{d.why}</p>
                <p className="bb-caution__line">{d.who}</p>
                {f.sourceRef && <p className="bb-caution__ref">근거: {f.sourceRef}</p>}
              </article>
            );
          })}

          {info.length > 0 && (
            <section className="bb-caution__info-group">
              <h3 className="bb-caution__subtitle">배합한도가 있는 성분(참고)</h3>
              {info.map((f) => (
                <article key={f.ingredientId} className="bb-caution__item">
                  <p className="bb-caution__name">{f.name}</p>
                  {f.sourceRef && <p className="bb-caution__ref">근거: {f.sourceRef}</p>}
                </article>
              ))}
            </section>
          )}
        </div>
      </aside>
    </div>
  );
}
