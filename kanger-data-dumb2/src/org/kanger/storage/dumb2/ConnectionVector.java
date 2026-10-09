/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Immutable directed connection set selected for one working Context. */
final class ConnectionVector {

    private final List<ContextConnection> connections;
    private CommuneRuntimeCache runtimeCache;

    ConnectionVector(Collection<ContextConnection> source) {
        if (source == null) {
            throw new NullPointerException("source");
        }

        Map<UUID, ContextConnection> byContext =
                new LinkedHashMap<UUID, ContextConnection>();
        for (ContextConnection connection : source) {
            if (connection == null) {
                throw new NullPointerException("connection");
            }
            UUID targetId = connection.getTarget().getContextId();
            if (byContext.put(targetId, connection) != null) {
                throw new IllegalArgumentException(
                        "duplicate target Context connection: " + targetId);
            }
        }
        connections = Collections.unmodifiableList(
                new ArrayList<ContextConnection>(byContext.values()));
    }

    static ConnectionVector empty() {
        return new ConnectionVector(
                Collections.<ContextConnection>emptyList());
    }

    List<ContextConnection> getConnections() {
        return connections;
    }

    void prepareCommunes(CommuneRuntimeCache cache) throws Exception {
        cache.prepare(groups());
        runtimeCache = cache;
    }

    CommuneRuntimeCache runtimeCache() { return runtimeCache; }

    Collection<ContextConnection> retainCommunes(CommuneRuntimeCache cache) throws Exception {
        return cache.retain(groups());
    }

    private Map<String,List<ContextConnection>> groups() {
        Map<String,List<ContextConnection>> groups = new LinkedHashMap<>();
        for (ContextConnection c : connections) {
            if (c.getTrustGroup() != null)
                groups.computeIfAbsent(c.getTrustGroup(), ignored -> new ArrayList<>()).add(c);
        }
        return groups;
    }

    List<ContextConnection> executionConnections(CommuneRuntimeCache cache) throws Exception {
        CommuneRuntimeCache.State state = cache.prepare(groups());
        List<ContextConnection> result = new ArrayList<>();
        java.util.Set<String> included = new java.util.HashSet<>();
        for (ContextConnection connection : connections) {
            String group = connection.getTrustGroup();
            if (group == null) result.add(connection);
            else if (included.add(group)) {
                CommuneRuntime runtime = state.groups().get(group);
                // Stable physical routing address; all proof sources remain the real members.
                result.add(runtime.members().get(0).forCommune(runtime));
            }
        }
        return Collections.unmodifiableList(result);
    }

    boolean isEmpty() {
        return connections.isEmpty();
    }

    int size() {
        return connections.size();
    }

    ContextConnection find(UUID targetContextId) {
        if (targetContextId == null) {
            return null;
        }
        for (ContextConnection connection : connections) {
            if (targetContextId.equals(
                    connection.getTarget().getContextId())) {
                return connection;
            }
        }
        return null;
    }

    ConnectionVector with(ContextConnection replacement) {
        if (replacement == null) {
            throw new NullPointerException("replacement");
        }
        ArrayList<ContextConnection> copy =
                new ArrayList<ContextConnection>();
        UUID targetId = replacement.getTarget().getContextId();
        boolean replaced = false;
        for (ContextConnection connection : connections) {
            if (targetId.equals(connection.getTarget().getContextId())) {
                copy.add(replacement);
                replaced = true;
            } else {
                copy.add(connection);
            }
        }
        if (!replaced) {
            copy.add(replacement);
        }
        return new ConnectionVector(copy);
    }

    ConnectionVector without(UUID targetContextId) {
        ArrayList<ContextConnection> copy =
                new ArrayList<ContextConnection>();
        for (ContextConnection connection : connections) {
            if (!connection.getTarget().getContextId()
                    .equals(targetContextId)) {
                copy.add(connection);
            }
        }
        return new ConnectionVector(copy);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ConnectionVector
                && connections.equals(
                ((ConnectionVector) other).connections);
    }

    @Override
    public int hashCode() {
        return connections.hashCode();
    }
}
