-- V60__ingredient_reg_flag.sql — 성분 규제/주의 플래그 참조 테이블(INCI 키 조회용).
-- 카탈로그(ingredient/goods_ingredient)와 분리한다: 이 표는 "어떤 INCI가 어느 공식 목록에 있는가"라는
-- 규제 사실의 조회 사전이며 제품에 종속되지 않는다. 판정 엔진은 제품 성분의 INCI를 이 표에 조인해
-- 종합판정을 파생한다(설계 §0.3). irritation_level/comedogenic 수기 값은 이 표로 대체되며 별도 단계에서 제거한다.
--
-- 출처: 식약처 화장품 사용제한 원료정보 API(COUNTRY_NAME='한국') + 착향제 알레르기 유발물질 25종 고시(별표2) + 내부 각질산 목록.
-- inci_name은 소문자·공백정규화된 조인 키다. 한 INCI가 여러 플래그를 가질 수 있어(예: 살리실릭애씨드=LIMIT+EXFOLIANT_ACID) UNIQUE를 걸지 않는다.
CREATE TABLE ingredient_reg_flag (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  inci_name   VARCHAR(255) NOT NULL,        -- 정규화된 INCI 영문명(조인 키). 255 초과는 서술형 금지명뿐이라 시드에서 제외.
  kr_name     VARCHAR(255) NULL,            -- 식약처 표준 한글명
  cas_no      VARCHAR(200) NULL,            -- CAS 번호(복수면 원문 그대로)
  flag_type   VARCHAR(20)  NOT NULL,        -- BANNED|LIMIT|ALLERGEN|EXFOLIANT_ACID
  source      VARCHAR(60)  NOT NULL,        -- MFDS_RESTRICT|MFDS_ALLERGEN_25|INTERNAL_ACID
  source_ref  TEXT         NULL,            -- 배합한도 원문(LIMIT_COND) 등 근거 문구(길이 제한 없음)
  KEY idx_reg_flag_inci (inci_name),
  KEY idx_reg_flag_type (flag_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
