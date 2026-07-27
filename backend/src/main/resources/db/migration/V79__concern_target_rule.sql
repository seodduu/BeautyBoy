-- V79__concern_target_rule.sql — 고민(프로필 태그) → 목표 단계 규칙. 설계 §5.1.
-- 프로필만 있는 티어1에는 앵커 상품이 없어 routine_flow_rule의 from이 성립하지 않는다.
-- reason은 DB가 유일한 출처라는 원칙(next-step 설계 §3)을 지키려고 문구까지 데이터로 둔다.
-- tag slug·category code에 물리 FK를 걸지 않는다(패키지 경계 너머 물리 FK 금지 관례) —
-- 오타는 ConcernTargetRuleSeedIT가 잡는다.
CREATE TABLE concern_target_rule (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  concern_tag_slug  VARCHAR(40)  NOT NULL,   -- 프로필 고민 슬러그. tag.slug와 같은 어휘(물리 FK는 걸지 않는다)
  to_category_code  VARCHAR(12)  NOT NULL,   -- 중분류 7자. goods.category_code(leaf 10자)는 접두사 매칭
  to_tag_slug       VARCHAR(40)  NOT NULL,   -- 추천 대상이 가져야 할 태그
  reason            VARCHAR(200) NOT NULL,   -- 화면에 그대로 나가는 문구. 유일한 출처
  priority          INT          NOT NULL DEFAULT 0,  -- 낮을수록 우선
  CONSTRAINT uq_concern_target UNIQUE (concern_tag_slug, to_category_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 시드: 고민 9종 + gentle = 슬러그 10개. gentle은 프로필에서 직접 고를 수 없지만 SENSITIVE
-- 피부타입의 파생 태그(설계 §6.2)라 규칙이 없으면 파생 태그 하나가 통째로 무효가 된다.
--
-- 이 행들은 카테고리 의미(V12)와 태그 파생 규칙(V72)에서 유도한 뒤, ConcernTargetRuleSeedIT를
-- 실 MySQL(V1~V79 clean 로드)에 돌려 "(to_category_code 접두사 × to_tag_slug) 비HIDDEN 상품 4개 이상"을
-- 실측으로 확인해 확정했다. 후보가 4개 미만이던 행은 태그를 끼워 맞추지 않고 삭제했다 —
-- 후보가 안 나오는 규칙은 화면에서 폴백만 유발한다. 삭제 내역은 gentle 행 위 주석에 남겼다.
-- 실측 최소는 trouble→C001002/trouble의 4개, 최대는 bright→C004001/uv의 23개다.
INSERT INTO concern_target_rule
  (concern_tag_slug, to_category_code, to_tag_slug, reason, priority) VALUES
('exfoliate',  'C002003', 'exfoliate',  '각질이 고민이라면 주 1~2회 필링부터 시작하세요', 10),
('exfoliate',  'C001001', 'soothe',     '각질 케어 뒤엔 진정 토너로 완충해 주세요', 20),
('sebum',      'C002001', 'sebum',      '피지가 고민이라면 세안부터 피지 잡는 제품으로', 10),
('sebum',      'C001001', 'sebum',      '세안 뒤 유분 정돈까지 이어가세요', 20),
('pore',       'C001002', 'pore',       '모공은 세럼 단계에서 정면으로 잡는 게 효율적이에요', 10),
('pore',       'C002001', 'pore',       '모공 관리는 잘 씻어내는 것부터예요', 20),
('trouble',    'C001002', 'trouble',    '트러블이 고민이라면 세럼으로 집중 관리하세요', 10),
('trouble',    'C002001', 'trouble',    '트러블 피부일수록 세정 단계 선택이 중요해요', 20),
('soothe',     'C001001', 'soothe',     '예민한 날엔 진정 토너로 결부터 달래 주세요', 10),
('soothe',     'C001002', 'soothe',     '진정 성분 세럼으로 한 겹 더 얹어 보세요', 20),
('moisture',   'C001003', 'moisture',   '보습이 고민이라면 덮어 가두는 크림이 핵심이에요', 10),
('moisture',   'C001002', 'moisture',   '크림 전에 수분 세럼으로 채워 두세요', 20),
('barrier',    'C001003', 'barrier',    '장벽이 무너졌다면 크림으로 지붕부터 올리세요', 10),
('barrier',    'C001002', 'barrier',    '세라마이드 계열 세럼으로 장벽을 채워 보세요', 20),
('bright',     'C001002', 'bright',     '톤이 고민이라면 브라이트닝 세럼이 출발점이에요', 10),
('bright',     'C004001', 'uv',         '미백 관리의 절반은 자외선 차단이에요', 20),
('anti-aging', 'C001002', 'anti-aging', '주름 관리는 세럼 단계에서 시작하세요', 10),
('anti-aging', 'C001003', 'anti-aging', '고영양 크림으로 마무리하면 더 오래 갑니다', 20),
-- gentle의 짝이던 ('gentle','C002001','gentle', '민감한 피부일수록 순한 세정부터예요', 10)은
-- 실측 후보 2개(4 미만)라 삭제했다. gentle 태그가 붙은 클렌저가 아직 그만큼 없다.
-- 아래 C001001 행(5개)이 남아 있어 SENSITIVE 파생 태그 자체는 여전히 유효하다(설계 §6.2).
('gentle',     'C001001', 'gentle',     '자극 없는 토너로 결만 정돈해 주세요', 20);
