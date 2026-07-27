-- V81__migrate_concerns_to_tag_slug.sql
-- member_profile.concerns의 구 어휘(PORE/TROUBLE/WRINKLE/DARK_SPOT)를 tag.slug 체계로 옮긴다.
-- 값 집합만 바뀌고 컬럼 타입·제약은 그대로다(설계 §4).
-- WRINKLE→anti-aging, DARK_SPOT→bright: 구 어휘가 증상명이고 신 어휘가 효과명이라 1:1이 아니지만,
-- 그 증상을 겨냥한 태그가 각각 하나뿐이라 모호함이 없다.
UPDATE member_profile SET concerns = REPLACE(concerns, 'PORE',       'pore')       WHERE concerns LIKE '%PORE%';
UPDATE member_profile SET concerns = REPLACE(concerns, 'TROUBLE',    'trouble')    WHERE concerns LIKE '%TROUBLE%';
UPDATE member_profile SET concerns = REPLACE(concerns, 'WRINKLE',    'anti-aging') WHERE concerns LIKE '%WRINKLE%';
UPDATE member_profile SET concerns = REPLACE(concerns, 'DARK_SPOT',  'bright')     WHERE concerns LIKE '%DARK_SPOT%';
