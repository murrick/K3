/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextProductionNativeBinding.Snapshot;

/** Detached single-assertion trial. A historical diagnostic, never permission to publish. */
public final class ContextProductionCandidate {
    public static final class Artifact {
        public final UUID identity;
        public final String statement;
        public final Snapshot base, staged;
        public final Source candidate;
        public final Map<Source,Source> rebased;
        public final ContextProductionFamilyAssembly.Artifact family;
        private Artifact(UUID identity,String statement,Snapshot base,Snapshot staged,Source candidate,
                Map<Source,Source> rebased,ContextProductionFamilyAssembly.Artifact family) {
            this.identity = identity; this.statement = statement; this.base = base; this.staged = staged; this.candidate = candidate;
            this.rebased = Collections.unmodifiableMap(new LinkedHashMap<>(rebased)); this.family = family;
        }
        public ContextProductionFamilyAssembly.Evaluation evaluate(int rounds,int depth,int work,int events) {
            return family.evaluate(rounds,depth,work,events);
        }
    }
    public static final class Result {
        public final boolean prepared;
        public final String reason;
        public final Artifact artifact;
        private Result(boolean prepared,String reason,Artifact artifact) { this.prepared = prepared; this.reason = reason; this.artifact = artifact; }
    }
    public static final class Evaluation {
        public final ContextProductionFamilyAssembly.Status status;
        public final String reason;
        public final Artifact artifact;
        public final ContextProductionFamilyAssembly.Evaluation qualification;
        private Evaluation(Result prepared,int rounds,int depth,int work,int events) {
            artifact = prepared.artifact;
            qualification = prepared.prepared ? artifact.evaluate(rounds,depth,work,events) : null;
            status = qualification == null ? ContextProductionFamilyAssembly.Status.INCOMPLETE : qualification.status;
            reason = qualification == null ? prepared.reason : qualification.reason;
        }
    }
    private static ContextProductionInventory inventory(Mind mind,Snapshot base,UUID identity,int maximumRows,Runnable work) throws Exception {
        ContextProductionInventory inventory = ContextProductionInventory.inspect(mind,base.inventory.context,base.inventory.revision,true,
                Collections.singletonList("candidate:"+identity),maximumRows);
        for (ContextProductionInventory.Row row : inventory.rows) work.run();
        return inventory;
    }
    private static long append(Mind mind,Snapshot base,UUID identity,String statement,int maximumRows,Runnable work) throws Exception {
        ContextProductionInventory before = inventory(mind,base,identity,maximumRows,work);
        if (!mind.compile(statement,null,false)) throw new IllegalStateException("Candidate private native compilation refused");
        ContextProductionInventory after = inventory(mind,base,identity,maximumRows,work);
        Set<Long> old = new HashSet<>(); for (ContextProductionInventory.Row row : before.primary) { work.run(); old.add(row.nativeRule); }
        long added = -1;
        for (ContextProductionInventory.Row row : after.primary) {
            work.run(); if (old.remove(row.nativeRule)) continue;
            if (added >= 0) throw new IllegalStateException("Exactly one new primary occurrence required");
            added = row.nativeRule;
        }
        if (!old.isEmpty() || added < 0 || after.primary.size() != before.primary.size()+1)
            throw new IllegalStateException("Candidate duplicate/replacement is outside append-only scope");
        return added;
    }
    public static Result prepare(Snapshot base,List<Snapshot> foreign,String statement,int maximumRows,int maximumWork,int maximumEvents) {
        if (maximumRows < 1 || maximumRows > 10000 || maximumWork < 1 || maximumEvents < 1)
            throw new IllegalArgumentException("Bounded candidate preparation required");
        int[] remaining = {maximumWork}; Runnable work = () -> { if (--remaining[0] < 0) throw new IllegalStateException("Candidate preparation work limit"); };
        try {
            // Same single primary assertion boundary as sourced replay. No query or source script.
            if (statement == null || !statement.startsWith("!")) throw new IllegalStateException("One candidate assertion required");
            org.kanger.compiler.Token token = org.kanger.enums.Tools.extractLine(statement,null);
            if (token == null || org.kanger.enums.Tools.extractLine(statement,token) != null)
                throw new IllegalStateException("Exactly one complete candidate assertion required");
            List<Snapshot> sources = new ArrayList<>(foreign); ContextProductionFamilyAssembly.identity(base,true,work);
            if (sources.size() > 15) throw new IllegalStateException("Candidate source limit");
            int available = maximumRows; Set<UUID> contexts = new HashSet<>(); contexts.add(base.inventory.context);
            for (Snapshot source : sources) {
                ContextProductionFamilyAssembly.identity(source,false,work);
                if (!contexts.add(source.inventory.context)) throw new IllegalStateException("Repeated candidate context");
                available -= source.inventory.rows.size(); work.run();
            }
            if (available < 1) throw new IllegalStateException("Candidate total inventory row limit");
            UUID identity = UUID.randomUUID();
            // Preflight the complete candidate in a separate native runtime before composing it with X.
            User probeUser = new User(); Mind probe = new Mind(probeUser); probeUser.setCurrentMind(probe);
            append(probe,base,identity,statement,available,work);
            ContextProductionInventory probeInventory = inventory(probe,base,identity,available,work);
            ContextProductionNativeBinding.Result candidateShape = ContextProductionNativeBinding.bind(probe,probeInventory,maximumWork,maximumEvents);
            if (!candidateShape.bound || candidateShape.snapshot.primary.size() != 1)
                throw new IllegalStateException("Candidate whole declaration unsupported: "+candidateShape.reason);
            User user = new User(); Mind privateX = new Mind(user); user.setCurrentMind(privateX);
            Map<Source,Long> occurrences = new LinkedHashMap<>();
            for (Entry entry : base.primary) {
                work.run(); occurrences.put(entry.source,append(privateX,base,identity,entry.statement,available,work));
            }
            // Authenticate the rebuilt base before adding the candidate; no primary source is lost.
            ContextProductionNativeBinding.Result rebuilt = ContextProductionNativeBinding.bind(privateX,
                    inventory(privateX,base,identity,available,work),maximumWork,maximumEvents);
            if (!rebuilt.bound || rebuilt.snapshot.primary.size() != base.primary.size())
                throw new IllegalStateException("Candidate base reconstruction incomplete");
            Map<Long,Entry> before = new HashMap<>(); for (Entry entry : rebuilt.snapshot.primary) before.put(entry.source.rule,entry);
            for (Entry original : base.primary) ContextProductionNativeBinding.sameEntry(original,before.get(occurrences.get(original.source)),work);
            long candidateId = append(privateX,base,identity,statement,available,work);
            ContextProductionNativeBinding.Result staged = ContextProductionNativeBinding.bind(privateX,
                    inventory(privateX,base,identity,available,work),maximumWork,maximumEvents);
            if (!staged.bound) throw new IllegalStateException("Candidate native binding incomplete: "+staged.reason);
            Map<Long,Entry> indexed = new HashMap<>(); for (Entry entry : staged.snapshot.primary) indexed.put(entry.source.rule,entry);
            Map<Source,Source> rebased = new LinkedHashMap<>();
            for (Entry original : base.primary) {
                work.run(); Entry entry = indexed.get(occurrences.get(original.source));
                if (entry == null) throw new IllegalStateException("Candidate lost an original primary occurrence");
                ContextProductionNativeBinding.sameEntry(original,entry,work); rebased.put(original.source,entry.source);
            }
            Entry candidate = indexed.get(candidateId);
            if (candidate == null || indexed.size() != rebased.size()+1) throw new IllegalStateException("Candidate primary inventory is incomplete");
            ContextProductionNativeBinding.sameEntry(candidateShape.snapshot.primary.get(0),candidate,work);
            ContextProductionFamilyAssembly.Result assembly = ContextProductionFamilyAssembly.assemble(staged.snapshot,sources,maximumWork);
            if (!assembly.assembled) throw new IllegalStateException("Candidate family assembly incomplete: "+assembly.reason);
            return new Result(true,"Detached native-bound candidate and complete original-source mapping prepared; publication pending",
                    new Artifact(identity,statement,base,staged.snapshot,candidate.source,rebased,assembly.artifact));
        } catch (Exception failure) { return new Result(false,failure.getMessage(),null); }
    }
    public static Evaluation qualify(Snapshot base,List<Snapshot> foreign,String statement,int maximumRows,int rounds,int depth,int work,int events) {
        return new Evaluation(prepare(base,foreign,statement,maximumRows,work,events),rounds,depth,work,events);
    }
}
