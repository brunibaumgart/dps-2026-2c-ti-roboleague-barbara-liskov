CREATE TABLE standings (
    id           VARCHAR(140) PRIMARY KEY,
    version      BIGINT       NOT NULL DEFAULT 0,
    challenge_id VARCHAR(64)  NOT NULL,
    category_id  VARCHAR(64)  NOT NULL,
    versions     JSONB        NOT NULL,
    publications JSONB        NOT NULL,
    CONSTRAINT standings_challenge_category_uk UNIQUE (challenge_id, category_id)
);
