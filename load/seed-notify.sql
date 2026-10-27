-- 알림 측정 시드. load/seed.sql(회원 1~101, 세션)을 먼저 실행한다. 회원 102~1101 을 더하고(세션 포함),
-- 개설자(회원 1) + 참여자 1,000명(회원 102~1101, 각 수량 1) 공구 1건(id 1)을 만든다. 취소하면 1,000건의 알림이 생긴다.
SET SESSION cte_max_recursion_depth = 5000;
INSERT INTO member (id, name, display_name_key, email, email_key, password_hash, consent_at, consent_version, created_at)
WITH RECURSIVE seq (n) AS (SELECT 102 UNION ALL SELECT n + 1 FROM seq WHERE n < 1101)
SELECT n, CONCAT('알림회원', n), CONCAT('알림회원', n), CONCAT('n', n, '@school.ac.kr'), CONCAT('n', n, '@school.ac.kr'), 'x',
       UTC_TIMESTAMP(6), 'v1', UTC_TIMESTAMP(6) FROM seq;
INSERT INTO login_session (token_hash, member_id, created_at, last_used_at)
SELECT SHA2(CONCAT('bench-session-', id), 256), id, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) FROM member WHERE id >= 102;
DELETE FROM notification;
DELETE FROM participation;
DELETE FROM group_buy;
INSERT INTO group_buy (id, organizer_id, title, description, item_name, unit_price, total_quantity, secured_quantity,
                       deadline, category, item_url, open_chat_url, status, version, created_at, updated_at)
VALUES (1, 1, '알림 측정 공구', NULL, '햇반', 1200, 2000, 1010, TIMESTAMPADD(DAY, 7, UTC_TIMESTAMP(6)), 'FOOD',
        'https://example.com/item', 'https://open.kakao.com/o/abc', 'OPEN', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
INSERT INTO participation (group_buy_id, member_id, quantity, created_at) VALUES (1, 1, 10, UTC_TIMESTAMP(6));
INSERT INTO participation (group_buy_id, member_id, quantity, created_at)
SELECT 1, id, 1, UTC_TIMESTAMP(6) FROM member WHERE id >= 102;
