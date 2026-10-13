/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.nio.file.Files;
import java.util.*;
import org.kanger.interfaces.*;

/** Native continuation over immutable prefixes of exact cache restrictions. */
public final class DmzAccumulatedCacheRestrictionsRunner {
    private static int checks;
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",Files.createTempDirectory("dmz-cache-prefix-").toString());
            for(boolean negative:new boolean[]{false,true})for(boolean recursive:new boolean[]{false,true})
                for(boolean aliases:new boolean[]{false,true})run(negative,recursive,aliases);
            System.out.println("DMZ_ACCUMULATED_CACHE_RESTRICTIONS_PASS checks="+checks);System.exit(0);
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
    private static void run(boolean negative,boolean recursive,boolean aliases) throws Exception {
        User user=new User();Mind q=new Mind(user);user.setCurrentMind(q);IContextResults.Revision rules=pin(),other=pin(),facts=pin();
        String sign=negative?"~":"",opposite=negative?"":"~",premise=recursive?"middle":"a";
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(q);TerminalSupportCapture capture=TerminalSupportCapture.begin(q)) {
            if(recursive)raw(q,rules,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y a(x,y) -> middle(x,y);");
            String production="!@x @y "+premise+"(x,y) -> "+sign+"b(x,y);";
            raw(q,rules,1,DmzReplayProvenance.Authority.EXTERNAL,production);
            raw(q,other,0,DmzReplayProvenance.Authority.EXTERNAL,aliases?production:"!@x @y c(x,y) -> "+sign+"b(x,y);");
            raw(q,rules,2,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y "+sign+"b(x,y) -> "+sign+"tail(x,y);");
            if(aliases) {
                raw(q,pin(),0,DmzReplayProvenance.Authority.EXTERNAL,production);
                raw(q,pin(),0,DmzReplayProvenance.Authority.EXTERNAL,production);
            }
            require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q,facts,0,DmzReplayProvenance.Authority.TARGET_Q,"!a(John,Tom);")),"native primary materializes shared root after production sources are present");
            if(!aliases)require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q,other,1,DmzReplayProvenance.Authority.EXTERNAL,"!c(John,Tom);")),"independent native path materialized");
            require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q,facts,1,DmzReplayProvenance.Authority.TARGET_Q,"!a(Mary,Sarah);")),"other native substitution materialized");
            raw(q,facts,2,DmzReplayProvenance.Authority.TARGET_Q,"!"+opposite+"b(John,Tom);");
            DmzCacheCollisionEvidence.Baseline baseline=DmzCacheCollisionEvidence.beforeOperation(capture,q);CollisionProofCapture.Conflict event;
            try(CollisionProofCapture conflicts=CollisionProofCapture.begin()) {
                require(!q.compile("!anchor();",null,false),"actual warm cache conflict");event=conflicts.snapshot().get(0);
            }
            DmzCacheCollisionEvidence evidence=DmzCacheCollisionEvidence.bind(baseline,event,q,10000);
            int expected=aliases?4:2;
            if(!aliases) {
                require(!evidence.eligible && !evidence.truncated,"unobserved independent historical route refuses accumulated permission");
                CollisionProofCapture.Node cache=event.left.nodes.get(event.left.root).generated?event.left.nodes.get(event.left.root):event.right.nodes.get(event.right.root);
                DmzStoredGroundRoutes missing=DmzStoredGroundRoutes.capture(capture,q,cache.nativeRule,10000);
                require(!missing.covered && missing.expectedProofs==2 && missing.observedProofs==1,"expected independent path is not substituted for its missing native storage route");
                try {DmzCacheRestrictionSet.begin(evidence,q);throw new AssertionError("Expected uncovered prefix refusal");}
                catch(IllegalStateException expectedFailure){++checks;}
                require(!q.hasPendingTransactions() && DmzTerminalRestriction.activeNoGoods(q).isEmpty(),"missing independent route acquires no native scope");
                return;
            }
            require(evidence.eligible && evidence.combinations.size()==expected,"all historical source alternatives bound: "+negative+"/"+recursive+"/"+aliases+" eligible="+evidence.eligible+" truncated="+evidence.truncated+" pairs="+evidence.combinations.size());
            List<DmzProofWitnesses.Witness> choices=new ArrayList<DmzProofWitnesses.Witness>();DmzProofWitnesses.Witness primary=null;
            for(DmzWitnessConflicts.Combination pair:evidence.combinations)for(DmzProofWitnesses.Witness side:Arrays.asList(pair.left,pair.right))
                if(side.step>=0)choices.add(side);else primary=side;
            String state=DmzObservationStateFingerprint.capture(q);DmzReplayProvenance.SourceCheckpoint sources=DmzReplayProvenance.sourceCheckpoint(q);
            DmzCacheRestrictionSet empty=DmzCacheRestrictionSet.begin(evidence,q),set=empty;
            try {DmzTypedNativeResume.runAccumulated(empty,q,10000);throw new AssertionError("Expected empty restriction refusal");}
            catch(IllegalArgumentException expectedFailure){++checks;}
            for(int i=0;i<choices.size();++i) {
                DmzCacheRestrictionSet previous=set;set=set.append(choices.get(i),q,10000);
                require(previous.selections.size()==i && set.selections.size()==i+1 && empty.selections.isEmpty(),"immutable accumulated prefixes");
                require(DmzProofWitnesses.enumerate(set.graph,choices.get(i).node,10000).witnesses.size()==expected-i-1,"one exact remaining root choice removed per append");
                DmzTypedCacheProjection plan=DmzTypedCacheProjection.projectAccumulated(set,q,10000);
                require(plan.eligible && plan.isCurrent(q),"current full-prefix cache plan");
                DmzTypedNativeResume.Result result=DmzTypedNativeResume.runAccumulated(set,q,10000);
                boolean last=i+1==expected;
                require(result.compiled==last && !result.complete && !result.provisional,"actual native continuation follows remaining full support");
                require(result.retractedCaches.size()==(last?2:0),"last accumulated support alone retracts root and tail");
                require(present(result,"!"+sign+"b(John,Tom);")==!last && present(result,"!"+sign+"tail(John,Tom);")==!last,"actual shared caches follow full accumulated support");
                if(last)require(result.applicationDenied>0 && result.queryDenied>0,"both native gates prevent cumulative resurrection");
                else require(!result.conflicts.isEmpty(),"retained root source choices preserve actual conflict");
                require(present(result,"!a(John,Tom);") && present(result,"!"+opposite+"b(John,Tom);"),"Q donor and contrary primary survive");
                require(present(result,"!"+sign+"b(Mary,Sarah);") && present(result,"!"+sign+"tail(Mary,Sarah);"),"other substitution retains its native materializations");
                if(recursive)require(present(result,"!middle(John,Tom);"),"upstream cache is not globally cut");
                require(state.equals(DmzObservationStateFingerprint.capture(q)) && sources.isCurrent(q) && set.isCurrent(q)
                        && !q.hasPendingTransactions() && user.getCurrentMind()==q && DmzTerminalRestriction.activeNoGoods(q).isEmpty(),"every prefix restores Q, sources and owned scope");
                try {set.append(choices.get(i),q,10000);throw new AssertionError("Expected duplicate choice refusal");}
                catch(IllegalStateException expectedFailure){++checks;}
                require(set.selections.size()==i+1,"duplicate refusal preserves accumulated value");
            }
            try {set.selections.clear();throw new AssertionError("Expected immutable selection list");}catch(UnsupportedOperationException expectedFailure){++checks;}
            try {set.append(primary,q,10000);throw new AssertionError("Expected primary refusal");}catch(IllegalStateException expectedFailure){++checks;}
            try {empty.append(choices.get(0),q,1);throw new AssertionError("Expected budget refusal");}catch(IllegalStateException expectedFailure){++checks;}
            require(!DmzTypedCacheProjection.projectAccumulated(set,q,1).eligible,"short accumulated cache plan refuses");
            try(TechnicalMindTransaction occupied=TechnicalMindTransaction.beginIsolated(q)) {
                try {DmzTypedNativeResume.runAccumulated(set,q,10000);throw new AssertionError("Expected occupied boundary refusal");}
                catch(IllegalStateException expectedFailure){require(expectedFailure.getMessage().contains("Exclusive"),"exclusive scope required for accumulated run");}
            }
            raw(q,pin(),0,DmzReplayProvenance.Authority.EXTERNAL,"!a(John,Tom);");
            require(!set.isCurrent(q),"new source occurrence invalidates full accumulated prefix");
            try {DmzTypedNativeResume.runAccumulated(set,q,10000);throw new AssertionError("Expected stale prefix refusal");}catch(IllegalStateException expectedFailure){++checks;}
            require(!q.hasPendingTransactions() && DmzTerminalRestriction.activeNoGoods(q).isEmpty(),"stale prefix never acquires native scope");
        }
    }
    private static void require(boolean value,String message){++checks;if(!value)throw new AssertionError(message);}
}
