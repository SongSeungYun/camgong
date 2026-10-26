-- ponytail: Flyway로 올릴 시점은 운영 데이터가 생기는 5단계 전
-- 모든 테이블은 CREATE TABLE IF NOT EXISTS 라 앱 재시작에 안전하다.

CREATE TABLE IF NOT EXISTS member (
    id   BIGINT       NOT NULL PRIMARY KEY,
    name VARCHAR(50)  NOT NULL
);

CREATE TABLE IF NOT EXISTS group_buy (
    id                BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organizer_id      BIGINT        NOT NULL,
    title             VARCHAR(100)  NOT NULL,
    description       VARCHAR(2000) NULL,
    item_name         VARCHAR(100)  NOT NULL,
    unit_price        INT           NOT NULL CHECK (unit_price BETWEEN 1 AND 1000000),
    total_quantity    INT           NOT NULL,
    secured_quantity  INT           NOT NULL,
    deadline          DATETIME(6)   NOT NULL,
    category          VARCHAR(20)   NOT NULL,
    item_url          VARCHAR(500)  NOT NULL,
    open_chat_url     VARCHAR(500)  NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    version           BIGINT        NOT NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    CONSTRAINT fk_group_buy_organizer FOREIGN KEY (organizer_id) REFERENCES member (id),
    CONSTRAINT chk_group_buy_secured CHECK (secured_quantity BETWEEN 1 AND total_quantity)
);

CREATE TABLE IF NOT EXISTS participation (
    id            BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    group_buy_id  BIGINT      NOT NULL,
    member_id     BIGINT      NOT NULL,
    quantity      INT         NOT NULL CHECK (quantity > 0),
    created_at    DATETIME(6) NOT NULL,
    CONSTRAINT fk_participation_group_buy FOREIGN KEY (group_buy_id) REFERENCES group_buy (id),
    CONSTRAINT fk_participation_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT uq_participation_group_member UNIQUE (group_buy_id, member_id)
);

CREATE TABLE IF NOT EXISTS create_guard (
    member_id    BIGINT      NOT NULL,
    fingerprint  CHAR(64)    NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (member_id, fingerprint)
);
