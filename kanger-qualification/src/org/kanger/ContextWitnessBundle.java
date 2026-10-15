/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionRoundsReference.Declaration;
import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.*;
import org.kanger.units.*;

/** Detached primary existential conjunction. Not a derived production or certificate. */
final class ContextWitnessBundle {
    static final class Slot {
        final int witness;
        final SemanticTermSnapshot value;
        Slot(int witness,SemanticTermSnapshot value) { this.witness = witness; this.value = value; }
    }
    static final class Atom {
        final String predicate;
        final boolean positive;
        final List<Slot> arguments;
        Atom(String predicate,boolean positive,List<Slot> arguments) {
            this.predicate = predicate; this.positive = positive;
            this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
        }
    }
    static final class Bundle {
        final Source source;
        final int witnesses;
        final List<Atom> atoms;
        private Bundle(Source source,int witnesses,List<Atom> atoms) {
            this.source = source; this.witnesses = witnesses;
            this.atoms = Collections.unmodifiableList(new ArrayList<>(atoms));
        }
        private String assertion() {
            StringBuilder out = new StringBuilder("!");
            for (int i=0;i<witnesses;++i) out.append("$ctxbundle").append(i).append(' ');
            out.append('(');
            for (int i=0;i<atoms.size();++i) {
                if (i != 0) out.append(" && "); Atom atom = atoms.get(i);
                if (!atom.positive) out.append('~'); out.append(atom.predicate).append('(');
                for (int j=0;j<atom.arguments.size();++j) {
                    if (j != 0) out.append(','); Slot slot = atom.arguments.get(j);
                    if (slot.witness >= 0) out.append("ctxbundle").append(slot.witness); else out.append('?');
                }
                out.append(')');
            }
            return out.append(");").toString();
        }
        private Queue<ITerm> project(Mind target,Runnable work) throws Exception {
            Queue<ITerm> queue = new LinkedList<>();
            for (Atom atom : atoms) for (Slot slot : atom.arguments) {
                work.run(); if (slot.witness < 0) queue.add(target.getTerms().projectSemantic(slot.value.materialize()));
            }
            return queue;
        }
    }
    private static Runnable budget(int maximumWork) {
        if (maximumWork < 1) throw new IllegalArgumentException("Bounded bundle operation required");
        int[] remaining = {maximumWork};
        return () -> { if (--remaining[0] < 0) throw new IllegalStateException("Witness bundle work limit"); };
    }
    private static Mind memory() throws Exception { User u = new User(); Mind m = new Mind(u); u.setCurrentMind(m); return m; }
    private static Bundle extract(Source source,Rule rule,Mind mind,Runnable work) throws Exception {
        List<Atom> atoms = new ArrayList<>(); Map<Long,Integer> witnesses = new LinkedHashMap<>();
        if (rule.isQuery() || rule.isGenerated() || rule.getTree().isEmpty() || rule.getTree().size() > 8)
            throw new IllegalArgumentException("Primary conjunction of at most eight atoms required");
        for (List<Domain> branch : rule.getTree()) {
            work.run();
            if (branch.size() != 1) throw new IllegalArgumentException("Disjunction or conditional witness bundle unsupported");
            Domain domain = branch.get(0);
            if (domain.isSystem(mind) || domain.isCalculated(mind) || domain.getRange() < 1 || domain.getRange() > 8
                    || "rule(1)".equals(domain.getPredicate(mind).toString(mind)))
                throw new IllegalArgumentException("Ordinary predicate atoms required");
            List<Slot> arguments = new ArrayList<>();
            for (IArgument argument : domain.getArguments()) {
                work.run();
                if (argument.getType() != ArgumentType.TERM) throw new IllegalArgumentException("Universal/function slots unsupported");
                ITerm value = argument.getValue(mind);
                if (value != null && value.isCVariable()) {
                    Term term = (Term)value;
                    if (term.getRuleId() != rule.getId() || term.isDomini() || term.getParent(mind) != null)
                        throw new IllegalArgumentException("Only primary local existential binders supported");
                    Integer index = witnesses.get(term.getId());
                    if (index == null) { index = witnesses.size(); witnesses.put(term.getId(),index); }
                    if (witnesses.size() > 4) throw new IllegalArgumentException("Witness bundle rank limit");
                    arguments.add(new Slot(index,null));
                } else {
                    if (!SemanticTermSnapshot.isOrdinaryValue(value)) throw new IllegalArgumentException("Ordinary fixed value required");
                    arguments.add(new Slot(-1,SemanticTermSnapshot.capture(value)));
                }
            }
            atoms.add(new Atom(domain.getPredicate(mind).getName(mind),domain.isAntc(),arguments));
        }
        if (witnesses.isEmpty()) throw new IllegalArgumentException("Existential witness required");
        return new Bundle(source,witnesses.size(),atoms);
    }
    static Bundle capture(Declaration declaration,int maximumWork) throws Exception {
        Runnable work = budget(maximumWork); Mind mind = memory();
        try (ContextProductionCapture capture = ContextProductionCapture.begin(mind,maximumWork)) {
            work.run(); capture.replay(mind,declaration.source,declaration.statement);
            Snapshot snapshot = capture.snapshot();
            if (snapshot.inputs.size() != 1 || !declaration.source.equals(snapshot.inputs.get(0).source))
                throw new IllegalStateException("One sourced primary assertion required");
            return extract(declaration.source,(Rule)mind.getRules().get(snapshot.inputs.get(0).nativeRule),mind,work);
        }
    }
    private static boolean equivalent(Bundle left,Bundle right,Runnable work) {
        if (left.witnesses != right.witnesses || left.atoms.size() != right.atoms.size()) return false;
        // Ordinals follow first occurrence across the whole conjunction, so this
        // preserves the global sharing bijection, not a per-atom renaming.
        for (int i=0;i<left.atoms.size();++i) {
            work.run(); Atom a = left.atoms.get(i), b = right.atoms.get(i);
            if (!a.predicate.equals(b.predicate) || a.positive != b.positive || a.arguments.size() != b.arguments.size()) return false;
            for (int j=0;j<a.arguments.size();++j) {
                work.run(); Slot x = a.arguments.get(j), y = b.arguments.get(j);
                if (x.witness != y.witness || (x.witness < 0 && !x.value.semanticallyEquals(y.value))) return false;
            }
        }
        return true;
    }
    /** Restores only into a fresh disposable Mind; no user Context is mutated. */
    static Mind restore(List<Bundle> bundles,int maximumWork) throws Exception {
        Runnable work = budget(maximumWork); Map<Source,Bundle> sources = new HashMap<>();
        for (Bundle bundle : bundles) {
            work.run(); Bundle old = sources.put(bundle.source,bundle);
            if (old != null && !equivalent(old,bundle,work)) throw new IllegalArgumentException("Conflicting source occurrence");
        }
        Mind target = memory();
        for (Bundle bundle : bundles) {
            work.run(); Rule rule = (Rule)target.compileLine(bundle.assertion(),false,bundle.project(target,work));
            if (rule == null || !equivalent(bundle,extract(bundle.source,rule,target,work),work))
                throw new IllegalStateException("Witness bundle roundtrip mismatch");
        }
        return target;
    }
}
