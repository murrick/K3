/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

/**
 * Optional pre-publication policy for one serialized Context candidate.
 */
interface CandidateGate {

    void qualify(ContextCandidate candidate) throws Exception;
}
