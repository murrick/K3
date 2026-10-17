/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalUnitRounds.*;
import org.kanger.ContextUniversalUnitProof.Proof;
import org.kanger.ContextUniversalQuotientModel.Model;

/** Bounded read-only synchronous flat universal family inspection. No native write admission. */
public final class ContextUniversalFamilyInspection {
    public enum Status { CONSISTENT, COLLISION, INCOMPLETE }
    public static final class Collision {
        public final Frontier input;
        public final List<Receipt> receipts;
        public final List<Proof> oppositions;
        private Collision(Frontier input,List<Receipt> receipts,List<Proof> oppositions) {
            this.input=input; this.receipts=Collections.unmodifiableList(new ArrayList<>(receipts));
            this.oppositions=Collections.unmodifiableList(new ArrayList<>(oppositions));
        }
    }
    public static final class Result {
        public final Status status;
        public final String reason;
        /** Number of complete common barriers, including a terminal stable/collision barrier. */
        public final int completedBarriers;
        public final ContextUniversalFamilyModel.Certificate certificate;
        public final Collision collision;
        private Result(Status status,String reason,int completedBarriers,ContextUniversalFamilyModel.Certificate certificate,Collision collision) {
            this.status=status; this.reason=reason; this.completedBarriers=completedBarriers; this.certificate=certificate; this.collision=collision;
        }
    }
    /** All cuts see one immutable baseline per barrier; no verdict from a partial family. */
    public static Result inspect(Snapshot x,List<Snapshot> foreign,int maximumCommonRounds,int maximumLocalRounds,
            int maximumDepth,int maximumInstances,int maximumModels,int maximumWork) {
        if(maximumCommonRounds<1 || maximumLocalRounds<1 || maximumDepth<0 || maximumInstances<1 || maximumModels<1 || maximumWork<1)
            throw new IllegalArgumentException("Bounded universal family inspection required");
        Runnable work=ContextGroundProductionRounds.budget(maximumWork);
        int[] instances={Math.min(maximumInstances,65536)},assignments={maximumModels}; int completed=0;
        try {
            Frontier input=ContextUniversalUnitRounds.startBound(x,foreign,work);
            for(int round=0;round<maximumCommonRounds;round++) {
                List<Receipt> receipts=new ArrayList<>();
                for(Snapshot cut:input.cuts) {
                    Receipt receipt=ContextUniversalUnitRounds.inspectBound(input,cut,maximumLocalRounds,maximumDepth,work,true,true);
                    if(!receipt.verified) throw new IllegalStateException(receipt.reason);
                    receipts.add(receipt);
                }
                Barrier barrier=ContextUniversalUnitRounds.mergeBound(input,receipts,maximumDepth,work);
                if(barrier.status==ContextUniversalUnitRounds.Status.INCOMPLETE) throw new IllegalStateException(barrier.reason);
                completed++;
                if(barrier.status==ContextUniversalUnitRounds.Status.COLLISION)
                    return new Result(Status.COLLISION,barrier.reason,completed,null,new Collision(input,receipts,barrier.oppositions));
                if(barrier.changed) { input=barrier.frontier; continue; }
                List<Model> models=new ArrayList<>();
                for(Receipt receipt:receipts) {
                    ContextUniversalQuotientModel.Result model=ContextUniversalQuotientModel.findBound(receipt.closureStep.input,instances,assignments,work);
                    if(!model.modeled) throw new IllegalStateException(model.reason);
                    models.add(model.model);
                }
                ContextUniversalFamilyModel.Result checked=ContextUniversalFamilyModel.certifyBound(input,receipts,models,maximumDepth,instances,work);
                if(!checked.certified) throw new IllegalStateException(checked.reason);
                return new Result(Status.CONSISTENT,"Exact stable flat universal family has all-value models; native write admission unknown",completed,checked.certificate,null);
            }
            throw new IllegalStateException("Universal family common round limit");
        } catch(Exception failure) { return new Result(Status.INCOMPLETE,failure.getMessage(),completed,null,null); }
    }
}
