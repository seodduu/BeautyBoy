import { useId } from 'react';
import type { AgeBand, Concern, ConcernSlug, SkinType, TextureSlug } from '../../api/auth';
import './SkinProfileFields.css';

/**
 * 피부타입·고민·연령대 상수 — 가입 2스텝(`Signup`)과 마이페이지 프로필(`MyProfile`)이
 * 공유하는 유일한 출처. 옵션 문구를 바꿀 일이 있으면 이 파일 하나만 고치면 된다.
 */
export const SKIN_TYPES: { value: SkinType; label: string; desc: string }[] = [
  { value: 'DRY', label: '건성', desc: '당김·각질이 신경 쓰이는 편' },
  { value: 'OILY', label: '지성', desc: '번들거림·모공이 신경 쓰이는 편' },
  { value: 'COMBINATION', label: '복합성', desc: 'T존은 유분, 볼은 건조한 편' },
  { value: 'SENSITIVE', label: '민감성', desc: '자극·트러블에 예민한 편' },
];

/**
 * 고민 9종 — 값은 `tag.slug`, 표시명은 `tag.name`과 같게 맞춘다(설계 §4.1).
 * 프로필에서 고른 "모공"과 상품 카드의 "모공" 태그가 같은 어휘·같은 색이 되어
 * 프로필과 상품이 시각적으로 이어진다.
 */
export const CONCERNS: { value: ConcernSlug; label: string }[] = [
  { value: 'exfoliate', label: '각질' },
  { value: 'sebum', label: '피지' },
  { value: 'pore', label: '모공' },
  { value: 'trouble', label: '트러블' },
  { value: 'soothe', label: '진정' },
  { value: 'moisture', label: '보습' },
  { value: 'barrier', label: '장벽' },
  { value: 'bright', label: '브라이트닝' },
  { value: 'anti-aging', label: '안티에이징' },
];

/**
 * 선호 사용감 3종 — 화면에서는 별도 `fieldset`이지만 값은 고민과 **같은 `concerns` 배열**에
 * 담긴다. 서버가 문자열 리스트로만 다루므로 컬럼을 더 만들 이유가 없고, 그룹 구분은
 * 이 상수 두 벌이 맡는다(설계 §4.1).
 */
export const TEXTURES: { value: TextureSlug; label: string }[] = [
  { value: 'fresh', label: '산뜻함' },
  { value: 'dewy', label: '촉촉함' },
  { value: 'matte', label: '매트' },
];

export const AGE_BANDS: AgeBand[] = ['10s', '20s', '30s', '40s', '50s+'];

interface SkinProfileFieldsProps {
  skinType: SkinType | undefined;
  concerns: Concern[];
  ageBand: AgeBand | undefined;
  onChangeSkinType: (value: SkinType) => void;
  onToggleConcern: (value: Concern) => void;
  onChangeAgeBand: (value: AgeBand) => void;
  /**
   * 호스트 페이지가 붙이는 부가 클래스. 카드 그리드를 1열로 접는 뷰포트 기준선은
   * 가입 2스텝(좁은 인증 카드)과 마이페이지(사이드바 레이아웃이 통째로 접히는 900px
   * 기준선)가 서로 달라 — 컴포넌트 내부에서 하나로 못 정한다. 각 호스트의 CSS 파일이
   * 이 클래스를 셀렉터로 자기 기준선의 미디어쿼리를 얹는다.
   */
  className?: string;
}

