-- 참여 측정용 시드(한 번만): 회원 1~101 과 로그인 세션. 쿠키 값은 "bench-session-<회원 id>" 이다.
-- 앱을 한 번 띄워 schema.sql 이 적용된 DB 에서 쓴다.
SET SESSION cte_max_recursion_depth = 1000;

DELETE FROM login_session;
DELETE FROM participation;
DELETE FROM group_buy;
DELETE FROM member;

INSERT INTO member (id, name, display_name_key, email, email_key, password_hash, consent_at, consent_version, created_at)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 101)
SELECT n, CONCAT('벤치회원', n), CONCAT('벤치회원', n), CONCAT('b', n, '@school.ac.kr'), CONCAT('b', n, '@school.ac.kr'), 'x',
       UTC_TIMESTAMP(6), 'v1', UTC_TIMESTAMP(6)
FROM seq;

INSERT INTO login_session (token_hash, member_id, created_at, last_used_at)
SELECT SHA2(CONCAT('bench-session-', id), 256), id, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) FROM member;
