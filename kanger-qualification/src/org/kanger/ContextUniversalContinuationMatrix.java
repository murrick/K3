/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalGroupedRounds.*;
import org.kanger.ContextUniversalGroupedRounds.Result;
import org.kanger.ContextWitnessResultTransport.*;

/** Qualification-only actual four-pool frames; no production configuration switch. */
final class ContextUniversalContinuationMatrix {
    static int checks,audits,receipts,continuations;static final int WORK=64000000;
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static final class Frame {
        final Snapshot x;final List<Snapshot> cuts;final Packet input;final Map<Cut,PrivatePool> pools;
        final ContextUniversalSignedUnitExchange.Exchange units;final ContextUniversalCompoundExchange.Exchange compounds;
        final ContextUniversalFrozenBranch.Limits limits=ContextUniversalGroupedRoundsRunner.limits(16);
        final ContextUniversalGroupedLocalRounds.Limits local=ContextUniversalGroupedRoundsRunner.local();
        Frame(Snapshot x,List<Snapshot> cuts,Packet input,Map<Cut,PrivatePool> pools){this(x,cuts,input,pools,null,null);}
        Frame(Snapshot x,List<Snapshot> cuts,Packet input,Map<Cut,PrivatePool> pools,ContextUniversalSignedUnitExchange.Exchange units,ContextUniversalCompoundExchange.Exchange compounds){this.x=x;this.cuts=new ArrayList<>(cuts);this.input=input;this.pools=new LinkedHashMap<>(pools);this.compounds=compounds==null?ContextUniversalGroupedRoundsRunner.exchange(x,cuts,input,pools):compounds;this.units=units==null?ContextUniversalGroupedRoundsRunner.units(this.compounds):units;}
    }
    static SessionRun run(Frame f,int rounds,int work,int cap){return ContextUniversalGroupedRounds.runWithValidationReuse(f.x,f.cuts,f.input,f.pools,f.units,f.compounds,f.limits,f.local,rounds,work,cap);}
    static void incomplete(SessionRun s,String why){Result r=s.result;require(r.status==Status.INCOMPLETE&&r.output==null&&r.symbolicOutput==null&&r.compoundOutput==null&&r.history.isEmpty()&&r.refutations.isEmpty()&&s.metrics.entries==0,why+": "+r.phase+": "+r.reason);}
    static void audited(Frame f,Packet input,Map<Cut,PrivatePool> pools,ContextUniversalSignedUnitExchange.Exchange units,ContextUniversalCompoundExchange.Exchange compounds,int rounds,SessionRun s){Result r=s.result;require(r.status==Status.STABLE_GROUPED_POOLS&&r.output!=null&&r.symbolicOutput!=null&&r.compoundOutput!=null&&!r.history.isEmpty()&&r.refutations.isEmpty()&&s.metrics.entries==0&&s.metrics.verified>0,r.phase+": "+r.reason);ContextUniversalGroupedRoundsRunner.stable(r,compounds);++checks;
        ContextUniversalGroupedChainAudit.SessionInspection audit=ContextUniversalGroupedChainAudit.inspectWithValidationReuse(f.x,f.cuts,input,pools,units,compounds,f.limits,f.local,rounds,r,WORK,4096);require(audit.inspection.audited&&audit.inspection.output!=null&&audit.metrics.entries==0&&audit.metrics.verified>0,audit.inspection.phase+": "+audit.inspection.reason);require(ContextUniversalGroupedChainAudit.checkWithValidationReuse(audit.inspection.output,WORK,4096).verified>0,"fresh receipt recheck of actual matrix trace");++audits;++receipts;
        for(Round row:r.history){require(row.checks.keySet().equals(row.certificates.keySet())&&row.checks.keySet().equals(row.products.keySet())&&row.checks.keySet().equals(row.barrier.branches.keySet()),"complete all-cut same-barrier inventory");for(Cut cut:row.checks.keySet()){ConstraintTransfer t=row.barrier.branches.get(cut);require(t.certificate==row.certificates.get(cut)&&t.bindings!=null&&t.bindings.coverage==row.checks.get(cut)&&t.proof==row.checks.get(cut)&&row.products.get(cut).transfer==t&&row.products.get(cut).coverage==t.proof&&row.products.get(cut).bound,"actual certificate/parameter/product ownership per cut");}}
    }
    static SessionRun stable(Frame f,int continuations){SessionRun s=run(f,8,WORK,4096);audited(f,f.input,f.pools,f.units,f.compounds,8,s);Result initial=s.result;for(int i=0;i<continuations;++i){Result before=s.result;SessionRun next=ContextUniversalGroupedRounds.runWithValidationReuse(f.x,f.cuts,before.output.packet,before.output.privatePools,before.symbolicOutput,before.compoundOutput,f.limits,f.local,1,WORK,4096);audited(f,before.output.packet,before.output.privatePools,before.symbolicOutput,before.compoundOutput,1,next);require(next.result.completedRounds==1,"one unchanged continued round at original bounds");++ContextUniversalContinuationMatrix.continuations;same(before,next.result);s=next;}same(initial,s.result);return s;}
    static void sameShared(Result a,Result b){require(ContextWitnessResultTransport.sameContents(a.output.packet,b.output.packet,WORK)&&ContextUniversalSignedUnitExchange.sameContents(a.symbolicOutput,b.symbolicOutput,WORK)&&ContextUniversalCompoundExchange.sameContents(a.compoundOutput,b.compoundOutput,WORK),"complete fixed/unit/compound exact Source-depth pools equal");}
    static void same(Result a,Result b){sameShared(a,b);require(a.output.privatePools.keySet().equals(b.output.privatePools.keySet()),"same complete private cut inventory");for(Cut cut:a.output.privatePools.keySet()){PrivatePool p=a.output.privatePools.get(cut),q=b.output.privatePools.get(cut);require(p.sources.equals(q.sources)&&p.declarations.size()==q.declarations.size()&&ContextWitnessResultTransport.sameContents(p.local,q.local,WORK),"complete private Source-depth pool and original manifest preserved");for(int i=0;i<p.declarations.size();++i)require(p.declarations.get(i).source.equals(q.declarations.get(i).source)&&p.declarations.get(i).statement.equals(q.declarations.get(i).statement),"original private declaration contents retained");}}
    static void noStableAudit(Frame f,SessionRun s){ContextUniversalGroupedChainAudit.SessionInspection a=ContextUniversalGroupedChainAudit.inspectWithValidationReuse(f.x,f.cuts,f.input,f.pools,f.units,f.compounds,f.limits,f.local,8,s.result,WORK,4096);require(!a.inspection.audited&&a.inspection.output==null&&a.metrics.entries==0,"refused/refuted prefix cannot obtain stable receipt");}
    static Snapshot bind(boolean x,String text)throws Exception{return ContextUniversalGroupedRoundsRunner.bind(x,text);}
}
