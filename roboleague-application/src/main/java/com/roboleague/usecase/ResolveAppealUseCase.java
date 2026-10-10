package com.roboleague.usecase;

import com.roboleague.evaluation.AppealRevision;
import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.MeasurementCheck;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ResultSource;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.RulebookReference;
import com.roboleague.evaluation.SourceReport;
import com.roboleague.evaluation.audit.AuditNote;
import com.roboleague.evaluation.audit.OperationAudit;
import com.roboleague.ranking.Standings;
import com.roboleague.ranking.StandingsId;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.ranking.appeal.AppealId;
import com.roboleague.repository.AppealRepository;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.repository.ChallengeRepository;
import com.roboleague.repository.RoundRepository;
import com.roboleague.scheduling.Round;
import com.roboleague.support.Clock;
import com.roboleague.support.IdGenerator;
import com.roboleague.tournament.Challenge;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Resolves an appeal under review. Accepting it takes the corrected reports of the sources the appeal disputes,
 * checked against the attempt's own rulebook version (the reviewer does not choose the rules): the attempt gets a
 * new revision and the standings of its category are recalculated, so the correction shows up as a new version.
 * Rejecting it keeps the score and releases the attempt once no other appeal is open.
 */
public class ResolveAppealUseCase {
    private final Clock clock;
    private final IdGenerator ids;

    private final AppealRepository appealRepository;
    private final AttemptRepository attemptRepository;
    private final ChallengeRepository challengeRepository;
    private final RoundRepository roundRepository;
    private final RecalculateStandingsUseCase recalculateStandings;

    public ResolveAppealUseCase(AppealRepository appealRepository, AttemptRepository attemptRepository,
                                ChallengeRepository challengeRepository, RoundRepository roundRepository,
                                RecalculateStandingsUseCase recalculateStandings, Clock clock, IdGenerator ids) {
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.ids = Objects.requireNonNull(ids, "ids cannot be null");
        this.appealRepository = Objects.requireNonNull(appealRepository, "appealRepository cannot be null");
        this.attemptRepository = Objects.requireNonNull(attemptRepository, "attemptRepository cannot be null");
        this.challengeRepository = Objects.requireNonNull(challengeRepository, "challengeRepository cannot be null");
        this.roundRepository = Objects.requireNonNull(roundRepository, "roundRepository cannot be null");
        this.recalculateStandings = Objects.requireNonNull(recalculateStandings, "recalculateStandings cannot be null");
    }

    /**
     * Accepts the appeal with the corrected reports: each replaces what its source had sent, and what the other
     * sources sent stays. Corrections that do not fit the rulebook change nothing and come back as problems.
     */
    public AppealAcceptance acceptAppeal(AppealId appealId, AuditNote resolution, List<SourceReport> corrections) {
        Objects.requireNonNull(resolution, "resolution cannot be null");
        Objects.requireNonNull(corrections, "corrections cannot be null");
        Appeal appeal = appeal(appealId);
        Attempt attempt = attemptOf(appeal);
        RulebookReference scoredWith = attempt.getRulebookReference();
        Challenge challenge = challengeRepository.findById(scoredWith.challengeId())
                .orElseThrow(() -> new IllegalStateException("Challenge of attempt " + attempt.getId()
                        + " not found: " + scoredWith.challengeId()));
        Rulebook rulebook = challenge.rulebook(scoredWith.version())
                .orElseThrow(() -> new IllegalStateException("Rulebook " + scoredWith + " not found"));
        List<String> problems = problemsOf(corrections, rulebook, scoredWith);
        if (!problems.isEmpty()) {
            return new AppealAcceptance.Invalid(problems);
        }
        RawMetrics revised = attempt.getLatestMetrics();
        for (SourceReport correction : corrections) {
            revised = correction.addTo(revised);
        }

        OperationAudit audit = new OperationAudit(clock.now(), ids.nextId(), ids.nextId());
        appeal.accept(resolution.reason(), revised, resolution.authorId(), audit.timestamp());
        attempt.adjustAfterAppeal(new AppealRevision(appealId, revised, resolution), rulebook, audit);
        attemptRepository.save(attempt);
        appealRepository.save(appeal);

        Round round = roundRepository.findById(attempt.getRoundId())
                .orElseThrow(() -> new IllegalStateException("Round of attempt " + attempt.getId()
                        + " not found: " + attempt.getRoundId()));
        Standings standings = recalculateStandings.execute(new StandingsId(challenge.getId(), round.getCategoryId()));
        return new AppealAcceptance.Accepted(appeal, standings.latest());
    }

    public Appeal rejectAppeal(AppealId appealId, AuditNote resolution) {
        Objects.requireNonNull(resolution, "resolution cannot be null");
        Appeal appeal = appeal(appealId);
        Attempt attempt = attemptOf(appeal);

        appeal.reject(resolution.reason(), resolution.authorId(), clock.now());
        attempt.restoreAfterRejectedAppeal();

        attemptRepository.save(attempt);
        appealRepository.save(appeal);
        return appeal;
    }

    private Appeal appeal(AppealId appealId) {
        return appealRepository.findById(appealId)
                .orElseThrow(() -> new IllegalArgumentException("Appeal not found: " + appealId));
    }

    private Attempt attemptOf(Appeal appeal) {
        return attemptRepository.findById(appeal.getAttemptId())
                .orElseThrow(() -> new IllegalStateException("Attempt of appeal " + appeal.getAppealId()
                        + " not found: " + appeal.getAttemptId()));
    }

    private static List<String> problemsOf(List<SourceReport> corrections, Rulebook rulebook,
                                           RulebookReference scoredWith) {
        if (corrections.isEmpty()) {
            return List.of("an accepted appeal needs the corrected report of at least one source");
        }
        List<String> problems = new ArrayList<>();
        Set<ResultSource> corrected = EnumSet.noneOf(ResultSource.class);
        for (SourceReport correction : corrections) {
            ResultSource source = correction.source();
            if (!corrected.add(source)) {
                problems.add(source + " is corrected twice");
            } else if (!rulebook.requiredSources().contains(source)) {
                problems.add(scoredWith + " takes no results from " + source);
            } else if (rulebook.check(source, correction.named()) instanceof MeasurementCheck.Rejected rejected) {
                problems.addAll(rejected.problems());
            }
        }
        return problems;
    }
}
