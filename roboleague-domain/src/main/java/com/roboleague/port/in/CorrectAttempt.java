package com.roboleague.port.in;

import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.audit.AuditNote;
import com.roboleague.scheduling.JudgeId;

/**
 * Corrects a scored attempt outside an appeal: faults seen after the capture (a new revision scored with the
 * attempt's own rulebook) or a disqualification by a judge of its turn. Either way the attempt keeps its history
 * and the standings of its challenge and category are recalculated, so the correction shows up as a new version.
 *
 * <p>Input port: adapters (REST, GraphQL) depend on this interface, never on its implementation.
 */
public interface CorrectAttempt {
    /**
     * Charges {@code additionalPenalties} more faults to the attempt, as the note's author decided and why.
     */
    AttemptCorrection adjustPenalties(AttemptId attemptId, int additionalPenalties, AuditNote note);

    /**
     * Disqualifies the attempt. The judge has to be assigned to its turn, like the one who captures its results.
     */
    AttemptCorrection disqualify(AttemptId attemptId, JudgeId judgeId, String reason);
}
