/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.nio.file.Files;
import java.util.*;
import org.kanger.interfaces.*;

/** Actual next conflicts are bound to the exact owned prefix that discovered them. */
public final class DmzSuccessiveCacheConflictsRunner {
    private static int checks;
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",Files.createTempDirectory("dmz-successive-").toString());
            for(boolean negative:new boolean[]{false,true})for(boolean recursive:new boolean[]{false,true})for(int aliases:new int[]{1,2})run(negative,recursive,aliases);
            System.out.println("DMZ_SUCCESSIVE_CACHE_CONFLICTS_PASS checks="+checks);System.exit(0);
        }catch(Throwable failure){failure.printStackTrace();System.exit(1);}
    }
    private static IContextResults.Revision pin(){return new IContextResults.Revision(UUID.randomUUID(),1);}
    private static void raw(Mind q,IContextResults.Revision source,long id,DmzReplayProvenance.Authority authority,String text) throws Exception {
        DmzReplayProvenance.replayRule(q,source,id,authority,text);++checks;
    }
    private static boolean present(DmzTypedNativeResume.Result result,String text) throws Exception {
        TerminalSupportCapture.Ground expected=DmzGroundReplay.parseIdentity(text).ground();
        for(DmzTypedNativeResume.Materialization item:result.materializations)if(expected.equivalent(item.ground))return true;
        return false;
    }
    private static DmzProofWitnesses.Witness candidate(DmzCacheCollisionEvidence evidence,List<DmzProofWitnesses.NoGood> removed) {
        for(DmzWitnessConflicts.Combination pair:evidence.combinations)for(DmzProofWitnesses.Witness side:Arrays.asList(pair.left,pair.right))if(side.step>=0) {
            boolean cut=false;for(DmzProofWitnesses.NoGood pattern:removed)cut |= pattern.matches(side.graph,side.node,side.step,side.source,side.premises);
            if(!cut)return side;
        }
        throw new AssertionError("Expected a distinct remaining native endpoint choice");
    }
    private static void run(boolean negative,boolean recursive,int aliases) throws Exception {
        User user=new User();Mind q=new Mind(user);user.setCurrentMind(q);IContextResults.Revision rules=pin(),facts=pin();
        String sign=negative?"~":"",opposite=negative?"":"~",premise=recursive?"middle":"a";
        DmzCacheRestrictionPath detached;
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(q);TerminalSupportCapture capture=TerminalSupportCapture.begin(q)) {
            if(recursive)raw(q,rules,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y a(x,y) -> middle(x,y);");
            String production="!@x @y "+premise+"(x,y) -> "+sign+"b(x,y);";
            raw(q,rules,1,DmzReplayProvenance.Authority.EXTERNAL,production);
            if(aliases==2)raw(q,pin(),0,DmzReplayProvenance.Authority.EXTERNAL,production);
            raw(q,rules,2,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y "+sign+"b(x,y) -> "+sign+"tail(x,y);");
            String[] tuples={"John,Tom","Mary,Sarah","Susan,Bill"};
            for(int i=0;i<tuples.length;++i)require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q,facts,i,DmzReplayProvenance.Authority.TARGET_Q,"!a("+tuples[i]+");")),"native source materializes root and tail");
            for(int i=0;i<2;++i)raw(q,facts,10+i,DmzReplayProvenance.Authority.TARGET_Q,"!"+opposite+"b("+tuples[i]+");");
            String state=DmzObservationStateFingerprint.capture(q);DmzReplayProvenance.SourceCheckpoint sources=DmzReplayProvenance.sourceCheckpoint(q);
            DmzCacheCollisionEvidence.Baseline baseline=DmzCacheCollisionEvidence.beforeOperation(capture,q);CollisionProofCapture.Conflict event;
            try(CollisionProofCapture conflicts=CollisionProofCapture.begin()) {
                require(!q.compile("!anchor();",null,false),"first actual native cache conflict");event=conflicts.snapshot().get(0);
            }
            DmzCacheCollisionEvidence initial=DmzCacheCollisionEvidence.bind(baseline,event,q,10000);
            require(initial.eligible,"initial historical endpoint evidence");
            DmzCacheRestrictionPath path=DmzCacheRestrictionPath.start(initial,candidate(initial,Collections.<DmzProofWitnesses.NoGood>emptyList()),q,10000);
            List<DmzCacheRestrictionPath> prefixes=new ArrayList<DmzCacheRestrictionPath>();
            DmzTypedNativeResume.Result earlier=null;
            for(int n=1;n<=2*aliases;++n) {
                require(path.selections.size()==n && path.isCurrent(q),"current immutable path prefix");prefixes.add(path);
                DmzTypedCacheProjection plan=DmzTypedCacheProjection.projectPath(path,q,10000);
                require(plan.eligible && plan.isCurrent(q),"per-root accumulated cache plan");
                DmzTypedNativeResume.Result result=DmzTypedNativeResume.runPath(path,q,10000);
                boolean last=n==2*aliases;
                require(result.compiled==last && !result.complete && !result.provisional,"actual continuation resolves all and only accumulated roots: "+negative+"/"+recursive+"/"+aliases+"/"+n);
                int exhausted=0;int[] removed=new int[2];
                for(DmzProofWitnesses.Witness selected:path.selections)for(int i=0;i<2;++i)
                    if(DmzGroundReplay.parseIdentity("!"+sign+"b("+tuples[i]+");").ground().equivalent(selected.graph.observed.nodes.get(selected.node).ground))++removed[i];
                for(int count:removed)if(count==aliases)++exhausted;
                require(result.retractedCaches.size()==2*exhausted,"root and tail retracted per fully excluded signed ground: "+negative+"/"+recursive+"/"+aliases+"/"+n+" actual="+result.retractedCaches.size()+" expected="+(2*exhausted));
                require(present(result,"!"+sign+"b(Susan,Bill);") && present(result,"!"+sign+"tail(Susan,Bill);"),"unselected substitution retains both native caches");
                for(int i=0;i<2;++i) {
                    require(present(result,"!"+sign+"b("+tuples[i]+");")== (removed[i]<aliases)
                            && present(result,"!"+sign+"tail("+tuples[i]+");")== (removed[i]<aliases),"each actual cache follows remaining support at its own signed root");
                    require(present(result,"!a("+tuples[i]+");") && present(result,"!"+opposite+"b("+tuples[i]+");"),"Q donors and opposite primary endpoints remain live");
                    if(recursive)require(present(result,"!middle("+tuples[i]+");"),"upstream cache remains live");
                }
                require(state.equals(DmzObservationStateFingerprint.capture(q)) && sources.isCurrent(q) && path.isCurrent(q)
                        && !q.hasPendingTransactions() && DmzTerminalRestriction.activeNoGoods(q).isEmpty() && user.getCurrentMind()==q,"each continuation restores source, authority and ownership boundaries");
                for(DmzCacheRestrictionPath prefix:prefixes)require(prefix.selections.size()<=n && prefix.isCurrent(q),"earlier immutable prefixes remain current");
                if(last) {
                    require(result.applicationDenied>0 && result.queryDenied>0,"both native gates block resurrection across signed roots");
                    try {DmzCacheCollisionEvidence.successor(result,q,10000);throw new AssertionError("Expected successful result refusal");}
                    catch(IllegalStateException expected){++checks;}
                } else {
                    DmzCacheCollisionEvidence next=DmzCacheCollisionEvidence.successor(result,q,10000);
                    require(next.eligible && next.isCurrent(q),"next actual conflict gets current historical endpoint evidence");
                    DmzProofWitnesses.Witness choice=candidate(next,path.patterns);
                    try {DmzCacheRestrictionPath.start(next,choice,q,10000);throw new AssertionError("Expected prefix reset refusal");}catch(IllegalStateException expected){++checks;}
                    try {DmzCacheRestrictionSet.begin(next,q);throw new AssertionError("Expected single-root reset refusal");}catch(IllegalStateException expected){++checks;}
                    require(!DmzTypedCacheProjection.projectCache(next,choice,q,10000).eligible,"standalone cache plan cannot drop inherited choices");
                    try {DmzTypedNativeResume.runCache(next,choice,q,10000);throw new AssertionError("Expected standalone successor refusal");}catch(IllegalStateException expected){++checks;}
                    try {path.append(next,choice,q,1);throw new AssertionError("Expected successor budget refusal");}catch(IllegalStateException expected){++checks;}
                    for(DmzWitnessConflicts.Combination pair:next.combinations)for(DmzProofWitnesses.Witness side:Arrays.asList(pair.left,pair.right))if(side.step>=0) {
                        boolean cut=false;for(DmzProofWitnesses.NoGood pattern:path.patterns)cut |= pattern.matches(side.graph,side.node,side.step,side.source,side.premises);
                        if(cut)try {path.append(next,side,q,10000);throw new AssertionError("Expected previously removed successor refusal");}catch(IllegalStateException expected){++checks;}
                    }
                    DmzCacheRestrictionPath old=path;path=path.append(next,choice,q,10000);
                    require(old.selections.size()==n && path.selections.size()==n+1,"successor append leaves parent prefix unchanged");
                    try {old.append(initial,initial.combinations.get(0).left,q,10000);throw new AssertionError("Expected unrelated evidence refusal");}
                    catch(IllegalStateException expected){++checks;}
                    if(earlier!=null) {
                        DmzCacheCollisionEvidence wrong=DmzCacheCollisionEvidence.successor(earlier,q,10000);
                        try {old.append(wrong,candidate(wrong,Collections.<DmzProofWitnesses.NoGood>emptyList()),q,10000);throw new AssertionError("Expected wrong prefix refusal");}
                        catch(IllegalStateException expected){++checks;}
                    }
                    earlier=result;
                }
            }
            detached=path;
            try {path.selections.clear();throw new AssertionError("Expected immutable path");}catch(UnsupportedOperationException expected){++checks;}
            require(!DmzTypedCacheProjection.projectPath(path,q,1).eligible,"short path plan refuses");
            try(TechnicalMindTransaction occupied=TechnicalMindTransaction.beginIsolated(q)) {
                try {DmzTypedNativeResume.runPath(path,q,10000);throw new AssertionError("Expected occupied boundary refusal");}
                catch(IllegalStateException expected){require(expected.getMessage().contains("Exclusive"),"path requires exclusive native child");}
            }
            raw(q,pin(),0,DmzReplayProvenance.Authority.EXTERNAL,"!a(John,Tom);");
            require(!path.isCurrent(q),"source changes invalidate all linked conflict receipts");
            try {DmzTypedNativeResume.runPath(path,q,10000);throw new AssertionError("Expected stale path refusal");}catch(IllegalStateException expected){++checks;}
            require(!q.hasPendingTransactions() && DmzTerminalRestriction.activeNoGoods(q).isEmpty(),"stale path acquires no scope");
        }
        require(!detached.isCurrent(q) && detached.selections.size()==2*aliases,"closed owner invalidates path while detached selections survive");
    }
    private static void require(boolean value,String message){++checks;if(!value)throw new AssertionError(message);}
}
