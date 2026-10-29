/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionNativeBinding;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalGroupedRounds.*;
import org.kanger.ContextWitnessResultTransport.*;
import static org.kanger.ContextUniversalContinuationMatrix.*;

/** Same actual rows under supplied-cut and trusted-member order, plus bounded repeated replay. */
public final class ContextUniversalContinuationPermutationRunner {
    static void cases()throws Exception {
        Snapshot x=bind(true,"!anchor(1); !@u p(u);"),a=bind(false,"!@u p(u)&&b(u)->r(u);"),b=bind(false,"!b(1);");Packet p=ContextPrivateWitnessPoolRunner.common(x);SessionRun forward=null;
        for(List<Snapshot> order:Arrays.asList(Arrays.asList(a,b),Arrays.asList(b,a))){Frame f=new Frame(x,order,p,Collections.emptyMap());SessionRun first=run(f,8,WORK,4096);audited(f,p,f.pools,f.units,f.compounds,8,first);ContextUniversalGroupedRounds.Result old=ContextUniversalGroupedRounds.run(x,order,p,f.pools,f.units,f.compounds,f.limits,f.local,8,WORK);ContextUniversalGroupedSessionRunner.compare(old,first.result);++checks;SessionRun finalState=stable(f,2);require(ContextUniversalGroupedRoundsRunner.has(finalState.result.output.packet,"r")&&!ContextUniversalGroupedRoundsRunner.has(finalState.result.output.packet,"b"),"only X-dependent product shared in either enumeration");require(finalState.result.output.packet.items.stream().filter(i->i.atom.predicate.equals("r")).flatMap(i->i.proofs.stream()).allMatch(s->s.depth==1&&s.sources.size()==3),"actual r1 with all three physical Sources");if(forward!=null)same(forward.result,finalState.result);forward=finalState;}
        SessionRun trustedForward=null;for(List<Snapshot> members:Arrays.asList(Arrays.asList(a,b),Arrays.asList(b,a))){Snapshot group=ContextProductionNativeBinding.trusted("matrix",members,WORK);Frame f=new Frame(x,Collections.singletonList(group),p,Collections.emptyMap());SessionRun r=stable(f,2);require(r.result.output.branches.size()==2&&r.result.output.branches.containsKey(Cut.of(group)),"one actual trusted shared cut plus self-X");require(r.result.output.packet.items.stream().filter(i->i.atom.predicate.equals("r")).flatMap(i->i.proofs.stream()).allMatch(s->s.depth==1&&s.sources.size()==3),"trusted membership retains original member Source identities/minimum");if(trustedForward!=null)same(trustedForward.result,r.result);trustedForward=r;}
        Frame f=new Frame(x,Arrays.asList(a,b),p,Collections.emptyMap());SessionRun work=run(f,8,1,4096);incomplete(work,"owning work refusal");noStableAudit(f,work);SessionRun rounds=run(f,1,WORK,4096);incomplete(rounds,"progress cannot publish incomplete round-limit prefix");require(rounds.result.completedRounds==1&&rounds.result.phase.equals("round-limit"),"actual completed first progress barrier is still unqualified");noStableAudit(f,rounds);SessionRun cap1=run(f,8,WORK,1);audited(f,p,f.pools,f.units,f.compounds,8,cap1);require(cap1.metrics.peakEntries==1&&cap1.metrics.capacityBypass>0,"capacity1 performs full fallback validation");same(forward.result,cap1.result);
        System.out.println("CONTINUATION_PERMUTATIONS independentOrders=2 trustedMemberOrders=2 continuationsPerCase=2 legacyComparisons=2");
    }
    public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("continuation-permutations-").toString());cases();System.out.println("MATRIX_SCOPES audits="+audits+" receiptRechecks="+receipts+" continuations="+continuations);System.out.println("CONTEXT_UNIVERSAL_CONTINUATION_PERMUTATION_PASS checks="+checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
