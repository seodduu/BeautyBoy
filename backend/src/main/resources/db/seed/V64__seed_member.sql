-- V64__seed_member.sql
-- Wave4 Task 4-15: 시드 회원 3명 (admin 1 + 일반 2) — V66(리뷰)이 이 id를 참조하므로 고정한다.
-- 비밀번호는 셋 다 'seed1234!' — BCryptPasswordEncoder(기본 strength 10)로 생성한 해시.

-- =====================================================================
-- 1. 회원 3명 (id 1~3 고정)
-- =====================================================================
INSERT INTO member (id, email, password_hash, nickname, grade, status, role) VALUES
(1, 'admin@beautyboy.dev', '$2a$10$1/0jQRywlEZFFuzSbNRMmuxE9tWUZj82IVYsTL4yaXONVoWP2Pi5y', '운영자', 'BABY', 'ACTIVE', 'ADMIN'),
(2, 'dry@beautyboy.dev', '$2a$10$1/0jQRywlEZFFuzSbNRMmuxE9tWUZj82IVYsTL4yaXONVoWP2Pi5y', '건조맨', 'BABY', 'ACTIVE', 'USER'),
(3, 'oily@beautyboy.dev', '$2a$10$1/0jQRywlEZFFuzSbNRMmuxE9tWUZj82IVYsTL4yaXONVoWP2Pi5y', '지성맨', 'BABY', 'ACTIVE', 'USER');

-- =====================================================================
-- 2. member_profile — 일반 회원 둘만 (관리자는 피부타입 없어도 됨)
-- =====================================================================
INSERT INTO member_profile (member_id, skin_type, concerns, age_band) VALUES
(2, 'DRY', 'PORE,WRINKLE', '30s'),
(3, 'OILY', 'PORE,TROUBLE', '20s');

-- =====================================================================
-- 3. address — 일반 회원 둘의 기본배송지 1건씩 (is_default=1, TINYINT(1))
-- =====================================================================
INSERT INTO address (member_id, receiver, phone, zipcode, address1, address2, is_default) VALUES
(2, '건조맨', '010-1111-2222', '06134', '서울특별시 강남구 테헤란로 123', '101동 1001호', 1),
(3, '지성맨', '010-3333-4444', '04524', '서울특별시 중구 세종대로 110', '5층', 1);
