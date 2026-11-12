/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.NativeDependentMatchFrame.*;
import org.kanger.NativeDependentReplayCapsule.*;
import org.kanger.units.Rule;

/** Read-only local-X replay diagnostic. Owning provider guards remain mandatory; no admission verdict. */
public final class ContextDependentReplayInspection {
    private ContextDependentReplayInspection(){}
    public static final class Policy {
        public final int bytes,rows,steps,work,nodes;
        public Policy(int bytes,int rows,int steps,int work,int nodes) {
            new Limits(bytes,rows,steps,work,nodes);
            this.bytes=bytes;this.rows=rows;this.steps=steps;this.work=work;this.nodes=nodes;
        }
        private Limits limits(){return new Limits(bytes,rows,steps,work,nodes);}
    }
    public static final class Request {
        public final Policy policy;
        public final long receiver;
        public final int branch;
        public final List<Long> producers;
        public final List<Integer> pivots;
        public Request(Policy policy,long receiver,int branch,List<Long> producers,List<Integer> pivots) {
            if(policy==null||receiver<0||branch<0||producers==null||pivots==null)
                throw new IllegalArgumentException("Exact bounded local-X replay recipe required");
            List<Long> producerCopy=new ArrayList<>(producers);List<Integer> pivotCopy=new ArrayList<>(pivots);
            if(producerCopy.isEmpty()||producerCopy.size()!=pivotCopy.size()||producerCopy.size()>policy.steps)
                throw new IllegalArgumentException("Complete bounded local-X replay recipe required");
            for(Long id:producerCopy)if(id==null||id<0)throw new IllegalArgumentException("Whole producer occurrence required");
            for(Integer pivot:pivotCopy)if(pivot==null||pivot<0)throw new IllegalArgumentException("Exact receiver pivot required");
            this.policy=policy;this.receiver=receiver;this.branch=branch;
            this.producers=Collections.unmodifiableList(producerCopy);this.pivots=Collections.unmodifiableList(pivotCopy);
        }
    }
    /** Detached historical evidence, never a full-family or write-eligibility receipt. */
    public static final class Historical {
        public final boolean closedBranch;
        public final int depth,remaining;
        public final Set<Source> originals,proofSources;
        private Historical(Materialization m,Map<Source,Source> sources) {
            closedBranch=m.remaining.isEmpty();depth=m.support.depth;remaining=m.remaining.size();
            originals=Collections.unmodifiableSet(new LinkedHashSet<>(sources.keySet()));
            Map<Source,Source> inverse=new HashMap<>();for(Map.Entry<Source,Source> entry:sources.entrySet())
                if(inverse.put(entry.getValue(),entry.getKey())!=null)throw new IllegalStateException("Non-bijective replay Sources");
            Set<Source> proof=new LinkedHashSet<>();for(Source fresh:m.support.sources){Source saved=inverse.get(fresh);if(saved==null)throw new IllegalStateException("Unmapped replay proof Source");proof.add(saved);}
            proofSources=Collections.unmodifiableSet(proof);
        }
    }
    public static final class Result {
        public final Historical evidence;
        public final String pin;
        private final byte[] packet;
        private Result(Saved saved,Historical evidence){this.evidence=evidence;pin=saved.pin.hex();packet=saved.bytes();}
        public byte[] replayPacket(){return packet.clone();}
    }
    /** Historical verification cannot establish live provider/source currentness. */
    public static Historical replay(byte[] packet,String pin,Policy policy)throws Exception {
        if(policy==null)throw new IllegalArgumentException("Bounded replay policy required");
        try(Reopened result=NativeDependentReplayCapsule.reopen(packet,Pin.fromHex(pin),policy.limits())) {
            result.verify();return new Historical(result.materialization,result.sources);
        }
    }
    static void current(Mind mind,ContextProductionInventory expected,Policy policy)throws Exception {
        if(expected==null||!expected.localX)throw new IllegalArgumentException("Actual local-X inventory required");currentMember(mind,expected,policy);
    }
    static void currentMember(Mind mind,ContextProductionInventory expected,Policy policy)throws Exception {
        if(expected==null)throw new IllegalArgumentException("Actual physical member inventory required");
        if(expected.groundCacheProof()!=null&&!DmzTerminalRestriction.activeNoGoods(mind).isEmpty())throw new IllegalStateException("Restricted branch cannot reuse local ground-cache implication coverage");
        if(expected.symbolicDiagnosticProof()!=null)expected.symbolicDiagnosticProof().requireCurrent(mind,expected);
        ContextProductionInventory now=ContextProductionInventory.inspect(mind,expected.context,expected.revision,expected.localX,expected.initialization,policy.rows);
        if(!expected.fingerprint.equals(now.fingerprint))throw new IllegalStateException("Changed local-X replay diagnostic inventory");
    }
    static final class Projection {
        final Mind mind;final Map<Long,Long> mapping;
        Projection(Mind mind,Map<Long,Long> mapping){this.mind=mind;this.mapping=Collections.unmodifiableMap(new LinkedHashMap<>(mapping));}
    }
    static Projection project(Mind actual,ContextProductionInventory expected,Policy policy,boolean horn)throws Exception {
        return project(actual,expected,policy,horn,ContextGroundProductionRounds.budget(policy.work));
    }
    static Projection projectMember(Mind actual,ContextProductionInventory expected,Policy policy)throws Exception {
        return projectMember(actual,expected,policy,()->{});
    }
    static Projection projectMember(Mind actual,ContextProductionInventory expected,Policy policy,Runnable chargeWork)throws Exception {
        return projectMember(actual,expected,policy,chargeWork,null,null,null,null,Long.MAX_VALUE,null,null,Long.MAX_VALUE,()->{});
    }
    static Projection projectMember(Mind actual,ContextProductionInventory expected,Policy policy,
                                    Runnable chargeWork,Runnable chargeNewTerm,Runnable chargeCompilerEntry,Runnable chargeParserNode,Runnable chargeParserToken,long parserDepth,java.util.function.IntConsumer chargeLexemeText,java.util.function.LongConsumer chargeCompilerText,long compilerDepth,Runnable chargeExpansion)throws Exception {
        Runnable localWork = ContextGroundProductionRounds.budget(policy.work);
        return project(actual,expected,policy,true,()->{localWork.run();chargeWork.run();},false,chargeNewTerm,chargeCompilerEntry,chargeParserNode,chargeParserToken,parserDepth,chargeLexemeText,chargeCompilerText,compilerDepth,chargeExpansion);
    }
    private static Projection project(Mind actual,ContextProductionInventory expected,Policy policy,boolean horn,Runnable work)throws Exception {
        return project(actual,expected,policy,horn,work,true,null,null,null,null,Long.MAX_VALUE,null,null,Long.MAX_VALUE,()->{});
    }
    private static Projection project(Mind actual,ContextProductionInventory expected,Policy policy,boolean horn,Runnable work,boolean local,Runnable chargeNewTerm,Runnable chargeCompilerEntry,Runnable chargeParserNode,Runnable chargeParserToken,long parserDepth,java.util.function.IntConsumer chargeLexemeText,java.util.function.LongConsumer chargeCompilerText,long compilerDepth,Runnable chargeExpansion)throws Exception {
        if(local)current(actual,expected,policy);else currentMember(actual,expected,policy);
        if(!expected.dependentPrimaryView())throw new IllegalStateException("Whole primary-only local-X diagnostic required");
        User user=new User();Mind isolated=new Mind(user,chargeNewTerm,chargeCompilerEntry,chargeParserNode,chargeParserToken,parserDepth,chargeLexemeText,chargeCompilerText,compilerDepth,chargeExpansion);user.setCurrentMind(isolated);Map<Long,Long> mapping=new LinkedHashMap<>();
        try {
            for(ContextProductionInventory.Row row:expected.primary) {
                work.run();Rule original=(Rule)actual.getRules().get(row.nativeRule),copy=(Rule)isolated.compileLineForReplay(row.origin);
                if(original==null||copy==null)throw new IllegalStateException("Missing whole native original or replay");
                if(horn&&!NativeDependentProductionRows.unit(original)) {
                    ContextProductionCapture.Clause left=NativeDependentProductionRows.ordinary(original,actual,work),right=NativeDependentProductionRows.ordinary(copy,isolated,work);
                    if(!left.equivalent(right,work))throw new IllegalStateException("Different whole native/replayed Horn or constraint row");
                }else {
                    List<ContextProductionCapture.Clause> left=NativeDependentMatchFrame.headlessShape(original,actual,work),right=NativeDependentMatchFrame.headlessShape(copy,isolated,work);
                    if(left==null||right==null) {
                        if(left!=null||right!=null||!NativePrenexUnitScope.same(NativePrenexUnitScope.observeFactoryRow(row.nativeRule,actual,work),NativePrenexUnitScope.observe(copy,isolated,work),work))throw new IllegalStateException("Different native/replayed whole producer");
                    }else {
                        if(left.size()!=right.size())throw new IllegalStateException("Different whole receiver branch count");
                        for(int i=0;i<left.size();i++)if(!left.get(i).equivalent(right.get(i),work))throw new IllegalStateException("Different native/replayed receiver");
                    }
                }
                mapping.put(row.nativeRule,copy.getId());
            }
            return new Projection(isolated,mapping);
        }finally{if(local)current(actual,expected,policy);else currentMember(actual,expected,policy);}
    }
    public static Result inspect(Mind actual,ContextProductionInventory expected,Request request)throws Exception {
        if(actual==null||request==null)throw new IllegalArgumentException("Actual local-X and exact replay request required");
        Policy policy=request.policy;current(actual,expected,policy);
        if(expected.rows.size()!=expected.primary.size())throw new IllegalStateException("Portable local replay still requires a whole primary-only inventory");
        Runnable work=ContextGroundProductionRounds.budget(policy.work);
        Projection projection=project(actual,expected,policy,false,work);Mind isolated=projection.mind;Map<Long,Long> mapping=projection.mapping;
        try {
            try(NativeDependentMatchFrame frame=NativeDependentMatchFrame.open(isolated,expected.context,expected.revision,true,expected.initialization,policy.rows,policy.steps,policy.work,policy.nodes)) {
                Long receiverId=mapping.get(request.receiver);if(receiverId==null)throw new IllegalStateException("Missing actual receiver");
                Instance instance=frame.instantiate(frame.entry((Rule)isolated.getRules().get(receiverId)),request.branch);Match last=null;
                for(int i=0;i<request.producers.size();i++) {
                    work.run();Long id=mapping.get(request.producers.get(i));if(id==null)throw new IllegalStateException("Missing actual producer");
                    last=frame.match(frame.entry((Rule)isolated.getRules().get(id)),instance,request.pivots.get(i));
                    if(last==null)throw new IllegalStateException("Supplied local-X replay step does not unify");instance=last.after;
                }
                Saved saved=NativeDependentReplayCapsule.captureProjected(frame,last,policy.limits(),expected,mapping);
                Historical checked=replay(saved.bytes(),saved.pin.hex(),policy);return new Result(saved,checked);
            }
        } finally {current(actual,expected,policy);}
    }
}
