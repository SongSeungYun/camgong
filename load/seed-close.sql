-- 마감 확정 측정 시드. 호출 전에 SET @N=…, @MIN_OFFSET=…, @MAX_OFFSET=…; 를 준다(초, 지금 기준 마감 시각의 범위; 음수면 이미 마감).
-- 공구 @N 건: 총 24, 개설자 9 + 참여자 2명(회원 2, 3) 3, 마감은 지금 + [@MIN_OFFSET, @MAX_OFFSET] 초에 균등하게 흩어진다. 회원 1~101(load/seed.sql)이 있어야 한다.
SET SESSION cte_max_recursion_depth = 100000;
DELETE FROM participation;
DELETE FROM group_buy;
INSERT INTO group_buy (organizer_id, title, description, item_name, unit_price, total_quantity, secured_quantity,
                       deadline, category, item_url, open_chat_url, status, version, created_at, updated_at)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @N)
SELECT 1, CONCAT('마감 측정 ', n), NULL, '햇반', 1200, 24, 15,
       TIMESTAMPADD(MICROSECOND, FLOOR((@MIN_OFFSET + RAND() * (@MAX_OFFSET - @MIN_OFFSET)) * 1000000), UTC_TIMESTAMP(6)),
       'FOOD', 'https://example.com/item', 'https://open.kakao.com/o/abc', 'OPEN', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM seq;
INSERT INTO participation (group_buy_id, member_id, quantity, created_at)
SELECT id, 1, 9, UTC_TIMESTAMP(6) FROM group_buy;
INSERT INTO participation (group_buy_id, member_id, quantity, created_at)
SELECT id, 2, 3, UTC_TIMESTAMP(6) FROM group_buy;
INSERT INTO participation (group_buy_id, member_id, quantity, created_at)
SELECT id, 3, 3, UTC_TIMESTAMP(6) FROM group_buy;
