/*
 * MIT License
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.kanger.ContextQualification;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.exception.CommandErrorException;

/**
 * One isolated, qualified joint knowledge state. This runtime owns no storage
 * provider and cannot publish its derived productions into a member or X.
 * Its owner retains the instance while a transaction may restore this state.
 */
final class CommuneRuntime implements AutoCloseable {
    private final User user;
    private final Mind root;
    private final Mind layer;
    private final List<ContextConnection> members;
    private boolean closed;

    private CommuneRuntime(User user, Mind root, Mind layer, List<ContextConnection> members) {
        this.user = user;
        this.root = root;
        this.layer = layer;
        this.members = Collections.unmodifiableList(new ArrayList<>(members));
    }

    static CommuneRuntime prepare(List<ContextConnection> members) throws Exception {
        return prepare(members, null);
    }

    static CommuneRuntime prepare(List<ContextConnection> members, String group) throws Exception {
        if (members == null || members.isEmpty())
            throw new IllegalArgumentException("A commune requires at least one member");
        List<ContextConnection> ordered = new ArrayList<>(members);
        ordered.sort(Comparator.comparing(c -> c.getTarget().getContextId().toString()));
        java.util.Set<java.util.UUID> identities = new java.util.HashSet<>();
        for (ContextConnection member : ordered)
            if (!identities.add(member.getTarget().getContextId()))
                throw new IllegalArgumentException("Duplicate commune member");
        User user = new User();
        Mind root = new Mind(user);
        user.setCurrentMind(root);
        Mind layer = Mind.contextConnectionLayer(root);
        user.setCurrentMind(layer);
        boolean accepted = false;
        try {
            // Replay all authoritative knowledge before qualifying or asking
            // anything. Member-local generated productions are not imported.
            List<org.kanger.interfaces.internal.IContextFederation.Revision> pins = new ArrayList<>();
            for (ContextConnection member : ordered) {
                Mind source = member.layer();
                org.kanger.interfaces.internal.IContextFederation.Revision pin =
                        new org.kanger.interfaces.internal.IContextFederation.Revision(
                                member.getTarget().getContextId(), member.getTarget().getRevision());
                PairQualification.PortableSource.capture(source).replay(layer, pin);
                pins.add(pin);
                for (org.kanger.interfaces.IRule candidate : source.getRules()) {
                    org.kanger.units.Rule rule = (org.kanger.units.Rule) candidate;
                    if (rule.isGenerated() || rule.isDeleted(source)) continue;
                    layer.addContextRuleOrigin(rule.getOrigin(),
                            new org.kanger.interfaces.internal.IContextFederation.ProofCause(
                                    rule.getId(), rule.toString(source), null, "", false,
                                    Collections.emptyList(), false, null, null, pin,
                                    !member.getInitialization().isEmpty()));
                }
            }
            layer.configureCommuneProvenance(group, pins);
            ContextQualification qualification = ContextQualification.inspect(layer, false);
            if (!qualification.isValid())
                throw new CommandErrorException("Trust commune contains incompatible knowledge");
            layer.link(null, false);
            CommuneRuntime runtime = new CommuneRuntime(user, root, layer, ordered);
            accepted = true;
            return runtime;
        } finally {
            if (!accepted) {
                root.discardEphemeral(layer);
                user.setCurrentMind(root);
            }
        }
    }

    synchronized Mind mind() {
        if (closed) throw new IllegalStateException("Commune runtime is closed");
        return layer;
    }

    List<ContextConnection> members() { return members; }

    @Override public synchronized void close() throws Exception {
        if (closed) return;
        root.discardEphemeral(layer);
        user.setCurrentMind(root);
        closed = true;
    }
}
