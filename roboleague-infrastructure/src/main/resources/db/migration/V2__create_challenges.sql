CREATE TABLE challenges (
    id         VARCHAR(64)  PRIMARY KEY,
    edition_id VARCHAR(64)  NOT NULL,
    name       VARCHAR(120) NOT NULL,
    rulebooks  JSONB        NOT NULL
);

CREATE INDEX challenges_edition_id_idx ON challenges (edition_id);
