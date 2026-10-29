/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalFrozenConstraints.Proof;
import org.kanger.ContextWitnessResultTransport.*;

/** Explicit all-supplied-cut fixed/unit/compound barriers with actual local stability certificates. */
final class ContextUniversalGroupedRounds {
    enum Status { STABLE_GROUPED_POOLS, REFUTED, INCOMPLETE }
    static final class Refutation {
        final Cut cut;final int round;final boolean xRefuted,foreignRefuted;
        final UUID inputIdentity,symbolicIdentity,compoundIdentity;
        final List<Proof> proofs;
        private Refutation(Cut cut,int round,ContextUniversalFrozenConstraints.Result checked,int maximumWork) {
            this.cut=cut;this.round=round;inputIdentity=checked.common.identity;symbolicIdentity=checked.exchange==null?null:checked.exchange.identity;compoundIdentity=checked.compounds.identity;xRefuted=checked.xRefuted;foreignRefuted=checked.foreignRefuted;
            proofs=ContextUniversalFrozenConstraints.rejectionEvidence(checked,maximumWork);
        }
    }
    static final class Round {
        final int number;final Packet input;final Map<Cut,PrivatePool> privateInput;
        final Map<Cut,ContextUniversalFrozenConstraints.Result> checks;
        final Map<Cut,ContextUniversalSignedProductAudit.Result> products;
        final ContextUniversalSignedUnitExchange.Exchange symbolicInput,symbolicOutput;
        final Map<Cut,ContextUniversalGroupedLocalRounds.Certificate> certificates;
        final ContextUniversalCompoundExchange.Exchange compoundInput,compoundOutput;
        final ConstraintBarrier barrier;final boolean commonChanged,privateChanged,symbolicChanged,compoundChanged;
        private Round(int number,Packet input,Map<Cut,PrivatePool> pools,Map<Cut,ContextUniversalFrozenConstraints.Result> checks,Map<Cut,ContextUniversalSignedProductAudit.Result> products,ConstraintBarrier barrier,boolean commonChanged,boolean privateChanged,ContextUniversalSignedUnitExchange.Exchange symbolicInput,ContextUniversalSignedUnitExchange.Exchange symbolicOutput,boolean symbolicChanged,ContextUniversalCompoundExchange.Exchange compoundInput,ContextUniversalCompoundExchange.Exchange compoundOutput,boolean compoundChanged,Map<Cut,ContextUniversalGroupedLocalRounds.Certificate> certificates) {
            this.certificates=Collections.unmodifiableMap(new LinkedHashMap<>(certificates));this.compoundInput=compoundInput;this.compoundOutput=compoundOutput;this.compoundChanged=compoundChanged;
            this.number=number;this.input=input;privateInput=Collections.unmodifiableMap(new LinkedHashMap<>(pools));this.checks=Collections.unmodifiableMap(new LinkedHashMap<>(checks));
            this.symbolicInput=symbolicInput;this.symbolicOutput=symbolicOutput;this.symbolicChanged=symbolicChanged;this.products=Collections.unmodifiableMap(new LinkedHashMap<>(products));this.barrier=barrier;this.commonChanged=commonChanged;this.privateChanged=privateChanged;
        }
    }
    static final class Result {
        final Status status;final String phase,reason;final int completedRounds;
        final Packet input;final ConstraintBarrier output;final ContextUniversalSignedUnitExchange.Exchange symbolicOutput;final ContextUniversalCompoundExchange.Exchange compoundOutput;final List<Round> history;final Map<Cut,Refutation> refutations;
        private Result(Status status,String phase,String reason,int completedRounds,Packet input,ConstraintBarrier output,List<Round> history,Map<Cut,Refutation> refutations) {
            this.status=status;this.phase=phase;this.reason=reason;this.completedRounds=completedRounds;this.input=input;this.output=output;this.symbolicOutput=output==null?null:history.get(history.size()-1).symbolicOutput;
            this.compoundOutput=output==null?null:history.get(history.size()-1).compoundOutput;
            this.history=Collections.unmodifiableList(new ArrayList<>(history));this.refutations=Collections.unmodifiableMap(new LinkedHashMap<>(refutations));
        }
    }
    private static void require(boolean valid,String reason){if(!valid)throw new IllegalStateException(reason);}
    /** Explicit continuation from an actual all-supplied-cut compound exchange; no default activation. */
    static Result run(Snapshot x,List<Snapshot> foreign,Packet initial,Map<Cut,PrivatePool> initialPools,ContextUniversalSignedUnitExchange.Exchange initialExchange,ContextUniversalCompoundExchange.Exchange initialCompounds,ContextUniversalFrozenBranch.Limits limits,ContextUniversalGroupedLocalRounds.Limits localLimits,int maximumRounds,int maximumWork) {
        if(maximumRounds<1||maximumRounds>64||maximumWork<1)throw new IllegalArgumentException("Bounded grouped rounds required");
        Runnable work=ContextGroundProductionRounds.budget(maximumWork);
        return run(x,foreign,initial,initialPools,initialExchange,initialCompounds,limits,localLimits,maximumRounds,maximumWork,work,0);
    }
    static final class SessionRun {
        final Result result;final ContextUniversalValidationSession.Metrics metrics;
        private SessionRun(Result result,ContextUniversalValidationSession.Metrics metrics){this.result=result;this.metrics=metrics;}
    }
    /** Explicit whole-invocation session, with ONE cumulative owning budget across all rounds. */
    static SessionRun runWithValidationReuse(Snapshot x,List<Snapshot> foreign,Packet initial,Map<Cut,PrivatePool> initialPools,ContextUniversalSignedUnitExchange.Exchange initialExchange,ContextUniversalCompoundExchange.Exchange initialCompounds,ContextUniversalFrozenBranch.Limits limits,ContextUniversalGroupedLocalRounds.Limits localLimits,int maximumRounds,int maximumWork,int capacity) {
        if(maximumRounds<1||maximumRounds>64||maximumWork<1)throw new IllegalArgumentException("Bounded grouped rounds required");
        ContextUniversalValidationSession.Scope scope=ContextUniversalValidationSession.open(maximumWork,capacity);Result result;
        try{result=run(x,foreign,initial,initialPools,initialExchange,initialCompounds,limits,localLimits,maximumRounds,maximumWork,scope.work,capacity);}finally{scope.close();}
        return new SessionRun(result,scope.metrics());
    }
    private static Result run(Snapshot x,List<Snapshot> foreign,Packet initial,Map<Cut,PrivatePool> initialPools,ContextUniversalSignedUnitExchange.Exchange initialExchange,ContextUniversalCompoundExchange.Exchange initialCompounds,ContextUniversalFrozenBranch.Limits limits,ContextUniversalGroupedLocalRounds.Limits localLimits,int maximumRounds,int maximumWork,Runnable work,int validationCapacity) {
        String phase="preflight";int completed=0;
        try {
            require(x!=null&&foreign!=null&&foreign.size()<=15&&initial!=null&&initialPools!=null&&limits!=null&&localLimits!=null&&initialCompounds!=null,"Actual supplied signed native/common/private inputs required");
            Map<Cut,Snapshot> targets=new LinkedHashMap<>();ContextProductionFamilyAssembly.identity(x,true,work);Cut self=Cut.of(x);targets.put(self,null);
            for(Snapshot snapshot:new ArrayList<>(foreign)){work.run();require(snapshot!=null,"Actual foreign snapshot required");ContextProductionFamilyAssembly.identity(snapshot,false,work);ContextProductionFamilyAssembly.disjoint(x,snapshot,work);Cut cut=Cut.of(snapshot);require(!targets.containsKey(cut),"Repeated supplied signed cut");targets.put(cut,snapshot);}
            List<Cut> cuts=new ArrayList<>(targets.keySet());ContextWitnessResultTransport.family(initial,cuts,maximumWork);Map<Cut,PrivatePool> pools=new LinkedHashMap<>(initialPools);
            for(Map.Entry<Cut,PrivatePool> e:pools.entrySet()){work.run();PrivatePool pool=e.getValue();require(pool!=null&&targets.containsKey(e.getKey())&&!e.getKey().equals(self)&&pool.cut.equals(e.getKey())&&pool.common==initial&&pool.x==x&&pool.snapshot==targets.get(e.getKey()),"Different exact initial signed private frame");}
            require(localLimits.kernelRounds==limits.rounds&&localLimits.depth==limits.proofDepth&&localLimits.clauses==limits.clauses&&localLimits.alternatives==limits.alternatives,"Different local/kernel closure policy");
            ContextUniversalCompoundExchange.check(initialCompounds,x,initial,limits.proofDepth,work);
            require(initialCompounds.checks.keySet().equals(targets.keySet()),"Different supplied initial compound cut inventory");
            for(Map.Entry<Cut,Snapshot> target:targets.entrySet()){work.run();require(initialCompounds.checks.get(target.getKey()).input.foreign==target.getValue(),"Different actual initial compound receiver");}
            if(initialExchange!=null){ContextUniversalSignedUnitExchange.check(initialExchange,x,initial,limits.proofDepth,work);require(initialExchange.checks.equals(initialCompounds.checks)&&initialExchange.barrier==initialCompounds.barrier,"Initial unit/compound carriers must share the actual owning checks/barrier");require(initialExchange.checks.keySet().equals(targets.keySet()),"Different supplied initial unit cut inventory");for(Map.Entry<Cut,Snapshot> target:targets.entrySet()){work.run();require(initialExchange.checks.get(target.getKey()).input.foreign==target.getValue(),"Different actual initial unit receiver");}}
            ContextUniversalCompoundExchange.Exchange compounds=initialCompounds;ContextUniversalSignedUnitExchange.Exchange symbolic=initialExchange;Packet input=initial;List<Round> history=new ArrayList<>();
            for(int number=1;number<=maximumRounds;++number) {
                phase="checks";Map<Cut,ContextUniversalFrozenConstraints.Result> checks=new LinkedHashMap<>();Map<Cut,ContextUniversalGroupedLocalRounds.Certificate> certificates=new LinkedHashMap<>();
                for(Map.Entry<Cut,Snapshot> target:targets.entrySet()) {
                    work.run();ContextUniversalNativeRows.Result rows=ContextUniversalNativeRows.inspect(x,target.getValue(),64,limits.work);require(rows.classified,rows.reason);require(rows.withinShape,"Unsupported whole original signed row plan");
                    ContextUniversalFrozenConstraints.Result base=ContextUniversalFrozenConstraints.closeSignedCompounds(rows,input,pools.get(target.getKey()),symbolic,compounds,limits.rounds,limits.proofDepth,limits.clauses,limits.alternatives,limits.work);
                    require(base.closed,"Round "+number+" signed cut: "+base.reason);
                    ContextUniversalGroupedLocalRounds.Closure local=validationCapacity==0?ContextUniversalGroupedLocalRounds.run(base,localLimits):ContextUniversalGroupedLocalRounds.runWithValidationReuse(base,localLimits,validationCapacity);require(local.stable,"Round "+number+" local cut: "+local.reason);
                    certificates.put(target.getKey(),local.output);checks.put(target.getKey(),local.output.output);
                }
                // Complete and independently bind EVERY local certificate before ANY refutation/product.
                phase="certificate-binding";checkCertificates(x,targets,input,pools,symbolic,compounds,checks,certificates,localLimits,work);
                Map<Cut,Refutation> refutations=new LinkedHashMap<>();for(Map.Entry<Cut,ContextUniversalFrozenConstraints.Result> e:checks.entrySet()){work.run();if(e.getValue().xRefuted||e.getValue().foreignRefuted)refutations.put(e.getKey(),new Refutation(e.getKey(),number,e.getValue(),limits.work));}
                if(!refutations.isEmpty())return new Result(Status.REFUTED,"checks","All supplied cuts checked; sourced refutation at common barrier "+number,completed,initial,null,Collections.emptyList(),refutations);
                phase="transfers";Map<Cut,ConstraintTransfer> transfers=new LinkedHashMap<>();
                for(Map.Entry<Cut,ContextUniversalFrozenConstraints.Result> e:checks.entrySet()){work.run();ConstraintTransfer t=validationCapacity==0?ContextWitnessResultTransport.transferConstraintsCertified(certificates.get(e.getKey()),limits.factDepth,limits.work):ContextWitnessResultTransport.transferConstraintsCertifiedWithValidationReuse(certificates.get(e.getKey()),limits.factDepth,limits.work,validationCapacity).transfer;require(t.transferred,t.reason);transfers.put(e.getKey(),t);}
                phase="product-correspondence";Map<Cut,ContextUniversalSignedProductAudit.Result> products=new LinkedHashMap<>();
                for(Map.Entry<Cut,ConstraintTransfer> e:transfers.entrySet()){work.run();ContextUniversalSignedProductAudit.Result audit=validationCapacity==0?ContextUniversalSignedProductAudit.bind(checks.get(e.getKey()),e.getValue(),65536,limits.work):ContextUniversalSignedProductAudit.bindWithValidationReuse(checks.get(e.getKey()),e.getValue(),65536,limits.work,validationCapacity).result;require(audit.bound,audit.reason);products.put(e.getKey(),audit);}
                phase="symbolic-collection";ContextUniversalSignedUnitExchange.Collection collected=validationCapacity==0?ContextUniversalSignedUnitExchange.collect(x,input,cuts,checks,limits.clauses,limits.alternatives,limits.factDepth,limits.work):ContextUniversalSignedUnitExchange.collectWithValidationReuse(x,input,cuts,checks,limits.clauses,limits.alternatives,limits.factDepth,limits.work,validationCapacity).collection;require(collected.collected,collected.reason);
                phase="compound-collection";ContextUniversalCompoundExchange.Collection compoundCollection=validationCapacity==0?ContextUniversalCompoundExchange.collect(x,input,cuts,checks,limits.clauses,limits.alternatives,limits.factDepth,limits.work):ContextUniversalCompoundExchange.collectWithValidationReuse(x,input,cuts,checks,limits.clauses,limits.alternatives,limits.factDepth,limits.work,validationCapacity).collection;require(compoundCollection.collected,compoundCollection.reason);
                phase="barrier";ConstraintBarrier barrier=ContextWitnessResultTransport.mergeConstraints(ContextWitnessResultTransport.family(input,cuts,maximumWork),transfers,maximumWork);require(barrier.merged,barrier.reason);
                phase="symbolic-rebase";ContextUniversalSignedUnitExchange.Exchange nextSymbolic=validationCapacity==0?ContextUniversalSignedUnitExchange.rebase(collected.output,barrier,limits.work):ContextUniversalSignedUnitExchange.rebaseWithValidationReuse(collected.output,barrier,limits.work,validationCapacity).exchange;
                phase="compound-rebase";ContextUniversalCompoundExchange.Exchange nextCompounds=ContextUniversalCompoundExchange.rebase(compoundCollection.output,barrier,limits.work);
                phase="pool-comparison";boolean compoundChanged=!ContextUniversalCompoundExchange.sameContents(compounds,nextCompounds,maximumWork);boolean symbolicChanged=!ContextUniversalSignedUnitExchange.sameContents(symbolic,nextSymbolic,maximumWork);boolean changed=!ContextWitnessResultTransport.sameContents(input,barrier.packet,maximumWork),privateChanged=false;require(changed==barrier.changed&&pools.keySet().equals(barrier.privatePools.keySet()),"Different signed pool change/inventory receipt");
                for(Map.Entry<Cut,PrivatePool> e:pools.entrySet()){work.run();PrivatePool next=barrier.privatePools.get(e.getKey());require(next.common==barrier.packet&&next.x==x&&next.snapshot==e.getValue().snapshot,"Different rebased signed private frame");privateChanged|=!ContextWitnessResultTransport.sameContents(e.getValue().local,next.local,maximumWork);}
                history.add(new Round(number,input,pools,checks,products,barrier,changed,privateChanged,symbolic,nextSymbolic,symbolicChanged,compounds,nextCompounds,compoundChanged,certificates));completed=number;
                if(!changed&&!privateChanged&&!barrier.localChanged&&!symbolicChanged&&!compoundChanged)return new Result(Status.STABLE_GROUPED_POOLS,"stable-grouped-pools","Complete supplied fixed/private/unit/compound Source/grade pools unchanged after actual local stability; native/full-family qualification remains open",completed,initial,barrier,history,Collections.emptyMap());
                compounds=nextCompounds;symbolic=nextSymbolic;input=barrier.packet;pools=new LinkedHashMap<>(barrier.privatePools);
            }
            phase="round-limit";throw new IllegalStateException("Grouped round limit; no qualified stable output");
        }catch(Exception failure){return new Result(Status.INCOMPLETE,phase,failure.toString(),completed,initial,null,Collections.emptyList(),Collections.emptyMap());}
    }
    /** Independent actual certificate/frame/policy binding, including the complete supplied cut inventory. */
    static void checkCertificates(Snapshot x,Map<Cut,Snapshot> targets,Packet input,Map<Cut,PrivatePool> pools,ContextUniversalSignedUnitExchange.Exchange units,ContextUniversalCompoundExchange.Exchange compounds,Map<Cut,ContextUniversalFrozenConstraints.Result> checks,Map<Cut,ContextUniversalGroupedLocalRounds.Certificate> certificates,ContextUniversalGroupedLocalRounds.Limits policy,Runnable work) {
        require(x!=null&&targets!=null&&targets.containsKey(Cut.of(x))&&targets.get(Cut.of(x))==null&&input!=null&&pools!=null&&compounds!=null&&checks!=null&&certificates!=null&&policy!=null,"Actual complete supplied certificate frame required");
        require(targets.keySet().equals(checks.keySet())&&targets.keySet().equals(certificates.keySet())&&targets.keySet().equals(compounds.checks.keySet()),"Incomplete all-cut local certificate inventory");
        for(Map.Entry<Cut,Snapshot> target:targets.entrySet()) {
            work.run();ContextUniversalGroupedLocalRounds.Certificate c=certificates.get(target.getKey());ContextUniversalFrozenConstraints.Result checked=checks.get(target.getKey());
            require(c!=null&&checked!=null&&c.output==checked&&c.initial!=null&&c.initial.input.x==x&&c.initial.input.foreign==target.getValue()&&c.initial.input.target.equals(target.getKey()),"Different actual certificate/cut output");
            require(c.initial.common==input&&c.initial.pool==pools.get(target.getKey())&&c.initial.exchange==units&&c.initial.compounds==compounds,"Different immutable local input barrier");
            require(c.limits==policy,"Different actual local enumeration policy");ContextUniversalGroupedLocalRounds.check(c,work);
        }
    }

}
