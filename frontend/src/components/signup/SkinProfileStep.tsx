import type { AgeBand, Concern, SkinType } from '../../api/auth';
import './SkinProfileStep.css';

const SKIN_TYPES: { value: SkinType; label: string; desc: string }[] = [
  { value: 'DRY', label: '건성', desc: '당김·각질이 신경 쓰이는 편' },
  { value: 'OILY', label: '지성', desc: '번들거림·모공이 신경 쓰이는 편' },
  { value: 'COMBINATION', label: '복합성', desc: 'T존은 유분, 볼은 건조한 편' },
  { value: 'SENSITIVE', label: '민감성', desc: '자극·트러블에 예민한 편' },
];

const CONCERNS: { value: Concern; label: string }[] = [
  { value: 'PORE', label: '모공' },
  { value: 'TROUBLE', label: '트러블' },
  { value: 'WRINKLE', label: '주름' },
  { value: 'DARK_SPOT', label: '색소침착' },
];

const AGE_BANDS: AgeBand[] = ['10s', '20s', '30s', '40s', '50s+'];

interface SkinProfileStepProps {
  skinType: SkinType | undefined;
  concerns: Concern[];
  ageBand: AgeBand | undefined;
  onChangeSkinType: (value: SkinType) => void;
  onToggleConcern: (value: Concern) => void;
  onChangeAgeBand: (value: AgeBand) => void;
}

/**
 * 가입 2스텝 — 피부 프로필(선택 입력).
 * 값은 상위(Signup)에서 관리한다: 이 컴포넌트는 순수 표시·선택 UI만 담당.
 */
export function SkinProfileStep({
  skinType,
  concerns,
  ageBand,
  onChangeSkinType,
  onToggleConcern,
  onChangeAgeBand,
}: SkinProfileStepProps) {
  return (
    <div className="bb-skin-step">
      <fieldset className="bb-skin-step__group">
        <legend className="bb-skin-step__legend">피부타입</legend>
        <div className="bb-skin-step__type-grid">
          {SKIN_TYPES.map((type) => (
            <label
              key={type.value}
              className={`bb-skin-type-card${skinType === type.value ? ' bb-skin-type-card--active' : ''}`}
            >
              <input
                type="radio"
                name="skinType"
                value={type.value}
                checked={skinType === type.value}
                onChange={() => onChangeSkinType(type.value)}
              />
              <span className="bb-skin-type-card__label">{type.label}</span>
              <span className="bb-skin-type-card__desc">{type.desc}</span>
            </label>
          ))}
        </div>
      </fieldset>

      <fieldset className="bb-skin-step__group">
        <legend className="bb-skin-step__legend">고민 (중복 선택 가능)</legend>
        <div className="bb-skin-step__chip-row">
          {CONCERNS.map((concern) => {
            const active = concerns.includes(concern.value);
            return (
              <button
                key={concern.value}
                type="button"
                className={`bb-chip${active ? ' bb-chip--active' : ''}`}
                aria-pressed={active}
                onClick={() => onToggleConcern(concern.value)}
              >
                {concern.label}
              </button>
            );
          })}
        </div>
      </fieldset>

      <fieldset className="bb-skin-step__group">
        <legend className="bb-skin-step__legend">연령대</legend>
        <div className="bb-skin-step__chip-row">
          {AGE_BANDS.map((band) => (
            <button
              key={band}
              type="button"
              className={`bb-chip${ageBand === band ? ' bb-chip--active' : ''}`}
              aria-pressed={ageBand === band}
              onClick={() => onChangeAgeBand(band)}
            >
              {band}
            </button>
          ))}
        </div>
      </fieldset>
    </div>
  );
}
