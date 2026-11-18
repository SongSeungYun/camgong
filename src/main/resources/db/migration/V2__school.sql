-- 009 학교 범위. 회원의 학교는 가입 확인 때 메일 도메인으로 정하고, 공구는 개설 때 개설자의 학교를 복사한다(둘 다 바뀌지 않는다).
-- 운영 데이터가 없어 기본값 없이 NOT NULL 로 둔다. 행이 있는 DB 면 MySQL 이 '' 로 채우는데, 그러면 모든 회원이 한 학교가 된다.
-- 빈 학교를 막는 CHECK 가 기존 행에도 검사되므로 그런 DB 에서는 이 마이그레이션이 실패한다(새 DB 로 시작한다).
ALTER TABLE member
    ADD COLUMN school VARCHAR(253) NOT NULL,
    ADD CONSTRAINT chk_member_school CHECK (school <> '');

-- 인덱스는 로그인 목록(학교로 거름)의 EXPLAIN 으로 정했다: 최신순·검색은 (school, id) 역방향, 마감 임박순은 (school, status, deadline).
-- 학교 검색(/api/schools)의 DISTINCT 도 (school, status, deadline) 커버링 스캔이다. 비로그인 전국 목록은 기존 PK·(status, deadline) 그대로다.
ALTER TABLE group_buy
    ADD COLUMN school VARCHAR(253) NOT NULL,
    ADD CONSTRAINT chk_group_buy_school CHECK (school <> ''),
    ADD KEY idx_group_buy_school_id (school, id),
    ADD KEY idx_group_buy_school_status_deadline (school, status, deadline);
