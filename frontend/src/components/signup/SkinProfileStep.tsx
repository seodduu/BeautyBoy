import type { AgeBand, Concern, SkinType } from '../../api/auth';
import { SkinProfileFields } from '../skin-profile/SkinProfileFields';
import './SkinProfileStep.css';

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
 * 실제 상수·마크업은 `SkinProfileFields`(마이페이지 프로필 탭과 공유)에 있다.
 * 값은 상위(Signup)에서 관리한다: 이 컴포넌트는 그 얇은 래퍼일 뿐이다.
 */
export function SkinProfileStep(props: SkinProfileStepProps) {
  return <SkinProfileFields {...props} className="bb-skin-fields--signup" />;
}
