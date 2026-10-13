-- Each standings version records the rulebook version whose ranking scheme ordered it ("rankedWith").
-- Before this migration the table was always ranked with the challenge's current rulebook, so stored versions
-- take the version that is current now. It is exact unless the challenge published a newer version after the
-- calculation; that case cannot be told apart from the stored data.
UPDATE standings s
SET versions = (
    SELECT jsonb_agg(v || jsonb_build_object('rankedWith', jsonb_array_length(c.rulebooks -> 'versions'))
                     ORDER BY ord)
    FROM jsonb_array_elements(s.versions) WITH ORDINALITY AS stored(v, ord)
)
FROM challenges c
WHERE c.id = s.challenge_id
  AND jsonb_array_length(s.versions) > 0;