/**
 * 피부타입/고민/연령대 선택 UI — 가입 2스텝과 마이페이지 프로필 탭이 공유하는 공용 컴포넌트.
 * 값은 항상 상위(Signup/MyProfile)에서 관리한다: 이 컴포넌트는 순수 표시·선택 UI만 담당.
 *
 * 접근성 이름 설계: 피부타입 카드는 `<label>`이 라벨("건성")과 설명("당김·각질이…")을
 * 함께 감싸므로, 아무 처리도 없으면 라디오의 접근성 이름이 "건성 당김·각질이 신경 쓰이는
 * 편"까지 늘어난다. `aria-labelledby`로 라벨 span의 id만 명시해 이름을 "건성"으로 고정하고,
 * `aria-describedby`로 설명 span을 연결해 스크린리더가 이름 뒤에 설명을 "description"으로
 * 더 읽어주게 한다 — 이전 두 구현보다 낫다: 원본(SkinProfileStep)은 이름이 너무 길었고,
 * 마이페이지 전용 구현은 `aria-label`로 이름을 덮어써 설명 문구 자체가 스크린리더에서
 * 통째로 사라졌다. `aria-labelledby` + `aria-describedby` 조합은 이름은 짧게, 설명은
 * 그대로 노출한다. 카드 전체가 `<label>`이므로 클릭 가능 영역(설명 문구 포함)은 그대로다.
 */
export function SkinProfileFields({
  skinType,
  concerns,
  ageBand,
  onChangeSkinType,
  onToggleConcern,
  onChangeAgeBand,
  className,
}: SkinProfileFieldsProps) {
  const idPrefix = useId();

  /**
   * 고민·사용감 칩은 마크업이 완전히 같고 값 집합만 다르다 — 한 곳에서 그린다.
   * `onToggleConcern` 하나로 두 그룹을 모두 처리하므로 상위는 배열 하나만 다룬다.
   */
  const renderChip = (value: Concern, label: string) => {
    const active = concerns.includes(value);
    return (
      <button
        key={value}
        type="button"
        className={`bb-chip bb-chip--${value}${active ? ' bb-chip--active bb-chip--on' : ''}`}
        aria-pressed={active}
        onClick={() => onToggleConcern(value)}
      >
        {/* ✓는 색에 얹는 보강 단서다. 상태는 aria-pressed가 이미 알리므로 이름에서 감춘다. */}
        {active && (
          <span className="bb-chip__check" aria-hidden="true">
            ✓
          </span>
        )}
        {label}
      </button>
    );
  };

  return (
    <div className={`bb-skin-fields${className ? ` ${className}` : ''}`}>
      <fieldset className="bb-skin-fields__group">
        <legend className="bb-skin-fields__legend">피부타입</legend>
        <div className="bb-skin-fields__type-grid">
          {SKIN_TYPES.map((type) => {
            const labelId = `${idPrefix}-${type.value}-label`;
            const descId = `${idPrefix}-${type.value}-desc`;
            return (
              <label
                key={type.value}
                className={`bb-skin-type-card bb-skin-type-card--${type.value.toLowerCase()}${
                  skinType === type.value ? ' bb-skin-type-card--active' : ''
                }`}
              >
                <input
                  type="radio"
                  name="skinType"
                  value={type.value}
                  checked={skinType === type.value}
                  onChange={() => onChangeSkinType(type.value)}
                  aria-labelledby={labelId}
                  aria-describedby={descId}
                />
                <span id={labelId} className="bb-skin-type-card__label">
                  {type.label}
                </span>
                <span id={descId} className="bb-skin-type-card__desc">
                  {type.desc}
                </span>
                {skinType === type.value && (
                  <span className="bb-skin-type-card__check" aria-hidden="true">
                    ✓
                  </span>
                )}
              </label>
            );
          })}
        </div>
      </fieldset>

      <fieldset className="bb-skin-fields__group">
        <legend className="bb-skin-fields__legend">고민 (중복 선택 가능)</legend>
        <div className="bb-skin-fields__chip-row">
          {CONCERNS.map((concern) => renderChip(concern.value, concern.label))}
        </div>
      </fieldset>

      <fieldset className="bb-skin-fields__group">
        <legend className="bb-skin-fields__legend">선호하는 사용감 (중복 선택 가능)</legend>
        <div className="bb-skin-fields__chip-row">
          {TEXTURES.map((texture) => renderChip(texture.value, texture.label))}
        </div>
      </fieldset>

      <fieldset className="bb-skin-fields__group">
        <legend className="bb-skin-fields__legend">연령대</legend>
        <div className="bb-skin-fields__chip-row">
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
