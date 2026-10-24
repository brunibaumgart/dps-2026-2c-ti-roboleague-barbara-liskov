-- Rulebooks published before the ranking scheme said how many attempts a turn admits and which counts: the best
-- of one attempt per turn, the default of a rulebook that does not say otherwise.
UPDATE challenges
SET rulebooks = jsonb_set(rulebooks, '{versions}', (
    SELECT jsonb_agg(
               CASE WHEN rulebook -> 'ranking' ? 'attemptSelection' THEN rulebook
                    ELSE jsonb_set(rulebook, '{ranking,attemptSelection}',
                                   '{"type": "best-attempt", "numbers": {"perTurn": 1}}'::jsonb) END
               ORDER BY position)
    FROM jsonb_array_elements(rulebooks -> 'versions') WITH ORDINALITY AS stored(rulebook, position)))
WHERE jsonb_array_length(rulebooks -> 'versions') > 0;

-- Standings calculated before each version recorded its rulebook were ranked with the challenge's latest rulebook,
-- and counted the best attempt of each turn without naming the others.
UPDATE standings s
SET versions = (
    SELECT jsonb_agg(
               (calculation - 'entries')
                   || jsonb_build_object('rulebookVersion', COALESCE(calculation -> 'rulebookVersion', to_jsonb(
                          (SELECT jsonb_array_length(c.rulebooks -> 'versions') FROM challenges c
                           WHERE c.id = s.challenge_id))))
                   || jsonb_build_object('entries', COALESCE((
                          SELECT jsonb_agg(
                                     entry
                                         || jsonb_build_object('attemptRule', COALESCE(entry -> 'attemptRule',
                                                to_jsonb('el mejor intento de cada turno'::text)))
                                         || jsonb_build_object('considered', COALESCE((
                                                SELECT jsonb_agg(round || jsonb_build_object('otherAttempts',
                                                           COALESCE(round -> 'otherAttempts', '[]'::jsonb))
                                                           ORDER BY round_position)
                                                FROM jsonb_array_elements(entry -> 'considered')
                                                         WITH ORDINALITY AS considered(round, round_position)),
                                                '[]'::jsonb))
                                         || jsonb_build_object('discarded', COALESCE((
                                                SELECT jsonb_agg(round || jsonb_build_object('otherAttempts',
                                                           COALESCE(round -> 'otherAttempts', '[]'::jsonb))
                                                           ORDER BY round_position)
                                                FROM jsonb_array_elements(entry -> 'discarded')
                                                         WITH ORDINALITY AS discarded(round, round_position)),
                                                '[]'::jsonb))
                                     ORDER BY entry_position)
                          FROM jsonb_array_elements(calculation -> 'entries')
                                   WITH ORDINALITY AS entries(entry, entry_position)), '[]'::jsonb))
               ORDER BY position)
    FROM jsonb_array_elements(s.versions) WITH ORDINALITY AS stored(calculation, position))
WHERE jsonb_array_length(s.versions) > 0;
