/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;

/** Detached single-assertion flat universal diagnostic. Never a publication capability. */
public final class ContextUniversalCandidate {
    public static final class Evaluation {
        public final ContextUniversalFamilyInspection.Status status;
        public final String reason;
        public final ContextProductionCandidate.Staged artifact;
        public final ContextUniversalFamilyInspection.Result qualification;
        private Evaluation(ContextProductionCandidate.StagingResult staged,int rounds,int localRounds,int depth,int instances,int models,int inspectionWork) {
            artifact=staged.artifact;
            qualification=staged.prepared ? ContextUniversalFamilyInspection.inspect(artifact.staged,artifact.foreign,rounds,localRounds,depth,instances,models,inspectionWork) : null;
            status=qualification==null ? ContextUniversalFamilyInspection.Status.INCOMPLETE : qualification.status;
            reason=qualification==null ? staged.reason : qualification.reason;
        }
    }
    /** Native staging and bounded inference have explicitly separate work allowances. */
    public static Evaluation qualify(Snapshot base,List<Snapshot> foreign,String statement,int maximumRows,int rounds,int localRounds,
            int depth,int instances,int models,int preparationWork,int preparationEvents,int inspectionWork) {
        if(rounds<1 || localRounds<1 || depth<0 || instances<1 || models<1 || inspectionWork<1)
            throw new IllegalArgumentException("Bounded universal candidate qualification required");
        return new Evaluation(ContextProductionCandidate.stage(base,foreign,statement,maximumRows,preparationWork,preparationEvents),
                rounds,localRounds,depth,instances,models,inspectionWork);
    }
}
