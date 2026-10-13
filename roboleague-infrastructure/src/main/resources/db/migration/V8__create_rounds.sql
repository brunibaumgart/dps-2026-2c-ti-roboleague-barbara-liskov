CREATE TABLE rounds (
    id           VARCHAR(64) PRIMARY KEY,
    version      BIGINT      NOT NULL DEFAULT 0,
    name         TEXT        NOT NULL,
    challenge_id VARCHAR(64) NOT NULL,
    edition_id   VARCHAR(64) NOT NULL,
    category_id  VARCHAR(64) NOT NULL,
    round_number INTEGER     NOT NULL,
    status       VARCHAR(20) NOT NULL,
    slots        JSONB       NOT NULL,
    CONSTRAINT rounds_scope_uk UNIQUE (challenge_id, category_id, round_number)
);

CREATE INDEX rounds_slots_idx ON rounds USING GIN (slots jsonb_path_ops);
