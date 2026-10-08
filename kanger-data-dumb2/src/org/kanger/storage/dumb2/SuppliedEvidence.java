/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.FrontierDomain;
import org.kanger.Mind;
import org.kanger.SemanticTermSnapshot;
import org.kanger.enums.QueryPass;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/** A concrete detached fact obtained only from a decisive TRUE child batch. */
final class SuppliedEvidence {
    private final List<org.kanger.interfaces.internal.IContextFederation.ProofCause> proofs;
    private final String predicate;
    private final boolean negated;
    private final List<SemanticTermSnapshot> arguments;
    private final Set<RevisionRef> supports;

    private SuppliedEvidence(FrontierDomain frontier, List<SemanticTermSnapshot> arguments,
                             Set<RevisionRef> supports) {
        this(frontier.getPredicateName(), frontier.isNegated(), arguments, supports);
    }

    private SuppliedEvidence(String predicate, boolean negated,
                             List<SemanticTermSnapshot> arguments, Set<RevisionRef> supports) {
        this(predicate, negated, arguments, supports,
                Collections.<org.kanger.interfaces.internal.IContextFederation.ProofCause>emptyList());
    }
    private SuppliedEvidence(String predicate, boolean negated, List<SemanticTermSnapshot> arguments,
            Set<RevisionRef> supports, List<org.kanger.interfaces.internal.IContextFederation.ProofCause> proofs) {
        this.proofs = Collections.unmodifiableList(new ArrayList<>(proofs));
        this.predicate = predicate;
        this.negated = negated;
        this.arguments = Collections.unmodifiableList(new ArrayList<SemanticTermSnapshot>(arguments));
        this.supports = Collections.unmodifiableSet(new LinkedHashSet<RevisionRef>(supports));
    }

    private static SuppliedEvidence fromAnswer(FrontierDomain frontier, List<SemanticTermSnapshot> arguments,
            FrontierAnswer answer) {
        String key = org.kanger.ContextProofProjection.factKey(frontier.getPredicateName(), !frontier.isNegated(), arguments);
        List<org.kanger.interfaces.internal.IContextFederation.ProofCause> proofs = answer.getProofs().get(key);
        return new SuppliedEvidence(frontier.getPredicateName(), frontier.isNegated(), arguments,
                Collections.singleton(answer.getSource()), proofs == null
                        ? Collections.<org.kanger.interfaces.internal.IContextFederation.ProofCause>emptyList() : proofs);
    }

    static List<SuppliedEvidence> fromAnswers(List<FrontierAnswer> answers) {
        FrontierAggregate aggregate = FrontierAggregate.of(answers);
        if (aggregate.getTruth() != FrontierAggregate.Truth.TRUE) {
            return Collections.emptyList();
        }
        FrontierDomain frontier = aggregate.getInvocation().getFrontier();
        List<SuppliedEvidence> facts = new ArrayList<SuppliedEvidence>();
        for (FrontierAnswer answer : answers) {
            if (answer.getTruth() != FrontierAnswer.Truth.TRUE) {
                continue;
            }
            if (frontier.isGround()) {
                add(facts, fromAnswer(frontier, frontier.semanticArguments(
                        Collections.<String>emptyList(), Collections.<SemanticTermSnapshot>emptyList()),
                        answer));
            } else {
                for (List<FrontierAnswer.ValueRef> row : answer.getValues()) {
                    List<SemanticTermSnapshot> values = new ArrayList<SemanticTermSnapshot>();
                    for (FrontierAnswer.ValueRef value : row) {
                        values.add(value.getSemantic());
                    }
                    add(facts, fromAnswer(frontier,
                            frontier.semanticArguments(answer.getVariableOrder(), values),
                            answer));
                }
            }
        }
        return Collections.unmodifiableList(facts);
    }

    static void add(List<SuppliedEvidence> facts, SuppliedEvidence candidate) {
        for (int i = 0; i < facts.size(); ++i) {
            SuppliedEvidence previous = facts.get(i);
            if (previous.sameFact(candidate)) {
                Set<RevisionRef> supports = new LinkedHashSet<RevisionRef>(previous.supports);
                supports.addAll(candidate.supports);
                List<org.kanger.interfaces.internal.IContextFederation.ProofCause> proofs = new ArrayList<>(previous.proofs);
                for (org.kanger.interfaces.internal.IContextFederation.ProofCause proof : candidate.proofs)
                    if (!proofs.contains(proof)) proofs.add(proof);
                facts.set(i, new SuppliedEvidence(previous.predicate, previous.negated,
                        previous.arguments, supports, proofs));
                return;
            }
        }
        facts.add(candidate);
    }

    boolean matches(FrontierDomain child) {
        return predicate.equals(child.getPredicateName()) && negated == child.isNegated()
                && arguments.size() == child.getArgumentCount();
    }

    List<SemanticTermSnapshot> getArguments() {
        return arguments;
    }

    Set<RevisionRef> getSupports() {
        return supports;
    }

    boolean sameFact(SuppliedEvidence other) {
        if (!predicate.equals(other.predicate) || negated != other.negated
                || arguments.size() != other.arguments.size()) {
            return false;
        }
        for (int i = 0; i < arguments.size(); ++i) {
            if (!arguments.get(i).semanticallyEquals(other.arguments.get(i))) {
                return false;
            }
        }
        return true;
    }

    int semanticHash() {
        int hash = 31 * predicate.hashCode() + (negated ? 1 : 0);
        for (SemanticTermSnapshot value : arguments) {
            hash = 31 * hash + value.getType().ordinal();
            hash = 31 * hash + value.getHash();
        }
        return hash;
    }

    String diagnosticStatement() {
        StringBuilder source = new StringBuilder(negated ? "!~" : "!").append(predicate).append('(');
        for (int i = 0; i < arguments.size(); ++i) {
            if (i > 0) source.append(',');
            String value = arguments.get(i).materialize().toString();
            if (arguments.get(i).getType() == org.kanger.enums.DataType.STRING) {
                source.append("'").append(value.replace("'", "''")).append("'");
            } else source.append(value);
        }
        return source.append(");").toString();
    }

    void materialize(Mind target) throws Exception {
        Queue<ITerm> values = new LinkedList<ITerm>();
        StringBuilder source = new StringBuilder(negated ? "!~" : "!");
        source.append(predicate).append('(');
        for (int i = 0; i < arguments.size(); ++i) {
            if (i > 0) {
                source.append(',');
            }
            source.append('?');
            values.add(target.getTerms().projectSemantic(arguments.get(i).materialize()));
        }
        source.append(");");
        QueryPass pass = target.getQueryPass();
        target.setQueryPass(QueryPass.ACCEPT);
        try {
            // This is a donor fact inside an isolated operation, not an
            // independent authoritative ACCEPT against generated query demand.
            Rule rule = (Rule) target.compileLine(source.toString(), false, values);
            target.addContextProofs(org.kanger.ContextProofProjection.factKey(predicate, !negated, arguments), proofs);
            if (rule != null) {
                // Follow the native indexed donor-seed saturation boundary.
                // A null result here is an already existing concrete fact.
                target.link(rule, false);
            }
        } finally {
            target.setQueryPass(pass);
        }
    }
}
