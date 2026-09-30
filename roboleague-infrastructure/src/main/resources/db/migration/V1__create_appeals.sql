CREATE TABLE appeals (
    id                   VARCHAR(64)  PRIMARY KEY,
    attempt_id           VARCHAR(64)  NOT NULL,
    team_id              VARCHAR(64)  NOT NULL,
    reason               TEXT         NOT NULL,
    evidence_description TEXT         NOT NULL,
    status               VARCHAR(20)  NOT NULL,
    submitted_at         TIMESTAMP    NOT NULL,
    reviewer_id          VARCHAR(64),
    resolution_notes     TEXT,
    revised_metrics      JSONB,
    resolved_at          TIMESTAMP
);

CREATE INDEX appeals_attempt_id_idx ON appeals (attempt_id);
