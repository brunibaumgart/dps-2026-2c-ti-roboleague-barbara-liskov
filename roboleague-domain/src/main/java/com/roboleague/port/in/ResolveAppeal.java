package com.roboleague.port.in;

import com.roboleague.evaluation.SourceReport;
import com.roboleague.evaluation.audit.AuditNote;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.ranking.appeal.AppealId;
import java.util.List;

/**
 * Resolves an appeal under review. Accepting it takes the corrected reports of the sources the appeal disputes,
 * checked against the attempt's own rulebook version (the reviewer does not choose the rules): the attempt gets a
 * new revision and the standings of its category are recalculated, so the correction shows up as a new version.
 * Rejecting it keeps the score and releases the attempt once no other appeal is open.
 *
 * <p>Input port: adapters (REST, GraphQL) depend on this interface, never on its implementation.
 */
public interface ResolveAppeal {
    /**
     * Accepts the appeal with the corrected reports: each replaces what its source had sent, and what the other
     * sources sent stays. Corrections that do not fit the rulebook change nothing and come back as problems.
     */
    AppealAcceptance acceptAppeal(AppealId appealId, AuditNote resolution, List<SourceReport> corrections);

    Appeal rejectAppeal(AppealId appealId, AuditNote resolution);
}
