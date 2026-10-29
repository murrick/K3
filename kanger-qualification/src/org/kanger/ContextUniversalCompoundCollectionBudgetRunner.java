/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalFrozenConstraints.Result;
import org.kanger.ContextUniversalCompoundExchange.*;
import org.kanger.ContextWitnessResultTransport.*;

/** Same complete next-barrier donor map: original collection refusal versus explicit owned session. */
public final class ContextUniversalCompoundCollectionBudgetRunner {
    static int checks;static final int WORK=64000000;
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    public static void main(String[] args){try {
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("compound-collection-budget-").toString());Snapshot x=ContextUniversalGroupedRoundsRunner.bind(true,"!anchor(1); !@u p(u);"),a=ContextUniversalGroupedRoundsRunner.bind(false,"!@u (p(u)&&b(u)->r(u))&&(p(u)&&r(u)->s(u));"),b=ContextUniversalGroupedRoundsRunner.bind(false,"!b(1);");Packet p=ContextPrivateWitnessPoolRunner.common(x);List<Snapshot> supplied=Arrays.asList(a,b);ContextUniversalCompoundExchange.Exchange input=ContextUniversalGroupedRoundsRunner.exchange(x,supplied,p,Collections.emptyMap());ContextUniversalGroupedRounds.SessionRun run=ContextUniversalGroupedRounds.runWithValidationReuse(x,supplied,p,Collections.emptyMap(),ContextUniversalGroupedRoundsRunner.units(input),input,ContextUniversalGroupedRoundsRunner.limits(16),ContextUniversalGroupedRoundsRunner.local(),8,WORK,4096);ContextUniversalGroupedRounds.Result stable=run.result;require(stable.status==ContextUniversalGroupedRounds.Status.STABLE_GROUPED_POOLS&&stable.completedRounds==3,stable.phase+": "+stable.reason);
        Map<Cut,Result> donors=new LinkedHashMap<>();List<Snapshot> receivers=new ArrayList<>();receivers.add(null);receivers.addAll(supplied);
        for(Snapshot receiver:receivers){Result base=ContextUniversalFrozenConstraints.closeSignedCompounds(ContextUniversalCompoundExchangeRunner.rows(x,receiver),stable.output.packet,null,stable.symbolicOutput,stable.compoundOutput,16,16,512,256,WORK);require(base.closed,base.reason);ContextUniversalGroupedLocalRounds.Closure local=ContextUniversalGroupedLocalRounds.runWithValidationReuse(base,ContextUniversalGroupedRoundsRunner.local(),4096);require(local.stable,local.reason);donors.put(receiver==null?Cut.of(x):Cut.of(receiver),local.output.output);}
        List<Cut> cuts=new ArrayList<>(donors.keySet());ContextUniversalCompoundExchange.Collection old=ContextUniversalCompoundExchange.collect(x,stable.output.packet,cuts,donors,512,256,16,WORK);require(!old.collected&&old.output==null&&old.reason.contains("Ground transport work limit"),"original same complete donor collection refusal: "+old.reason);
        SessionCollection fresh=ContextUniversalCompoundExchange.collectWithValidationReuse(x,stable.output.packet,cuts,donors,512,256,16,WORK,4096);require(fresh.collection.collected&&fresh.metrics.work<WORK&&fresh.metrics.hits>0&&fresh.metrics.entries==0,fresh.collection.reason);Exchange e=fresh.collection.output;require(e.x==x&&e.common==stable.output.packet&&e.checks.equals(donors)&&e.prior==null&&e.barrier==null,"actual complete next donor map/common frame");require(ContextUniversalCompoundExchange.sameContents(e,stable.compoundOutput,WORK),"exact whole-Source compound minima retained on same stable continuation");for(ResidualProof u:e.clauses)require(donors.containsValue(u.owner)&&u.owner.proofs.contains(u.proof)&&u.clause==u.proof.clause&&u.support==u.proof.support,"actual compound owner/proof/clause/Support references");
        require(e.maximumClauses==512&&e.maximumAlternatives==256&&e.maximumDepth==16,"actual sealed enumeration policy retained at unchanged bounds");
        System.out.println("COMPOUND_COLLECTION_RETAINED legacyRefused=true sessionWork="+fresh.metrics.work+" hits="+fresh.metrics.hits+" verified="+fresh.metrics.verified+" peak="+fresh.metrics.peakEntries+" clauses="+e.clauses.size());System.out.println("CONTEXT_UNIVERSAL_COMPOUND_COLLECTION_BUDGET_PASS checks="+checks);System.exit(0);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
