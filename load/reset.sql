-- 시나리오마다 공구 1건을 새로 만든다. 호출 전에 SET @TOTAL=…, @ORG=…; 를 준다.
-- 총 수량 @TOTAL, 개설자 수량 @ORG(확보 수량), 개당 1,200원, 마감은 7일 뒤. 개설자는 회원 1.
DELETE FROM participation;
DELETE FROM group_buy;
INSERT INTO group_buy (id, organizer_id, title, description, item_name, unit_price, total_quantity, secured_quantity,
                       deadline, category, item_url, open_chat_url, status, version, created_at, updated_at)
VALUES (1, 1, '측정용 공구', NULL, '햇반', 1200, @TOTAL, @ORG, TIMESTAMPADD(DAY, 7, UTC_TIMESTAMP(6)), 'FOOD',
        'https://example.com/item', 'https://open.kakao.com/o/abc', 'OPEN', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
INSERT INTO participation (group_buy_id, member_id, quantity, created_at) VALUES (1, 1, @ORG, UTC_TIMESTAMP(6));
