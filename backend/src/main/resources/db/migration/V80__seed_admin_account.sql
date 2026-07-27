-- V80__seed_admin_account.sql
-- 로컬 개발·시연용 고정 관리자 계정. compose 볼륨을 초기화해도 이 계정은 다시 생긴다.
--
-- 비밀번호는 'admin' — BCryptPasswordEncoder(기본 strength 10) 해시다.
-- 회원가입 폼의 비밀번호 규칙을 통과하지 못하는 값이지만, 시드는 서비스 계층을 거치지
-- 않고 직접 INSERT하므로 문제가 없다. 짧고 외우기 쉬운 것이 이 계정의 목적이다.
--
-- 주의: 로컬 전용 편의 계정이다. 실제 배포 환경에는 이 마이그레이션이 들어가면 안 된다.
--       (V64의 시드 회원 3명도 같은 성격이다 — 비밀번호 'seed1234!')
--
-- id는 V64가 고정한 시드 회원(1~3), 그리고 앞으로 시드가 쓸 낮은 번호대와 겹치지 않도록
-- 100으로 못 박는다.
INSERT INTO member (id, email, password_hash, nickname, grade, status, role) VALUES
(100, 'admin@naver.com', '$2a$10$euztbB2YUzKjZS/LOoZbZODXwHwUMkzX74w4IyVExtTjdkupL54C6',
 '관리자', 'BABY', 'ACTIVE', 'ADMIN');
