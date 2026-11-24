-- 011 비밀번호 재설정(plan 결정 1·3). 주소마다 한 줄이고 가입 여부와 상관없이 만든다: 한도를 가입 여부와 무관하게 세야 429 로 가입이 드러나지 않는다.
-- 회원이 아니면 member_id·token_hash 가 NULL 이다. 토큰은 해시만 둔다. 탈퇴하면 email_key 로 지운다(010 erase).
CREATE TABLE password_reset (
    email_key         VARCHAR(254) NOT NULL PRIMARY KEY,
    member_id         BIGINT       NULL,
    token_hash        CHAR(64)     NULL,
    token_expires_at  DATETIME(6)  NULL,
    used_at           DATETIME(6)  NULL,
    last_sent_at      DATETIME(6)  NOT NULL,
    window_started_at DATETIME(6)  NOT NULL,
    sent_count        INT          NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    KEY idx_password_reset_token (token_hash),
    CONSTRAINT fk_password_reset_member FOREIGN KEY (member_id) REFERENCES member (id)
);

-- 재설정한 시각. 이보다 먼저 만든 세션은 커밋 뒤 삭제가 실패해도 쓸 수 없다(세션 조회가 거부한다).
ALTER TABLE member ADD COLUMN password_changed_at DATETIME(6) NULL;
