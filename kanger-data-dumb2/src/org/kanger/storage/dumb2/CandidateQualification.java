/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

/** Local semantic qualification of one unpublished DUMB2 Context candidate. */
final class CandidateQualification {

    private CandidateQualification() {
    }

    static Result qualifyLocal(
            ContextSnapshot candidate) throws Exception {
        SnapshotMindRuntime runtime =
                SnapshotMindRuntime.open(
                        candidate,
                        "candidate-local-qualification");
        try {
            boolean valid =
                    Boolean.TRUE.equals(
                            runtime.getMind()
                                    .queryCheck(false));
            return new Result(
                    runtime.getRef(),
                    valid);
        } finally {
            runtime.close();
        }
    }

    static final class Result {

        private final RevisionRef candidate;
        private final boolean valid;

        private Result(
                RevisionRef candidate,
                boolean valid) {
            this.candidate = candidate;
            this.valid = valid;
        }

        RevisionRef getCandidate() {
            return candidate;
        }

        boolean isValid() {
            return valid;
        }
    }
}
