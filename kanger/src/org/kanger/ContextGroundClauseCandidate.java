/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;

/** Actual staged assertion checked by the scoped ground family. Historical evidence, never a write lease. */
public final class ContextGroundClauseCandidate {
    public static final class Evaluation {
        public final ContextGroundClauseFamily.Status status;
        public final String reason;
        public final ContextProductionCandidate.Staged artifact;
        public final ContextGroundClauseFamily.Evaluation qualification;
        private Evaluation(ContextProductionCandidate.StagingResult staged,int rounds,int localRounds,int depth,int work,int events,int probes,int models,org.kanger.ContextOrdinaryResidualRounds.LedgerLimits limits) {
            artifact = staged.artifact;
            qualification = staged.prepared ? ContextGroundClauseFamily.evaluate(artifact.staged,artifact.foreign,rounds,localRounds,depth,work,events,probes,models,limits) : null;
            status = qualification == null ? ContextGroundClauseFamily.Status.INCOMPLETE : qualification.status;
            reason = qualification == null ? staged.reason : qualification.reason;
        }
    }
    public static Evaluation qualify(Snapshot base,List<Snapshot> foreign,String statement,int maximumRows,int rounds,int localRounds,int depth,int work,int events,int probes,int models) {
        return qualify(base,foreign,statement,maximumRows,rounds,localRounds,depth,work,events,probes,models,org.kanger.ContextOrdinaryResidualRounds.LedgerLimits.DEFAULT);
    }
    public static Evaluation qualify(Snapshot base,List<Snapshot> foreign,String statement,int maximumRows,int rounds,int localRounds,int depth,int work,int events,int probes,int models,org.kanger.ContextOrdinaryResidualRounds.LedgerLimits limits) {
        if(limits==null) throw new IllegalArgumentException("Ledger limits required");
        if (rounds < 1 || localRounds < 1 || depth < 0 || work < 1 || events < 1 || probes < 1 || models < 1)
            throw new IllegalArgumentException("Bounded ground candidate qualification required");
        return new Evaluation(ContextProductionCandidate.stage(base,foreign,statement,maximumRows,work,events),rounds,localRounds,depth,work,events,probes,models,limits);
    }
}
