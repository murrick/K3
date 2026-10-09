/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Exact semantic set identity; hash is a prefilter, never equality authority. */
final class EvidenceFingerprint {
    private final List<SuppliedEvidence> facts;
    private final int hash;

    EvidenceFingerprint(List<SuppliedEvidence> facts) {
        List<SuppliedEvidence> unique = new ArrayList<SuppliedEvidence>();
        for (SuppliedEvidence fact : facts) {
            SuppliedEvidence.add(unique, fact);
        }
        this.facts = Collections.unmodifiableList(unique);
        int hash = unique.size();
        for (SuppliedEvidence fact : unique) {
            hash += fact.semanticHash();
        }
        this.hash = hash;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof EvidenceFingerprint)) {
            return false;
        }
        EvidenceFingerprint fingerprint = (EvidenceFingerprint) other;
        if (hash != fingerprint.hash || facts.size() != fingerprint.facts.size()) {
            return false;
        }
        for (SuppliedEvidence fact : facts) {
            boolean found = false;
            for (SuppliedEvidence candidate : fingerprint.facts) {
                if (fact.sameFact(candidate)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }
}
