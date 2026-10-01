/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One directed working-Context connection to an exact pinned target revision.
 *
 * <p>The locator is operational metadata only. Semantic identity is carried by
 * the target {@link RevisionRef}.</p>
 */
final class ContextConnection {

    private final Path targetLocation;
    private final RevisionRef target;

    ContextConnection(Path targetLocation, RevisionRef target) {
        if (targetLocation == null || targetLocation.getFileName() == null) {
            throw new IllegalArgumentException(
                    "target Context location must have a final path component");
        }
        this.targetLocation = targetLocation.toAbsolutePath().normalize();
        this.target = Objects.requireNonNull(target, "target");
    }

    Path getTargetLocation() {
        return targetLocation;
    }

    RevisionRef getTarget() {
        return target;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ContextConnection)) {
            return false;
        }
        ContextConnection connection = (ContextConnection) other;
        return targetLocation.equals(connection.targetLocation)
                && target.equals(connection.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetLocation, target);
    }

    @Override
    public String toString() {
        return target.toString() + " at " + targetLocation;
    }
}
