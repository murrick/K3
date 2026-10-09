package org.kanger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.kanger.interfaces.internal.IContextFederation;

/** Mind-local proof provenance; inheritance and settlement preserve existing copy rules. */
final class ContextProvenance {
    private final Map<String, List<IContextFederation.ProofCause>> contextProofs = new LinkedHashMap<>();
    private final Map<String,List<IContextFederation.ProofCause>> contextRuleOrigins = new LinkedHashMap<>();
    private String communeName;
    private List<IContextFederation.Revision> communeMembers = Collections.emptyList();

    void configureCommuneProvenance(String name, List<IContextFederation.Revision> members) {
        communeName = name;
        communeMembers = Collections.unmodifiableList(new ArrayList<>(members));
    }
    String getCommuneName() { return communeName; }
    List<IContextFederation.Revision> getCommuneMembers() { return communeMembers; }
    void addContextRuleOrigin(String origin, IContextFederation.ProofCause proof) {
        contextRuleOrigins.computeIfAbsent(origin, ignored -> new ArrayList<>()).add(proof);
    }
    List<IContextFederation.ProofCause> getContextRuleOrigins(String origin) {
        List<IContextFederation.ProofCause> proofs = contextRuleOrigins.get(origin);
        return proofs == null ? Collections.emptyList() : Collections.unmodifiableList(proofs);
    }

    void clearContextProofs() { contextProofs.clear(); }

    List<IContextFederation.ProofCause> getContextProofs(String fact) {
        List<IContextFederation.ProofCause> proofs = contextProofs.get(fact);
        return proofs == null ? Collections.<IContextFederation.ProofCause>emptyList() : proofs;
    }

    void addContextProofs(String fact, List<IContextFederation.ProofCause> proofs) {
        if (fact == null || proofs == null || proofs.isEmpty()) return;
        List<IContextFederation.ProofCause> merged = new ArrayList<>(getContextProofs(fact));
        for (IContextFederation.ProofCause proof : proofs) if (!merged.contains(proof)) merged.add(proof);
        contextProofs.put(fact, Collections.unmodifiableList(merged));
    }

    void inheritFrom(ContextProvenance parent) {
        contextProofs.putAll(parent.contextProofs);
        for (Map.Entry<String, List<IContextFederation.ProofCause>> entry : parent.contextRuleOrigins.entrySet()) {
            contextRuleOrigins.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        communeName = parent.communeName;
        communeMembers = parent.communeMembers;
    }

    void replaceProofsFrom(ContextProvenance child) {
        contextProofs.clear();
        contextProofs.putAll(child.contextProofs);
    }
}
