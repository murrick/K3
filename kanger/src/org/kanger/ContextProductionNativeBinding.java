/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.IArgument;
import org.kanger.units.Domain;
import org.kanger.units.Rule;
import org.kanger.units.TVariable;

/** Bounded whole-primary text/native binding. Snapshot acquisition authority stays with its provider lease. */
public final class ContextProductionNativeBinding {
    public enum Kind { WITNESS_BUNDLE, CLAUSES }
    public static final class Entry {
        public final Source source;
        public final String statement;
        public final Kind kind;
        public final List<Clause> clauses;
        final ContextWitnessBundle.Bundle bundle;
        private Entry(Source source,String statement,ContextWitnessBundle.Bundle bundle,List<Clause> clauses) {
            this.source = source; this.statement = statement; this.bundle = bundle; kind = bundle == null ? Kind.CLAUSES : Kind.WITNESS_BUNDLE;
            this.clauses = Collections.unmodifiableList(new ArrayList<>(clauses));
        }
        public int witnesses() { return bundle == null ? 0 : bundle.witnesses; }
    }
    public static final class Snapshot {
        public final ContextProductionInventory inventory;
        public final List<Entry> primary;
        public final Set<Long> excludedGenerated;
        private Snapshot(ContextProductionInventory inventory,List<Entry> primary) {
            this.inventory = inventory; this.primary = Collections.unmodifiableList(new ArrayList<>(primary));
            Set<Long> generated = new LinkedHashSet<>(); for (ContextProductionInventory.Row row : inventory.generated) generated.add(row.nativeRule);
            excludedGenerated = Collections.unmodifiableSet(generated);
        }
    }
    public static final class Result {
        public final boolean bound;
        public final String reason;
        public final Snapshot snapshot;
        private Result(boolean bound,String reason,Snapshot snapshot) { this.bound = bound; this.reason = reason; this.snapshot = snapshot; }
    }
    private static boolean witness(Rule rule,Mind mind,Runnable work) throws Exception {
        boolean witness = false;
        for (List<Domain> branch : rule.getTree()) for (Domain domain : branch) for (IArgument argument : domain.getArguments()) {
            work.run(); if (argument.getType() == ArgumentType.TERM && argument.getValue(mind) != null && argument.getValue(mind).isCVariable()) witness = true;
        }
        return witness;
    }
    private static List<Clause> clauses(Rule rule,Mind mind,Runnable work) throws Exception {
        if (rule.isQuery() || rule.isGenerated() || rule.getTree().isEmpty() || rule.getTree().size() > 8)
            throw new IllegalStateException("Whole primary universal/ordinary clauses required");
        Set<Long> variables = new HashSet<>(); List<Clause> result = new ArrayList<>();
        for (List<Domain> branch : rule.getTree()) {
            work.run(); if (branch.isEmpty() || branch.size() > 3) throw new IllegalStateException("Native clause width limit");
            for (Domain domain : branch) {
                work.run(); if (domain.getRange() < 1 || domain.getRange() > 8) throw new IllegalStateException("Native clause arity limit");
                for (IArgument argument : domain.getArguments()) {
                    work.run();
                    if (argument.getType() == ArgumentType.TVARIABLE) {
                        TVariable variable = (TVariable)argument.getObject(mind);
                        if (variable.getRuleId() != rule.getId()) throw new IllegalStateException("Nonlocal native universal variable");
                        variables.add(variable.getId());
                    } else if (argument.getType() != ArgumentType.TERM || !SemanticTermSnapshot.isOrdinaryValue(argument.getValue(mind)))
                        throw new IllegalStateException("Unsupported native clause argument");
                }
            }
            Clause clause = Clause.shapeOf(branch,mind);
            if (clause == null) throw new IllegalStateException("Unsupported native clause semantics"); result.add(clause);
        }
        if (variables.size() > 3) throw new IllegalStateException("Native clause variable limit"); return result;
    }
    private static void sameClauses(List<Clause> left,List<Clause> right,Runnable work) {
        if (left.size() != right.size()) throw new IllegalStateException("Origin/native clause count differs");
        boolean[] used = new boolean[right.size()];
        for (Clause clause : left) {
            boolean found = false;
            for (int i=0;i<used.length;++i) { work.run(); if (!used[i] && clause.equivalent(right.get(i))) { used[i] = true; found = true; break; } }
            if (!found) throw new IllegalStateException("Origin/native signed clause or variable sharing differs");
        }
    }
    /** Rebuilt native IDs may differ; whole semantic shape and witness sharing may not. */
    static void sameEntry(Entry original,Entry rebuilt,Runnable work) {
        work.run(); if (original.kind != rebuilt.kind) throw new IllegalStateException("Candidate primary kind changed");
        if (original.bundle != null) {
            if (!ContextWitnessBundle.equivalent(original.bundle,rebuilt.bundle,work))
                throw new IllegalStateException("Candidate primary witness sharing changed");
        } else sameClauses(original.clauses,rebuilt.clauses,work);
    }
    private static void current(Mind mind,ContextProductionInventory inventory) throws Exception {
        ContextProductionInventory now = ContextProductionInventory.inspect(mind,inventory.context,inventory.revision,inventory.localX,
                inventory.initialization,inventory.rows.size());
        if (!inventory.fingerprint.equals(now.fingerprint)) throw new IllegalStateException("Native binding inventory changed");
    }
    public static Result bind(Mind mind,ContextProductionInventory inventory,int maximumWork,int maximumEvents) {
        if (maximumWork < 1 || maximumEvents < 1) throw new IllegalArgumentException("Bounded native binding required");
        int[] remaining = {maximumWork}; Runnable work = () -> { if (--remaining[0] < 0) throw new IllegalStateException("Native binding work limit"); };
        try {
            current(mind,inventory); List<Entry> entries = new ArrayList<>();
            for (ContextProductionInventory.Row row : inventory.primary) {
                work.run(); Rule nativeRule = (Rule)mind.getRules().get(row.nativeRule);
                if (nativeRule == null || nativeRule.isDeleted(mind) || mind.getRules().isGenerated(nativeRule)) throw new IllegalStateException("Primary native row unavailable");
                if (row.origin == null || row.origin.trim().isEmpty()) throw new IllegalStateException("Native primary origin missing");
                Source source = new Source(inventory.context,inventory.revision,row.nativeRule,inventory.localX,"inventory:"+inventory.fingerprint);
                User user = new User(); Mind replay = new Mind(user); user.setCurrentMind(replay);
                try (ContextProductionCapture capture = ContextProductionCapture.begin(replay,maximumEvents)) {
                    capture.replay(replay,source,row.origin); ContextProductionCapture.Snapshot parsed = capture.snapshot();
                    if (parsed.inputs.size() != 1 || !source.equals(parsed.inputs.get(0).source)) throw new IllegalStateException("One whole sourced origin required");
                    Rule reconstructed = (Rule)replay.getRules().get(parsed.inputs.get(0).nativeRule);
                    if (witness(nativeRule,mind,work)) {
                        ContextWitnessBundle.Bundle nativeBundle = ContextWitnessBundle.extract(source,nativeRule,mind,work);
                        ContextWitnessBundle.Bundle replayedBundle = ContextWitnessBundle.extract(source,reconstructed,replay,work);
                        if (!ContextWitnessBundle.equivalent(nativeBundle,replayedBundle,work)) throw new IllegalStateException("Origin/native whole witness sharing differs");
                        entries.add(new Entry(source,row.origin,nativeBundle,Collections.emptyList()));
                    } else {
                        List<Clause> nativeClauses = clauses(nativeRule,mind,work); sameClauses(nativeClauses,clauses(reconstructed,replay,work),work);
                        entries.add(new Entry(source,row.origin,null,nativeClauses));
                    }
                }
            }
            current(mind,inventory);
            return new Result(true,"Every effective primary row bound to its whole native declaration; cache excluded",new Snapshot(inventory,entries));
        } catch (Exception failure) { return new Result(false,failure.getMessage(),null); }
    }
}
