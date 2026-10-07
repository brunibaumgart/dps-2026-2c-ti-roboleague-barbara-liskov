CREATE TABLE attempts (
    id               VARCHAR(80)  PRIMARY KEY,
    slot_id          VARCHAR(64)  NOT NULL,
    attempt_number   INTEGER      NOT NULL,
    round_id         VARCHAR(64)  NOT NULL,
    team_id          VARCHAR(64)  NOT NULL,
    challenge_id     VARCHAR(64)  NOT NULL,
    rulebook_version INTEGER      NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    open_appeals     INTEGER      NOT NULL,
    settled_status   VARCHAR(20),
    history          JSONB        NOT NULL
);

CREATE INDEX attempts_team_id_idx ON attempts (team_id);
CREATE INDEX attempts_round_id_idx ON attempts (round_id);
