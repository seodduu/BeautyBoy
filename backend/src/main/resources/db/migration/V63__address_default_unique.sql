-- V63__address_default_unique.sql
-- 기본배송지는 회원당 하나여야 한다. UNIQUE(member_id, is_default)는 is_default=0이 여럿이라 못 건다.
-- MySQL 8에는 부분 인덱스가 없으므로 "기본일 때만 member_id, 아니면 NULL"인 생성 컬럼에 UNIQUE를 건다.
-- UNIQUE 인덱스는 NULL 중복을 허용하므로 비기본 주소는 몇 개든 공존한다.

-- 제약을 걸기 전에 기존 위반 데이터를 정리한다. 가장 최근(id 최대) 한 건만 기본으로 남긴다 —
-- AddressRepository.findFirstByMemberIdAndIsDefaultTrueOrderByIdDesc 가 이미 쓰는 기준과 같다.
UPDATE address a
JOIN (
  SELECT member_id, MAX(id) AS keep_id
  FROM address WHERE is_default = 1 GROUP BY member_id
) k ON a.member_id = k.member_id
SET a.is_default = 0
WHERE a.is_default = 1 AND a.id <> k.keep_id;

ALTER TABLE address
  ADD COLUMN default_member_id BIGINT
    GENERATED ALWAYS AS (CASE WHEN is_default = 1 THEN member_id ELSE NULL END) STORED,
  ADD CONSTRAINT uq_address_default UNIQUE (default_member_id);
