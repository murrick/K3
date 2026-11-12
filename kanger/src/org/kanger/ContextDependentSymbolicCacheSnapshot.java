/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;

/** Immutable owner-bound checked cache snapshot. No primary-view, cold-replay, provider or admission authority. */
public final class ContextDependentSymbolicCacheSnapshot {
    private ContextDependentSymbolicCacheSnapshot() {}
    public enum Status {BOUND,INCOMPLETE}
    public static final class Snapshot {
        private final Mind owner;
        public final ContextProductionInventory inventory;
        public final ContextDependentCacheProofInspection.Result evidence;
        private Snapshot(Mind owner,ContextProductionInventory inventory,ContextDependentCacheProofInspection.Result evidence){this.owner=owner;this.inventory=inventory;this.evidence=evidence;}
        ContextProductionInventory diagnosticPrimaryView(Mind actual)throws Exception{requireCurrent(actual,inventory);return inventory.symbolicDiagnosticView(this);}
        /** Only this actual runtime and exact inventory may reuse the immutable local check. */
        public void requireCurrent(Mind actual,ContextProductionInventory supplied)throws Exception{
            need(actual==owner,"Symbolic cache snapshot belongs to a different runtime");need(supplied!=null&&inventory.fingerprint.equals(supplied.fingerprint),"Symbolic cache snapshot inventory identity/configuration changed");current(actual,inventory);
        }
    }
    public static final class Result {
        public final Status status;public final String reason;public final Snapshot snapshot;
        private Result(Status status,String reason,Snapshot snapshot){this.status=status;this.reason=reason;this.snapshot=snapshot;}
    }
    private static void need(boolean yes,String reason){if(!yes)throw new IllegalStateException(reason);}
    /** Scope guard only; it grants no proof or provider authority. */
    public static void requireUnrestrictedRuntime(Mind runtime){if(runtime==null)throw new IllegalArgumentException("Actual runtime required");need(DmzTerminalRestriction.activeNoGoods(runtime).isEmpty(),"Restricted branch cannot reuse an unrestricted symbolic cache snapshot");}
    private static void current(Mind mind,ContextProductionInventory inventory)throws Exception{
        requireUnrestrictedRuntime(mind);
        need(inventory.fingerprint.equals(ContextProductionInventory.inspect(mind,inventory.context,inventory.revision,inventory.localX,inventory.initialization,1000).fingerprint),"Changed actual symbolic cache snapshot inventory");
    }
    public static Result capture(Mind actual,ContextProductionInventory inventory)throws Exception{
        if(actual==null||inventory==null)throw new IllegalArgumentException("Actual runtime and complete inventory required");current(actual,inventory);
        try{try{
            ContextDependentCacheProofInspection.Result evidence=ContextDependentCacheProofInspection.inspect(actual,inventory);
            need(evidence.status==ContextDependentCacheProofInspection.Status.INSPECTED&&evidence.proof!=null&&evidence.ledger!=null,"Fresh complete primary-only cache proofs required: "+evidence.reason);
            need(inventory.fingerprint.equals(evidence.fingerprint)&&inventory.fingerprint.equals(evidence.ledger.fingerprint),"Proof and joint ledger must bind the exact full physical inventory");
            need(evidence.ledger.aliases==ContextDependentCacheAliasInspection.Status.RENAMING_MATCHED,"Native symbolic bindings do not preserve the checked joint witness identities: "+evidence.ledger.aliases);
            need(evidence.ledger.rootRows+evidence.ledger.productRows==inventory.generated.size(),"Complete actual generated occurrence coverage required");
            Set<Long> rows=new HashSet<>();for(ContextProductionInventory.Row row:inventory.generated)rows.add(row.nativeRule);Set<Long> products=new HashSet<>();for(ContextDependentCacheAliasInspection.Binding binding:evidence.bindings)need(rows.contains(binding.nativeRule)&&products.add(binding.nativeRule),"Unique actual demanded product binding required");need(products.size()==evidence.ledger.productRows,"Exact actual projected row coverage required");
            current(actual,inventory);Snapshot snapshot=new Snapshot(actual,inventory,evidence);snapshot.requireCurrent(actual,inventory);
            return new Result(Status.BOUND,"Fresh primary-only coverage and matching joint witness identities bound to this exact runtime inventory; no cold replay, native history, provider or family admission authority",snapshot);
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw interrupted;}catch(Exception failure){return new Result(Status.INCOMPLETE,failure.getMessage(),null);}}finally{current(actual,inventory);}
    }
}
