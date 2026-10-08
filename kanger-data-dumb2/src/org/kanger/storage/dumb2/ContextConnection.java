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
 * the target {@link RevisionRef}. The compatibility certificate is evidence
 * for one exact source/target revision pair; it is not transitive.</p>
 */
final class ContextConnection {

    private final Path targetLocation;
    private final RevisionRef target;
    private final CompatibilityCertificate certificate;
    private final java.util.List<String> initialization;
    private final String trustGroup;
    private CommuneRuntime commune;
    private ConnectionRuntime runtime;

    ContextConnection(Path targetLocation,
                      RevisionRef target,
                      CompatibilityCertificate certificate) {
        this(targetLocation, target, certificate, java.util.Collections.<String>emptyList());
    }

    ContextConnection(Path targetLocation, RevisionRef target, CompatibilityCertificate certificate,
                      java.util.List<String> initialization) {
        this(targetLocation, target, certificate, initialization, null);
    }

    ContextConnection(Path targetLocation, RevisionRef target, CompatibilityCertificate certificate,
                      java.util.List<String> initialization, String trustGroup) {
        this.trustGroup = org.kanger.TrustGroups.validate(trustGroup);
        if (targetLocation == null || targetLocation.getFileName() == null) {
            throw new IllegalArgumentException(
                    "target Context location must have a final path component");
        }
        this.targetLocation = targetLocation.toAbsolutePath().normalize();
        this.target = Objects.requireNonNull(target, "target");
        this.certificate = Objects.requireNonNull(
                certificate, "certificate");
        if (initialization.size() > 4096) throw new IllegalArgumentException("Too many initialization commands");
        for (String command : initialization) {
            if (command == null || command.isEmpty() || "!+-".indexOf(command.charAt(0)) < 0)
                throw new IllegalArgumentException("Invalid connection initialization command");
            try {
                org.kanger.compiler.Token token = org.kanger.enums.Tools.extractLine(command, null);
                if (token == null || org.kanger.enums.Tools.extractLine(command, token) != null)
                    throw new IllegalArgumentException("One initialization entry must contain one command");
            } catch (Exception invalid) { throw new IllegalArgumentException("Invalid initialization source", invalid); }
        }
        this.initialization = java.util.Collections.unmodifiableList(new java.util.ArrayList<String>(initialization));
        if (!certificate.getLeft().equals(target)
                && !certificate.getRight().equals(target)) {
            throw new IllegalArgumentException(
                    "compatibility certificate does not include target "
                            + target);
        }
    }

    Path getTargetLocation() {
        return targetLocation;
    }

    RevisionRef getTarget() {
        return target;
    }

    CompatibilityCertificate getCertificate() {
        return certificate;
    }

    java.util.List<String> getInitialization() { return initialization; }
    String getTrustGroup() { return trustGroup; }
    CommuneRuntime commune() { return commune; }
    ContextConnection withTrustGroup(String group) {
        ContextConnection copy = new ContextConnection(targetLocation, target, certificate, initialization, group);
        copy.runtime = runtime;
        return copy;
    }
    ContextConnection forCommune(CommuneRuntime joint) {
        ContextConnection copy = withTrustGroup(trustGroup);
        copy.commune = joint;
        return copy;
    }
    synchronized org.kanger.Mind layer() throws Exception {
        if (runtime == null || runtime.closed) runtime = new ConnectionRuntime(this);
        return runtime.mind;
    }
    synchronized void closeLayer() throws Exception {
        if (runtime != null) { runtime.close(); runtime = null; }
    }
    ContextConnection recertified(CompatibilityCertificate certificate) {
        ContextConnection copy = new ContextConnection(targetLocation, target, certificate, initialization, trustGroup);
        copy.runtime = runtime;
        return copy;
    }
    synchronized void extendLayer(ContextConnection original, String command) throws Exception {
        original.layer();
        runtime = new ConnectionRuntime(original.runtime, command);
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
                && target.equals(connection.target)
                && Objects.equals(trustGroup, connection.trustGroup)
                && initialization.equals(connection.initialization)
                && certificate.equals(connection.certificate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetLocation, target, certificate, initialization, trustGroup);
    }

    @Override
    public String toString() {
        return target.toString() + " at " + targetLocation;
    }
}
