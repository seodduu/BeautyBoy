-- 상품명 전문 검색 인덱스. 한글은 공백 단위 토큰이 의미가 없어 ngram 파서를 쓴다(설계 8장).
-- ngram_token_size 기본값이 2이므로 2자 미만 검색어는 어떤 것도 매칭되지 않는다 —
-- 그래서 서비스가 2자 미만을 SEARCH_QUERY_TOO_SHORT(400)로 먼저 끊는다.
--
-- 브랜드명이 이 인덱스에 없는 이유: FULLTEXT는 한 테이블 안에서만 걸린다.
-- 브랜드명 매칭은 조인 후 LIKE로 보완한다(T1-3). 브랜드 수가 수십 개라 LIKE로 충분하다.
ALTER TABLE goods ADD FULLTEXT INDEX ft_goods_name (name) WITH PARSER ngram;
