-- 체크표 측정 시드: 모집 완료 공구 1건(개설자 회원 1, 참여자 50명 = 회원 2~51, 각 수량 1). load/seed.sql 의 회원 1~101 이 있어야 한다.
DELETE FROM participation;
DELETE FROM group_buy;
INSERT INTO group_buy (id, organizer_id, title, description, item_name, unit_price, total_quantity, secured_quantity,
                       deadline, category, item_url, open_chat_url, status, version, created_at, updated_at)
VALUES (1, 1, '체크표 측정', NULL, '햇반', 1200, 60, 60, TIMESTAMPADD(DAY, 7, UTC_TIMESTAMP(6)), 'FOOD',
        'https://example.com/item', 'https://open.kakao.com/o/abc', 'COMPLETED', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
INSERT INTO participation (group_buy_id, member_id, quantity, created_at, paid_at, received_at)
SELECT 1, id, IF(id = 1, 10, 1), UTC_TIMESTAMP(6), IF(id % 3 = 0, UTC_TIMESTAMP(6), NULL), IF(id % 5 = 0, UTC_TIMESTAMP(6), NULL)
FROM member WHERE id BETWEEN 1 AND 51;
