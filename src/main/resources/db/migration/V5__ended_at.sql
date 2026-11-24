-- 012 끝난 공구 1년 보관(plan 결정 1). 공구가 COMPLETED·FAILED·CANCELLED 로 바뀐 시각. 끝나지 않은 공구는 NULL 이다.
ALTER TABLE group_buy
    ADD COLUMN ended_at DATETIME(6) NULL,
    ADD KEY idx_group_buy_ended_at (ended_at);

-- 이미 끝난 공구는 마지막 변경 시각으로 채운다(운영 데이터는 없고 로컬·측정 데이터만 있다).
UPDATE group_buy SET ended_at = updated_at WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED');

-- 정리가 공구별로 알림을 지운다. 인덱스가 없으면 DELETE 가 알림 표 전체를 훑으며 모든 행을 잠가 알림 생성을 막는다.
ALTER TABLE notification ADD KEY idx_notification_group_buy (group_buy_id);
