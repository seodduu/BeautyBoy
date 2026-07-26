-- V75__seed_routine_flow_rule.sql — 전이 규칙 12행. reason이 화면 문구의 유일한 출처.
-- priority: BUFFER=10 < NEXT_STEP=20 — 같은 상품에 둘 다 매칭되면 완충이 이긴다(설계 §4).
--
-- 재산정 메모(2026-07-27, Task 5): 계획서(docs/plans/2026-07-26-next-step-recommendation.md)의
-- 시드 근거는 V71(태그 확장 전) 기준이라 낡았다는 주의문이 있어, V72(is_key 조건 제거 + 신규
-- pore/trouble/barrier/antioxidant/gentle 5종) 반영 후 임시 MySQL(13310)에 V1~V74 clean 로드해
-- 실 goods_tag·goods_ingredient 데이터로 12행 전체를 재검증했다. 결론: **행 구성은 계획서와 동일하게
-- 유지한다** — 아래가 그 근거다.
--   - 각 행의 from_category_code(중분류, 7자)·from_tag_slug 조합이 여전히 그 카테고리의 실제
--     의미(예: C002001 exfoliate = BHA/AHA 함유 딥클렌징폼, C002003 exfoliate = 필링/스크럽 대부분)와
--     맞다. V72로 보조성분도 태그에 반영됐지만 이 조합이 가리키는 상품 집합은 취지에서 벗어나지 않았다.
--   - 게이트1(중분류만 사용) 준수: 12행 전부 7자 코드.
--   - 게이트2(순방향·PAIRED_REMOVAL의 to_category_code 비중복) 재확인: 같은 from에 걸리는 forward/removal
--     쌍은 (C004001→C004003 NEXT_STEP) / (C004001→C002002 PAIRED_REMOVAL)뿐이고 to_category가 다르다.
--   - 대표 데모(goods 2, AHA 토너)는 이제 soothe 태그 세럼이 4개 이상 존재해(V72 전엔 0개라 폴백에
--     의존) 태그 매칭만으로 4개가 즉시 채워진다. 그중 goods 159(RETINOID)·190(BHA)는 goods 2(AHA)와
--     각각 CONFLICT(AHA×RETINOID, AHA×BHA)라 게이트에서 빠지고 [133, 4]만 남는다 — goods 5는 애초에
--     태그 매칭 후보에 들지 않지만(soothe 태그 없음) 폴백이 발동하지 않아 후보에 오르지도 않으므로
--     여전히 부재는 유지된다. goods 21(무기자차선크림)의 2블록(NEXT_STEP+PAIRED_REMOVAL) 구조도 그대로
--     성립한다.
--   - 실측 커버리지는 Task 5 IT의 세 번째 테스트가 출력한다(보고서에 기록).
INSERT INTO routine_flow_rule
  (from_category_code, from_tag_slug, to_category_code, to_tag_slug, edge_kind, reason, priority) VALUES
('C002001', 'exfoliate', 'C001001', 'soothe',   'BUFFER',
 '피지·각질까지 씻어낸 다음엔 진정 토너로 완충해 주세요', 10),
('C002001', NULL,        'C001001', 'moisture', 'NEXT_STEP',
 '세안 다음 단계는 수분 충전이에요', 20),
('C002002', NULL,        'C001001', 'moisture', 'NEXT_STEP',
 '지운 다음엔 수분 토너로 결부터 정돈하세요', 20),
('C002003', 'exfoliate', 'C001001', 'soothe',   'BUFFER',
 '각질 케어 다음엔 진정 성분으로 완충하는 게 좋아요', 10),
('C001001', 'exfoliate', 'C001002', 'soothe',   'BUFFER',
 '각질 토너 다음 단계는 진정 세럼으로 완충하세요', 10),
('C001001', NULL,        'C001002', NULL,       'NEXT_STEP',
 '결을 정돈했다면 영양을 채울 차례예요', 20),
('C001002', NULL,        'C001003', 'moisture', 'NEXT_STEP',
 '세럼의 수분을 크림으로 덮어 가두세요', 20),
('C001003', NULL,        'C004001', 'uv',       'NEXT_STEP',
 '아침 루틴의 마지막은 자외선 차단이에요', 20),
('C004001', NULL,        'C004003', 'soothe',   'NEXT_STEP',
 '햇빛을 본 날엔 진정 케어로 마무리하세요', 20),
('C004001', NULL,        'C002002', 'cleanse',  'PAIRED_REMOVAL',
 '자외선차단제는 클렌징오일로 지워야 남지 않아요', 10),
('C004002', NULL,        'C002002', 'cleanse',  'PAIRED_REMOVAL',
 '선스틱도 저녁엔 오일 클렌징으로 지워 주세요', 10),
('C005002', NULL,        'C005003', NULL,       'NEXT_STEP',
 '면도 다음엔 진정 제품으로 마무리하세요', 20);
