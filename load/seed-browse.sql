-- 조회 부하 시드: 공구를 @N 건 넣는다. 앱을 한 번 띄워 Flyway 마이그레이션이 적용된 DB 에서 쓴다.
--   (echo "SET @N=10000, @SCHOOLS=20;"; cat load/seed-browse.sql) | docker exec -i <mysql 컨테이너> mysql --default-character-set=utf8mb4 -uroot -ppw camgong
--   (문자셋을 빼면 한글 제목이 깨져 들어가 검색 측정이 의미 없어진다)
-- 학교(009): 개설자 회원 1~@SCHOOLS 가 각자 s01.ac.kr, s02.ac.kr … 학교다. 공구는 연속 20건 묶음 단위로 학교를 돌아가며 연다
-- (상태·카테고리·제목은 n % 20·8·6 이라, 학교를 n % @SCHOOLS 로 고르면 학교마다 상태가 한쪽으로 쏠린다). 학교마다 약 @N/@SCHOOLS 건.
-- 로그인 목록 측정용 세션: 회원 1(s01.ac.kr)의 쿠키 값은 "bench-session-1" 이다.
-- 회원·세션·공구·참여를 모두 지우고 다시 넣는다. 측정용 DB 에서만 쓴다.
-- 상태·카테고리·마감 시각을 분산하고, 제목에 흔한 단어와 드문 단어('희귀템', 3000건에 1건)를 섞는다.
-- 마감은 앱 시계(실제 시각) 기준이라 UTC_TIMESTAMP 에서 상대적으로 만든다.
SET SESSION cte_max_recursion_depth = 100000;

DELETE FROM participation;
DELETE FROM group_buy;
DELETE FROM login_session;
DELETE FROM member;

INSERT INTO member (id, name, display_name_key, email, email_key, password_hash, consent_at, consent_version, created_at, school)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @SCHOOLS)
SELECT n, CONCAT('시드개설자', n), CONCAT('시드개설자', n), CONCAT('seed@s', LPAD(n, 2, '0'), '.ac.kr'),
       CONCAT('seed@s', LPAD(n, 2, '0'), '.ac.kr'), 'x', UTC_TIMESTAMP(6), 'v1', UTC_TIMESTAMP(6), CONCAT('s', LPAD(n, 2, '0'), '.ac.kr')
FROM seq;
INSERT INTO login_session (token_hash, member_id, created_at, last_used_at)
VALUES (SHA2('bench-session-1', 256), 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO group_buy (organizer_id, title, description, item_name, unit_price, total_quantity, secured_quantity,
                       deadline, category, item_url, open_chat_url, status, version, created_at, updated_at, school)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @N)
SELECT 1 + (n DIV 20) % @SCHOOLS,
       CONCAT(IF(n % 3000 = 17, '희귀템 ', ''), ELT(1 + n % 8, '햇반', '라면', '휴지', '샴푸', '커피', '물티슈', '세제', '계란'),
              ' ', n, '개 공구'),
       NULL,
       CONCAT(ELT(1 + n % 5, '묶음 상품', '대용량', '번들', '기획전', '리필'), ' ', n),
       1000 + n % 50 * 100, 24, 1 + n % 23,
       CASE WHEN n % 20 < 14 THEN TIMESTAMPADD(HOUR, 1 + n % 700, UTC_TIMESTAMP(6))      -- 70% 모집 중
            WHEN n % 20 < 16 THEN TIMESTAMPADD(HOUR, -1 - n % 300, UTC_TIMESTAMP(6))     -- 10% 마감됨
            ELSE TIMESTAMPADD(HOUR, 1 + n % 700, UTC_TIMESTAMP(6)) END,
       ELT(1 + n % 6, 'FOOD', 'DAILY', 'KITCHEN', 'HEALTH_BEAUTY', 'STATIONERY', 'ETC'),
       'https://example.com/item', 'https://open.kakao.com/o/abc',
       CASE WHEN n % 20 < 16 THEN 'OPEN' WHEN n % 20 < 18 THEN 'COMPLETED' WHEN n % 20 < 19 THEN 'FAILED' ELSE 'CANCELLED' END,
       0, TIMESTAMPADD(MINUTE, -(@N - n), UTC_TIMESTAMP(6)), UTC_TIMESTAMP(6), CONCAT('s', LPAD(1 + (n DIV 20) % @SCHOOLS, 2, '0'), '.ac.kr')
FROM seq;
