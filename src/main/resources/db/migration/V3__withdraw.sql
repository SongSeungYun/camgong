-- 010 회원 탈퇴(익명화). 회원 행은 남기고(공구·참여가 FK 로 가리킨다) 이름은 '탈퇴한 회원', 이름 키·이메일·이메일 키는 NULL 로 바꾼다.
-- UNIQUE 는 NULL 을 여러 개 허용하므로 탈퇴한 회원의 이메일과 표시 이름은 새 가입에 다시 쓸 수 있다. MODIFY 는 기존 UNIQUE 인덱스를 지우지 않는다.
ALTER TABLE member
    MODIFY display_name_key VARCHAR(20)  NULL,
    MODIFY email            VARCHAR(254) NULL,
    MODIFY email_key        VARCHAR(254) NULL,
    ADD COLUMN withdrawn_at DATETIME(6)  NULL;
