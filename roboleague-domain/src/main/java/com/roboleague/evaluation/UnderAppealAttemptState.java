package com.roboleague.evaluation;

import com.roboleague.evaluation.Attempt.AttemptStatus;

/**
 * An attempt with appeals open. It counts them, so closing one leaves it under appeal while others remain;
 * closing the last one settles it again, adjusted if any of them was accepted.
 */
final class UnderAppealAttemptState implements AttemptState {
    private final int openAppeals;
    private final SettledAttemptState settled;

    UnderAppealAttemptState(int openAppeals, SettledAttemptState settled) {
        if (openAppeals < 1) {
            throw new IllegalArgumentException("an attempt under appeal has at least one appeal open: " + openAppeals);
        }
        this.openAppeals = openAppeals;
        this.settled = settled;
    }

    int openAppeals() {
        return openAppeals;
    }

    @Override
    public AttemptStatus status() {
        return AttemptStatus.UNDER_APPEAL;
    }

    @Override
    public boolean counts() {
        return true;
    }

    @Override
    public AttemptState appealFiled() {
        return new UnderAppealAttemptState(openAppeals + 1, settled);
    }

    @Override
    public AttemptState appealRejected() {
        return closingOne(settled);
    }

    @Override
    public AttemptState appealAccepted() {
        return closingOne(SettledAttemptState.adjusted());
    }

    private AttemptState closingOne(SettledAttemptState afterwards) {
        if (openAppeals == 1) {
            return afterwards;
        }
        return new UnderAppealAttemptState(openAppeals - 1, afterwards);
    }
}
