/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger.exception;

/**
 * Signals a failure that happened only after a Mind transaction had already
 * reached an irreversible semantic settlement boundary.
 *
 * <p>The child reservation has been consumed when this exception is created;
 * callers must therefore never retry commit/reject settlement on the same
 * child. {@link Outcome#COMMITTED} means the child's semantic delta has already
 * been merged into the parent. {@link Outcome#REJECTED} means the child delta
 * was rejected and remains absent. In both cases only post-settlement work such
 * as root pack/update/flush or result projection failed.</p>
 *
 * <p>This distinction lets protocol adapters report an operational failure
 * without lying about the logical transaction result. In particular, a failed
 * durability/finalization step after {@code COMMITTED} must not be presented as
 * though the user transaction were still open or safely retryable.</p>
 */
public final class TransactionSettlementException extends Exception {

    private static final long serialVersionUID = 1L;

    public enum Outcome {
        COMMITTED,
        REJECTED
    }

    private final Outcome outcome;

    public TransactionSettlementException(Outcome outcome, Throwable cause) {
        super(message(outcome, cause), cause);
        if (outcome == null) {
            throw new IllegalArgumentException("Transaction settlement outcome is required");
        }
        this.outcome = outcome;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    /**
     * @return {@code true} when the child semantic delta is already part of the
     * parent despite the finalization failure
     */
    public boolean isSemanticApplied() {
        return outcome == Outcome.COMMITTED;
    }

    /**
     * The exception is emitted only after the parent reservation was consumed.
     */
    public boolean isReservationConsumed() {
        return true;
    }

    private static String message(Outcome outcome, Throwable cause) {
        String detail = cause == null ? "unknown finalization failure" : cause.toString();
        return "Transaction settled as " + outcome
                + " but post-settlement finalization failed: " + detail;
    }
}
