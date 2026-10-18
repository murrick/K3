/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.Support;
import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.*;
import org.kanger.units.*;

/** Detached match-time witness metadata. Producer labels alone are not a proof. */
public final class ContextWitnessProjection {
    public static final class Node {
        public final long term, ownerRule;
        public final int index;
        public final boolean domini;
        private Node(Term term) { this.term = term.getId(); ownerRule = term.getRuleId(); index = term.getIndex(); domini = term.isDomini(); }
    }
    public static final class Slot {
        public final SemanticTermSnapshot value;
        /** Child first, ultimate observed parent last; IDs belong to this capture only. */
        public final List<Node> lineage;
        private Slot(ITerm value,Mind mind) throws Exception {
            List<Node> path = new ArrayList<>(); Set<Long> seen = new HashSet<>();
            if (value != null && value.isCVariable()) {
                while (value != null) {
                    if (!(value instanceof Term) || !value.isCVariable() || !seen.add(value.getId()) || path.size() == 8)
                        throw new IllegalStateException("Unsupported witness projection lineage");
                    Term term = (Term)value; path.add(new Node(term)); value = term.getParent(mind);
                }
                this.value = null;
            } else {
                if (!SemanticTermSnapshot.isOrdinaryValue(value)) throw new IllegalStateException("Nonordinary witness projection slot");
                this.value = SemanticTermSnapshot.capture(value);
            }
            lineage = Collections.unmodifiableList(path);
        }
    }
    public static final class Literal {
        public final String predicate;
        public final boolean positive;
        public final List<Slot> arguments;
        private Literal(Domain domain,Mind mind,boolean premise) throws Exception {
            if (!domain.isComplete() || domain.isSystem(mind) || domain.isCalculated(mind)
                    || domain.getRange() < 0 || domain.getRange() > 8
                    || "rule(1)".equals(domain.getPredicate(mind).toString(mind)))
                throw new IllegalStateException("Unsupported witness projection literal");
            predicate = domain.getPredicate(mind).getName(mind); positive = premise ? !domain.isAntc() : domain.isAntc();
            List<Slot> slots = new ArrayList<>();
            for (IArgument argument : domain.getArguments()) {
                if (argument.getType() == ArgumentType.FUNCTION) throw new IllegalStateException("Calculated witness projection slot");
                slots.add(new Slot(argument.getValue(mind),mind));
            }
            arguments = Collections.unmodifiableList(slots);
        }
    }
    public final long nativeRule;
    public final Literal conclusion;
    public final List<Literal> premises;
    public final List<Support> producerSupports;
    private ContextWitnessProjection(long rule,Literal conclusion,List<Literal> premises,List<Support> supports) {
        nativeRule = rule; this.conclusion = conclusion;
        this.premises = Collections.unmodifiableList(new ArrayList<>(premises));
        producerSupports = Collections.unmodifiableList(new ArrayList<>(supports));
    }
    static ContextWitnessProjection observe(Mind mind,Domain conclusion,List<Domain> branch,List<Support> supports) throws Exception {
        if (branch.size() > 8) throw new IllegalStateException("Witness projection branch limit");
        Literal result = new Literal(conclusion,mind,false); List<Literal> premises = new ArrayList<>();
        boolean witness = result.arguments.stream().anyMatch(s -> !s.lineage.isEmpty());
        for (Domain domain : branch) if (domain != conclusion) {
            if (!domain.isExcluded(mind)) throw new IllegalStateException("Unclosed witness projection premise");
            Literal premise = new Literal(domain,mind,true); premises.add(premise);
            witness |= premise.arguments.stream().anyMatch(s -> !s.lineage.isEmpty());
        }
        if (!witness) return null;
        if (supports.isEmpty()) throw new IllegalStateException("Unlabelled witness projection producer");
        return new ContextWitnessProjection(conclusion.getRuleId(),result,premises,supports);
    }
    /** One actual createStored result; never a global witness-equality assertion. */
    public static final class Materialization {
        public final long producerRule, storedRule;
        public final Literal matched, stored;
        public final List<Support> producerSupports;
        private Materialization(Mind mind,Domain producer,Rule result,List<Support> supports) throws Exception {
            producerRule = producer.getRuleId(); storedRule = result.getId();
            matched = new Literal(producer,mind,false); stored = new Literal(result.getDomain(),mind,false);
            producerSupports = Collections.unmodifiableList(new ArrayList<>(supports));
        }
    }
    static Materialization materialized(Mind mind,Domain producer,Rule result,List<Support> supports) throws Exception {
        boolean witness = false;
        for (Domain domain : Arrays.asList(producer,result.getDomain())) for (IArgument argument : domain.getArguments()) {
            ITerm value = argument.getValue(mind); witness |= value != null && value.isCVariable();
        }
        if (!witness) return null;
        if (supports.isEmpty()) throw new IllegalStateException("Unlabelled witness materialization producer");
        return new Materialization(mind,producer,result,supports);
    }

}
