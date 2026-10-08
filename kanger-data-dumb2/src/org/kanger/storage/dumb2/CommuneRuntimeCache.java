/*
 * MIT License
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import java.util.*;

/**
 * Session-owned computed states. A transaction stores a State reference and
 * restores it without replaying member knowledge. Qualification certificates
 * are deliberately not a knowledge address: recertifying X does not change
 * a commune's pinned rules or private initialization commands.
 *
 * <p>The owner closes this cache when its Context session closes. Retaining
 * previous states until then keeps nested transaction checkpoints usable.
 * This class owns joint runtimes, not the member connection layers.</p>
 */
final class CommuneRuntimeCache implements AutoCloseable {
    private final Map<Key, CommuneRuntime> runtimes = new LinkedHashMap<>();
    private boolean closed;

    static final class State {
        private final Map<String, CommuneRuntime> groups;
        private State(Map<String, CommuneRuntime> groups) {
            this.groups = Collections.unmodifiableMap(new LinkedHashMap<>(groups));
        }
        Map<String, CommuneRuntime> groups() { return groups; }
    }

    /** Prepare the whole candidate atomically; a rejected group installs nothing. */
    synchronized State prepare(Map<String, ? extends List<ContextConnection>> groups) throws Exception {
        if (closed) throw new IllegalStateException("Commune cache is closed");
        Objects.requireNonNull(groups, "groups");
        Map<Key, CommuneRuntime> staged = new LinkedHashMap<>();
        Map<String, CommuneRuntime> result = new LinkedHashMap<>();
        // Validate every address before opening any member runtime.
        Map<String, Key> keys = new LinkedHashMap<>();
        Set<UUID> assigned = new HashSet<>();
        for (Map.Entry<String, ? extends List<ContextConnection>> entry : groups.entrySet()) {
            String name = entry.getKey();
            if (name == null || name.isEmpty() || name.length() > 256
                    || name.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c)))
                throw new IllegalArgumentException("Invalid trust commune name");
            Key key = new Key(name, entry.getValue());
            for (Member member : key.members)
                if (!assigned.add(member.target.getContextId()))
                    throw new IllegalArgumentException("A Context may belong to only one trust commune");
            keys.put(name, key);
        }
        try {
            for (Map.Entry<String, Key> entry : keys.entrySet()) {
                CommuneRuntime runtime = runtimes.get(entry.getValue());
                if (runtime == null) {
                    runtime = CommuneRuntime.prepare(groups.get(entry.getKey()), entry.getKey());
                    staged.put(entry.getValue(), runtime);
                }
                result.put(entry.getKey(), runtime);
            }
        } catch (Exception failure) {
            for (CommuneRuntime runtime : staged.values()) {
                try { runtime.close(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
        runtimes.putAll(staged);
        return new State(result);
    }

    @Override public synchronized void close() throws Exception {
        if (closed) return;
        Exception failure = null;
        for (CommuneRuntime runtime : runtimes.values()) {
            try { runtime.close(); }
            catch (Exception cleanup) {
                if (failure == null) failure = cleanup;
                else failure.addSuppressed(cleanup);
            }
        }
        runtimes.clear();
        closed = true;
        if (failure != null) throw failure;
    }

    private static final class Key {
        final String group;
        final List<Member> members;
        Key(String group, List<ContextConnection> connections) {
            if (connections == null || connections.isEmpty())
                throw new IllegalArgumentException("A commune requires at least one member");
            this.group = group;
            List<Member> ordered = new ArrayList<>();
            for (ContextConnection connection : connections) ordered.add(new Member(connection));
            ordered.sort(Comparator.comparing(m -> m.target.getContextId().toString()));
            members = Collections.unmodifiableList(ordered);
        }
        @Override public boolean equals(Object other) {
            return other instanceof Key && group.equals(((Key) other).group)
                    && members.equals(((Key) other).members);
        }
        @Override public int hashCode() { return Objects.hash(group, members); }
    }

    private static final class Member {
        final java.nio.file.Path location;
        final RevisionRef target;
        final List<String> initialization;
        final String semantics;
        Member(ContextConnection connection) {
            Objects.requireNonNull(connection, "connection");
            location = connection.getTargetLocation();
            target = connection.getTarget();
            initialization = connection.getInitialization();
            semantics = connection.getCertificate().getSemanticVersion();
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Member)) return false;
            Member member = (Member) other;
            return location.equals(member.location) && target.equals(member.target)
                    && initialization.equals(member.initialization) && semantics.equals(member.semantics);
        }
        @Override public int hashCode() { return Objects.hash(location, target, initialization, semantics); }
    }
}
