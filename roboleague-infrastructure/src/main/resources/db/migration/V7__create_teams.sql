CREATE TABLE teams (
    id            VARCHAR(64) PRIMARY KEY,
    version       BIGINT      NOT NULL DEFAULT 0,
    name          TEXT        NOT NULL,
    institution   TEXT        NOT NULL,
    robot         JSONB       NOT NULL,
    members       JSONB       NOT NULL,
    documentation JSONB       NOT NULL
);
