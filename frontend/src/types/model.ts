/**
 * 개인화 v3 모델 파일 형식(설계 §4). 브라우저가 내려받아 읽는 두 파일의 타입과,
 * 그 파일이 전제하는 입력 순서 상수를 한곳에 모은다.
 *
 * 여기 있는 배열들은 학습(tools/recsys)과 추론(브라우저)이 같은 자리에 같은 값을 넣는다는
 * 약속이다. 순서가 어긋나면 계산은 조용히 성공하고 추천만 틀린다. 그래서 파일 안에도
 * 같은 배열을 실어 보내고, 다르면 클라이언트가 파일을 버린다.
 */

/** tag.sort_order 순. V71(1~10, 20~22) + V72(11~14, 30). 사용자 탑 behaviorTags 입력 순서이자 상품 탑 태그 입력 순서. */
export const MODEL_TAG_SLUGS = [
  'cleanse', 'exfoliate', 'sebum', 'soothe', 'moisture', 'uv', 'bright', 'firm', 'anti-aging', 'scalp',
  'pore', 'trouble', 'barrier', 'antioxidant',
  'fresh', 'dewy', 'matte',
  'gentle',
] as const;                                      // 18
export const MODEL_SKIN_TYPES = ['DRY', 'OILY', 'COMBINATION', 'SENSITIVE'] as const;   // 4
export const MODEL_CONCERNS = ['exfoliate', 'sebum', 'pore', 'trouble', 'soothe', 'moisture', 'barrier', 'bright', 'anti-aging'] as const; // 9, profile.ts CONCERN_SLUGS와 동일 순서
export const MODEL_TEXTURES = ['fresh', 'dewy', 'matte'] as const;                        // 3
export const USER_INPUT_DIM = 4 + 9 + 3 + 18;    // 34
export const EMB_DIM = 32;
export const HIDDEN_DIM = 48;

export interface ItemEmbeddingsFile {
  version: string;                       // ETag. user_tower.json과 같아야 한다
  dim: 32;
  trainedAt: string;                     // ISO 8601
  tagSlugs: readonly string[];           // MODEL_TAG_SLUGS와 같아야 한다. 다르면 클라이언트가 파일을 버린다
  items: Record<string, number[]>;       // key = goodsNo 문자열, value 길이 dim
  tagContribution: Record<string, Record<string, number>>;  // key = goodsNo, value = 태그 → 기여도(상위 4개만)
}

export interface UserTowerLayer {
  w: number[][];                         // [in][out]
  b: number[];                           // [out]
  act: 'relu' | 'none';
}

export interface UserTowerFile {
  version: string;
  dim: 32;
  inputOrder: {
    skinType: readonly string[];         // MODEL_SKIN_TYPES
    concerns: readonly string[];         // MODEL_CONCERNS
    textures: readonly string[];         // MODEL_TEXTURES
    behaviorTags: readonly string[];     // MODEL_TAG_SLUGS
  };
  layers: [UserTowerLayer, UserTowerLayer];   // [34→48 relu, 48→32 none]
  normalize: true;
}
