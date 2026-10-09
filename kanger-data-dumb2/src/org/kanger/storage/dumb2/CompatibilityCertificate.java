/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import java.util.Objects;

/**
 * Runtime evidence that one exact pair of Context revisions was fully
 * qualified under one semantic/inference version.
 */
final class CompatibilityCertificate {

    private final RevisionRef left;
    private final RevisionRef right;
    private final String semanticVersion;

    CompatibilityCertificate(RevisionRef left,
                             RevisionRef right,
                             String semanticVersion) {
        this.left = Objects.requireNonNull(left, "left");
        this.right = Objects.requireNonNull(right, "right");
        if (left.getContextId().equals(right.getContextId())) {
            throw new IllegalArgumentException(
                    "compatibility pair requires two Context identities");
        }
        if (semanticVersion == null
                || semanticVersion.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "semanticVersion must not be blank");
        }
        this.semanticVersion = semanticVersion.trim();
    }

    RevisionRef getLeft() {
        return left;
    }

    RevisionRef getRight() {
        return right;
    }

    String getSemanticVersion() {
        return semanticVersion;
    }

    boolean matches(RevisionRef first,
                    RevisionRef second,
                    String version) {
        if (!semanticVersion.equals(version)) {
            return false;
        }
        return (left.equals(first) && right.equals(second))
                || (left.equals(second) && right.equals(first));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CompatibilityCertificate)) {
            return false;
        }
        CompatibilityCertificate certificate =
                (CompatibilityCertificate) other;
        return semanticVersion.equals(certificate.semanticVersion)
                && ((left.equals(certificate.left)
                        && right.equals(certificate.right))
                    || (left.equals(certificate.right)
                        && right.equals(certificate.left)));
    }

    @Override
    public int hashCode() {
        return left.hashCode() ^ right.hashCode()
                ^ semanticVersion.hashCode();
    }
}
