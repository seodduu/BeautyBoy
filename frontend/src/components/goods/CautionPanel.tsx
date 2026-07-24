import { useEffect, useRef } from 'react';
import type { FlaggedIngredient } from '../../types/assessment';
import './CautionPanel.css';

interface CautionPanelProps {
  open: boolean;
  flagged: FlaggedIngredient[];
  onClose: () => void;
}

/** 플래그 → 배지 라벨(3.2). 착향제/각질산/배합한도. */
const BADGE: Record<string, string> = {
  ALLERGEN: '착향제',
  EXFOLIANT_ACID: '각질산',
  LIMIT: '배합한도',
  BANNED: '검토',
};

/** 플래그 → 평가 근거 라벨(3.3). 분류(AHA 계열)와 구분되는 실제 출처. */
const SOURCE: Record<string, string> = {
  ALLERGEN: '식약처 착향제 알레르기 유발물질 25종',
  LIMIT: '식약처 화장품 사용제한 원료정보',
  EXFOLIANT_ACID: '자체 각질산 분류 기준 v1.0',
  BANNED: '식약처 화장품 사용제한 원료정보',
};

/** 왜 넣었나 / 누가 확인(UX §8). 대표 플래그 기준(착향제 > 각질산 > 한도). */
function whyWho(f: FlaggedIngredient): { why: string; who: string } {
  if (f.flags.includes('ALLERGEN')) {
    return { why: '향을 더하기 위해 들어갔어요.', who: '향에 예민하거나 알레르기가 있다면 확인하세요.' };
  }
  if (f.flags.includes('EXFOLIANT_ACID')) {
    return { why: '각질과 피지를 정돈하기 위해 들어갔어요.', who: '민감하거나 건조한 피부는 자극을 느낄 수 있어요.' };
  }
  return { why: '배합한도가 정해진 성분이에요.', who: '실제 함량은 표시되지 않아 확인할 수 없어요.' };
}

/**
 * 확인 성분 패널(UX §7·§8). 데스크톱 우측 사이드패널 / 모바일 바텀시트.
 * role=dialog + aria-modal, Esc·닫기·오버레이로 닫힘, 열릴 때 포커스 이동·닫힐 때 트리거 복귀,
 * 열려 있는 동안 배경 스크롤 잠금(6.4).
 */
export function CautionPanel({ open, flagged, onClose }: CautionPanelProps) {
  const closeRef = useRef<HTMLButtonElement>(null);
  const triggerRef = useRef<Element | null>(null);

  useEffect(() => {
    if (!open) return;
    triggerRef.current = document.activeElement;
    closeRef.current?.focus();

    // 배경 페이지 스크롤 잠금(6.4)
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = prevOverflow;
      if (triggerRef.current instanceof HTMLElement) triggerRef.current.focus();
    };
  }, [open, onClose]);

  if (!open) return null;

  const check = flagged.filter((f) => f.axis === 'CHECK');
  const info = flagged.filter((f) => f.axis === 'INFO');
  const titleId = 'bb-caution-title';

  return (
    <div className="bb-caution-overlay" onClick={onClose}>
      <aside
        className="bb-caution"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onClick={(e) => e.stopPropagation()}
      >
        <header className="bb-caution__head">
          <h2 id={titleId} className="bb-caution__title">
            확인이 필요한 성분 {check.length}개
          </h2>
          <button ref={closeRef} type="button" className="bb-caution__close" onClick={onClose}>
            닫기
          </button>
        </header>

        <div className="bb-caution__body">
          {check.map((f) => {
            const d = whyWho(f);
            const classification = f.acidClass ? `${f.acidClass} 계열` : null;
            return (
              <article key={f.ingredientId} className="bb-caution__item">
                <p className="bb-caution__name">
                  {f.name}
                  {f.flags.map((flag) =>
                    BADGE[flag] ? (
                      <span key={flag} className="bb-caution__badge">
                        {BADGE[flag]}
                      </span>
                    ) : null,
                  )}
                </p>
                {classification && <p className="bb-caution__class">분류: {classification}</p>}
                {f.summary && <p className="bb-caution__summary">{f.summary}</p>}

                <p className="bb-caution__q">왜 들어갔나요?</p>
                <p className="bb-caution__line">{d.why}</p>
                <p className="bb-caution__q">누가 확인해야 하나요?</p>
                <p className="bb-caution__line">{d.who}</p>

                <details className="bb-caution__evidence">
                  <summary>평가 근거 보기</summary>
                  <ul className="bb-caution__sources">
                    {f.flags.map((flag) =>
                      SOURCE[flag] ? <li key={flag}>{SOURCE[flag]}</li> : null,
                    )}
                    {f.limitText && <li className="bb-caution__limit">{f.limitText}</li>}
                  </ul>
                </details>
              </article>
            );
          })}

          {info.length > 0 && (
            <section className="bb-caution__info-group">
              <h3 className="bb-caution__subtitle">배합한도가 있는 성분(참고)</h3>
              {info.map((f) => (
                <article key={f.ingredientId} className="bb-caution__item">
                  <p className="bb-caution__name">
                    {f.name}
                    <span className="bb-caution__badge">배합한도</span>
                  </p>
                  {f.summary && <p className="bb-caution__summary">{f.summary}</p>}
                  <details className="bb-caution__evidence">
                    <summary>평가 근거 보기</summary>
                    <ul className="bb-caution__sources">
                      <li>{SOURCE.LIMIT}</li>
                      {f.limitText && <li className="bb-caution__limit">{f.limitText}</li>}
                    </ul>
                  </details>
                </article>
              ))}
            </section>
          )}

          <p className="bb-caution__disclaimer">
            성분 정보는 성분 표시와 식약처 공개자료를 바탕으로 한 참고 안내이며, 목록에 없다고 자극이
            없다는 뜻은 아니고 의학적 판단이 아닙니다.
          </p>
        </div>
      </aside>
    </div>
  );
}
