// tools/loadtest/browse.js — 시나리오 ②: 조회 혼합 70/20/10.
//
// 엔드포인트/쿼리 파라미터는 backend/src/main/java/com/beautyboy/{ranking,catalog,compat} 컨트롤러
// 실물 기준(모두 /api/v1 접두사, 인증 불필요 — 전부 공개 조회 엔드포인트):
//   - GET /api/v1/rankings                              (categoryCode 생략 = 전체 랭킹)
//   - GET /api/v1/goods?categoryCode=&page=&size=        (목록, categoryCode는 카테고리 leaf 코드)
//   - GET /api/v1/compat/verdicts?base=&candidates=      (기준상품 1개 대 후보 N개 궁합 배치 판정)
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

// 부하 모형 숫자·비율은 계약이다 — before/after 비교가 깨지므로 절대 바꾸지 않는다.
export const options = {
  scenarios: {
    browse: { executor: 'constant-vus', vus: 100, duration: '2m' },
  },
  thresholds: { http_req_failed: ['rate<0.01'] },
};

export default function () {
  const r = Math.random();
  let res;
  if (r < 0.7) {
    res = http.get(`${BASE}/api/v1/rankings`);                                          // 70% 랭킹(전체)
  } else if (r < 0.9) {
    res = http.get(`${BASE}/api/v1/goods?categoryCode=${__ENV.CATEGORY_CODE || 'C002001001'}&page=0`); // 20% 목록
  } else {
    res = http.get(`${BASE}/api/v1/compat/verdicts?base=${__ENV.GOODS_A}&candidates=${__ENV.GOODS_B}`); // 10% 궁합
  }
  check(res, { '2xx': (x) => x.status >= 200 && x.status < 300 });
}
