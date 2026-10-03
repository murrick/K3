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
