/**
 * 스킨케어 루틴 5단계 ↔ 카테고리 매핑.
 *
 * 이 상수가 매핑의 유일한 진실이다. 카테고리 트리로 순서를 표현하지 않는다 —
 * 카테고리는 "무엇인가"(분류), 루틴은 "언제 바르는가"(순서)로 축이 다르고,
 * 하나로 합치면 둘 다 망가진다. (docs/plans/2026-07-23-landing-main-composition.md 2장)
 *
 * 단계 깊이가 섞이는 것은 의도적이다: 클렌징은 대분류 하나가 곧 한 단계지만,
 * 스킨케어(C001)는 대분류 하나 안에 3단계가 들어 있다.
 *
 * CLAUDE.md "돈과 재고는 서버, 취향은 클라이언트"에 해당하므로 프론트 상수로 시작하고,
 * 필요해지면 Wave 3의 routine 도메인으로 옮긴다.
 */
export interface RoutineStep {
  /** 앵커 id이자 React key. 네비의 href="#{id}" 타깃이므로 바꾸면 네비가 깨진다. */
  id: string;
  /** 화면에 "STEP 01"로 표기되는 1-based 순번. */
  order: number;
  label: string;
  /** 실 시드(backend V12__seed_catalog.sql) 코드. 접두사 필터로 하위까지 포함된다. */
  categoryCode: string;
  /** 이 단계가 왜 필요한지 한 줄로. 타겟이 "뭘 사야 할지 모르는 남성"이라 순서 자체가 교육이 된다. */
  copy: string;
  /** public/ 기준 절대경로. 외부 URL을 직접 참조하지 않는다(오프라인·CI에서 깨진다). */
  image: string;
}

export const ROUTINE_STEPS: readonly RoutineStep[] = [
  {
    id: 'cleansing',
    order: 1,
    label: '클렌징',
    categoryCode: 'C002',
    copy: '하루 동안 쌓인 피지와 먼지를 씻어냅니다. 무엇을 바르든, 비우는 것이 먼저입니다.',
    image: '/images/routine/01-cleansing.jpg',
  },
  {
    id: 'toner',
    order: 2,
    label: '토너/스킨',
    categoryCode: 'C001001',
    copy: '세안 직후 흐트러진 피부 결을 정돈하고, 다음 단계가 잘 스며들 바탕을 만듭니다.',
    image: '/images/routine/02-toner.jpg',
  },
  {
    id: 'serum',
    order: 3,
    label: '에센스/세럼',
    categoryCode: 'C001002',
    copy: '고민을 정면으로 겨냥하는 단계입니다. 보습·미백·탄력 중 지금 필요한 하나를 고르세요.',
    image: '/images/routine/03-serum.jpg',
  },
  {
    id: 'cream',
    order: 4,
    label: '로션/크림',
    categoryCode: 'C001003',
    copy: '앞 단계에서 채운 수분이 날아가지 않게 덮어 잠급니다. 유분감은 취향껏 조절하세요.',
    image: '/images/routine/04-cream.jpg',
  },
  {
    id: 'suncare',
    order: 5,
    label: '선크림',
    categoryCode: 'C004001',
    copy: '아침 루틴의 마지막. 자외선 차단을 건너뛰면 앞의 네 단계가 하는 일이 절반으로 줄어듭니다.',
    image: '/images/routine/05-suncare.jpg',
  },
];

/** 섹션당 노출 상품 수. 한 줄(데스크톱 4칼럼)만 보여주고 나머지는 더보기로 넘긴다. */
export const ROUTINE_SECTION_SIZE = 4;
