/*
 * MIT License
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.ICause;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.internal.IContextFederation;
import org.kanger.primitives.Cause;
import org.kanger.units.Rule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Projects the same native rule/donor provenance used by solution tree. */
public final class ContextProofProjection {
    private ContextProofProjection() { }
    public static IContextFederation.RuleRow solution(Mind mind, IRule rule) throws Exception {
        return solution(mind, rule, null, null);
    }
    public static IContextFederation.RuleRow solution(Mind mind, IRule rule,
            IContextFederation.Revision source, String query) throws Exception {
        Set<IRule> path = Collections.newSetFromMap(new IdentityHashMap<IRule,Boolean>());
        return new IContextFederation.RuleRow(rule.getId(), ((Rule) rule).toString(mind),
                rule.isGenerated(), rule.getComment(), Collections.<List<String>>emptyList(), causes(mind, rule, path, source, query));
    }
    private static List<IContextFederation.ProofCause> causes(Mind mind, IRule rule, Set<IRule> path,
            IContextFederation.Revision source, String query) throws Exception {
        List<IContextFederation.ProofCause> result = new ArrayList<>();
        path.add(rule);
        try {
            for (ICause cause : rule.getCauses()) {
                IRule reason = cause.getRule(mind);
                IRule donor = cause.getDonor(mind);
                boolean cycle = donor != null && path.contains(donor);
                String donorSource = ((Cause) cause).getDonor().toString(mind);
                boolean hypothesis = false;
                if (donor == null || donor.getCauses().isEmpty()) {
                    for (org.kanger.interfaces.IHypothesis assumption : mind.getHypothesis())
                        if (donorSource.equals(((org.kanger.primitives.Hypothesis) assumption).toString(mind)))
                            hypothesis = true;
                }
                result.add(new IContextFederation.ProofCause(reason == null ? -1 : reason.getId(),
                        reason == null ? "" : ((Rule) reason).toString(mind), donor == null ? null : donor.getId(),
                        donor == null ? ((Cause) cause).getDonor().toString(mind) : ((Rule) donor).toString(mind),
                        cycle, donor == null || cycle ? Collections.<IContextFederation.ProofCause>emptyList()
                                : causes(mind, donor, path, source, query), hypothesis,
                        hypothesis ? source : null, hypothesis ? query : null));
            }
            return result;
        } finally { path.remove(rule); }
    }
}
