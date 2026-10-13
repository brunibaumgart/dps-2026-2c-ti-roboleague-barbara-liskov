CREATE TABLE editions (
    id             VARCHAR(64) PRIMARY KEY,
    version        BIGINT      NOT NULL DEFAULT 0,
    name           TEXT        NOT NULL,
    edition_number INTEGER     NOT NULL,
    start_date     DATE        NOT NULL,
    end_date       DATE        NOT NULL,
    tournament     JSONB       NOT NULL,
    categories     JSONB       NOT NULL,
    registrations  JSONB       NOT NULL
);

CREATE INDEX editions_registrations_idx ON editions USING GIN (registrations jsonb_path_ops);
