/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.nio.file.Files;
import java.util.*;
import org.kanger.interfaces.*;

/** Successive owned conflict roots with one shared native production and exact substitutions. */
public final class DmzSuccessiveCacheRestrictionsRunner {
    private static int checks;
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",Files.createTempDirectory("dmz-successive-").toString());
            for(boolean negative:new boolean[]{false,true})for(boolean recursive:new boolean[]{false,true})for(boolean sameRule:new boolean[]{false,true})run(negative,recursive,sameRule);
            for(boolean negative:new boolean[]{false,true})missingHistory(negative);
            System.out.println("DMZ_SUCCESSIVE_CACHE_RESTRICTIONS_PASS checks="+checks);System.exit(0);
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
    private static DmzProofWitnesses.Witness choice(List<DmzWitnessConflicts.Combination> pairs) {
        for(DmzWitnessConflicts.Combination pair:pairs)for(DmzProofWitnesses.Witness side:Arrays.asList(pair.left,pair.right))
            if(side.step>=0 && side.source.authority==DmzReplayProvenance.Authority.EXTERNAL)return side;
        throw new AssertionError("Missing derived choice");
    }
    private static DmzProofWitnesses.Witness primary(List<DmzWitnessConflicts.Combination> pairs) {
        for(DmzWitnessConflicts.Combination pair:pairs)for(DmzProofWitnesses.Witness side:Arrays.asList(pair.left,pair.right))if(side.step<0)return side;
        throw new AssertionError("Missing primary endpoint");
    }
    private static void run(boolean negative,boolean recursive,boolean sameRule) throws Exception {
        User user=new User();Mind q=new Mind(user);user.setCurrentMind(q);
        String sign=negative?"~":"",opposite=negative?"":"~",premise=recursive?"middle":"a";
        IContextResults.Revision rules=pin(),facts=pin();
        DmzCacheRestrictionSet completed;
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(q);TerminalSupportCapture capture=TerminalSupportCapture.begin(q)) {
            if(recursive)raw(q,rules,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y a(x,y) -> middle(x,y);");
            String production="!@x @y "+premise+"(x,y) -> "+sign+"b(x,y);";
            raw(q,rules,1,DmzReplayProvenance.Authority.EXTERNAL,production);
            raw(q,pin(),0,DmzReplayProvenance.Authority.EXTERNAL,production);
            raw(q,rules,2,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y "+sign+"b(x,y) -> "+sign+"tail(x,y);");
            if(!sameRule) {
                raw(q,rules,3,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y "+premise+"(x,y) -> "+sign+"d(x,y);");
                raw(q,pin(),0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y "+premise+"(x,y) -> "+sign+"d(x,y);");
                raw(q,rules,4,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y "+sign+"d(x,y) -> "+sign+"dtail(x,y);");
            }
            String[] predicates={"b",sameRule?"b":"d"};
            String[] tails={"tail",sameRule?"tail":"dtail"};
            String[] atoms={"John,Tom","Mary,Sarah","Eve,Bob"};
            for(int i=0;i<atoms.length;++i)require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q,facts,i,DmzReplayProvenance.Authority.TARGET_Q,"!a("+atoms[i]+");")),"native warm root");
            for(int i=0;i<2;++i)raw(q,facts,10+i,DmzReplayProvenance.Authority.TARGET_Q,"!"+opposite+predicates[i]+"("+atoms[i]+");");
            String state=DmzObservationStateFingerprint.capture(q);DmzReplayProvenance.SourceCheckpoint sources=DmzReplayProvenance.sourceCheckpoint(q);
            DmzCacheCollisionEvidence.Baseline baseline=DmzCacheCollisionEvidence.beforeOperation(capture,q);
            CollisionProofCapture.Conflict event;
            try(CollisionProofCapture collisions=CollisionProofCapture.begin()) {
                require(!q.compile("!anchor();",null,false),"actual first conflict");event=collisions.snapshot().get(0);
            }
            DmzCacheCollisionEvidence evidence=DmzCacheCollisionEvidence.bind(baseline,event,q,10000);
            require(evidence.eligible && evidence.combinations.size()==2,"two observed source authorships at first root");
            DmzCacheRestrictionSet empty=DmzCacheRestrictionSet.begin(evidence,q),set=empty.append(choice(evidence.combinations),q,10000);
            DmzTypedNativeResume.Result previousResult=null;
            DmzCacheRestrictionSet previousPrefix=null;
            for(int step=1;step<=4;++step) {
                int exhausted=0;
                for(int i=0;i<2;++i)if(selectedCount(set,sign,predicates[i],atoms[i])==2)++exhausted;
                DmzTypedCacheProjection plan=DmzTypedCacheProjection.projectAccumulated(set,q,10000);
                require(plan.eligible && plan.isCurrent(q),"current full successive cache plan");
                DmzTypedNativeResume.Result result=DmzTypedNativeResume.runAccumulated(set,q,10000);
                require(result.compiled==(step==4) && !result.complete && !result.provisional,"success only after both root supports excluded: "+step);
                require(result.retractedCaches.size()==exhausted*2,"full-prefix native cache retraction: "+step);
                int liveRoots=0;
                for(int i=0;i<2;++i) {
                    boolean root=present(result,"!"+sign+predicates[i]+"("+atoms[i]+");");
                    require(root==present(result,"!"+sign+tails[i]+"("+atoms[i]+");"),"dependent cache follows exact root support");
                    if(root)++liveRoots;
                    require(present(result,"!a("+atoms[i]+");") && present(result,"!"+opposite+predicates[i]+"("+atoms[i]+");"),"Q primaries survive all roots");
                    if(recursive)require(present(result,"!middle("+atoms[i]+");"),"upstream cache survives successive cuts");
                }
                require(liveRoots==2-exhausted,"each exhausted root stays absent at subsequent conflict");
                require(present(result,"!"+sign+"b(Eve,Bob);") && present(result,"!"+sign+"tail(Eve,Bob);"),"same production at unrelated substitution remains");
                require(state.equals(DmzObservationStateFingerprint.capture(q)) && sources.isCurrent(q) && set.isCurrent(q)
                        && !q.hasPendingTransactions() && user.getCurrentMind()==q && DmzTerminalRestriction.activeNoGoods(q).isEmpty(),"each continuation restores Q, sources and ownership");
                if(exhausted>0)require(result.applicationDenied>0 && result.queryDenied>0,"both native resurrection gates remain active");
                if(step==4) {
                    try {set.next(result,q,10000);throw new AssertionError("Expected successful continuation refusal");}catch(IllegalStateException expected){++checks;}
                    break;
                }
                DmzCacheRestrictionSet.Next next=set.next(result,q,10000);
                try {next.combinations.clear();throw new AssertionError("Expected immutable next pairs");}catch(UnsupportedOperationException expected){++checks;}
                try {set.next(null,q,10000);throw new AssertionError("Expected missing result refusal");}catch(IllegalStateException expected){++checks;}
                DmzProofWitnesses.Witness selected=choice(next.combinations);
                TerminalSupportCapture.Ground nextRoot=selected.graph.observed.nodes.get(selected.node).ground;
                int prior=0;
                for(DmzProofWitnesses.Witness old:set.selections)if(nextRoot.equivalent(old.graph.observed.nodes.get(old.node).ground))++prior;
                require(next.combinations.size()==2-prior,"remaining exact next conflict combinations");
                try {set.append(next,primary(next.combinations),q,10000);throw new AssertionError("Expected primary refusal");}catch(IllegalStateException expected){++checks;}
                try {set.next(result,q,1);throw new AssertionError("Expected short event refusal");}catch(IllegalStateException expected){++checks;}
                try {set.append(next,selected,q,1);throw new AssertionError("Expected short append refusal");}catch(IllegalStateException expected){++checks;}
                try {empty.next(result,q,10000);throw new AssertionError("Expected unrelated prefix refusal");}catch(IllegalStateException expected){++checks;}
                try {set.next(result,new Mind(user),10000);throw new AssertionError("Expected wrong boundary refusal");}catch(IllegalStateException expected){++checks;}
                if(previousResult!=null) {
                    try {set.next(previousResult,q,10000);throw new AssertionError("Expected old result refusal");}catch(IllegalStateException expected){++checks;}
                    try {previousPrefix.append(next,selected,q,10000);throw new AssertionError("Expected foreign next receipt refusal");}catch(IllegalStateException expected){++checks;}
                }
                if(previousPrefix!=null) {
                    try {set.append(choice(evidence.combinations),q,10000);throw new AssertionError("Expected original graph refusal");}catch(IllegalStateException expected){++checks;}
                }
                previousResult=result;previousPrefix=set;
                DmzCacheRestrictionSet old=set;set=set.append(next,selected,q,10000);
                require(old.selections.size()==step && set.selections.size()==step+1 && empty.selections.isEmpty(),"successive prefixes remain immutable");
            }
            completed=set;
            try(TechnicalMindTransaction occupied=TechnicalMindTransaction.beginIsolated(q)) {
                try {DmzTypedNativeResume.runAccumulated(set,q,10000);throw new AssertionError("Expected occupied refusal");}catch(IllegalStateException expected){require(expected.getMessage().contains("Exclusive"),"exclusive accumulated boundary");}
            }
            raw(q,pin(),0,DmzReplayProvenance.Authority.EXTERNAL,production);
            require(!set.isCurrent(q),"later source alias invalidates whole successive prefix");
            try {DmzTypedNativeResume.runAccumulated(set,q,10000);throw new AssertionError("Expected stale refusal");}catch(IllegalStateException expected){++checks;}
            require(!q.hasPendingTransactions() && DmzTerminalRestriction.activeNoGoods(q).isEmpty(),"stale continuation acquires no scope");
        }
        require(!completed.isCurrent(q) && completed.selections.size()==4,"closed history invalidates authority but preserves detached prefix");
    }
    private static void missingHistory(boolean negative) throws Exception {
        User user=new User();Mind q=new Mind(user);user.setCurrentMind(q);
        String sign=negative?"~":"",opposite=negative?"":"~";
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(q);TerminalSupportCapture capture=TerminalSupportCapture.begin(q)) {
            for(String predicate:Arrays.asList("b","d"))raw(q,pin(),0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y a(x,y) -> "+sign+predicate+"(x,y);");
            require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q,pin(),0,DmzReplayProvenance.Authority.TARGET_Q,"!a(John,Tom);")),"both native cache roots materialized before later alias");
            for(String predicate:Arrays.asList("b","d"))raw(q,pin(),0,DmzReplayProvenance.Authority.TARGET_Q,"!"+opposite+predicate+"(John,Tom);");
            CollisionProofCapture.Conflict original;
            try(CollisionProofCapture collisions=CollisionProofCapture.begin()) {
                require(!q.compile("!anchor();",null,false),"initial order inspection is read only");original=collisions.snapshot().get(0);
            }
            CollisionProofCapture.Node cache=original.left.nodes.get(original.left.root).generated?original.left.nodes.get(original.left.root):original.right.nodes.get(original.right.root);
            String other=cache.ground.predicate.equals("b")?"d":"b";
            raw(q,pin(),0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y a(x,y) -> "+sign+other+"(x,y);");
            DmzCacheCollisionEvidence.Baseline baseline=DmzCacheCollisionEvidence.beforeOperation(capture,q);
            CollisionProofCapture.Conflict event;
            try(CollisionProofCapture collisions=CollisionProofCapture.begin()) {
                require(!q.compile("!anchor();",null,false),"fresh first conflict after unrelated late alias");event=collisions.snapshot().get(0);
            }
            DmzCacheCollisionEvidence evidence=DmzCacheCollisionEvidence.bind(baseline,event,q,10000);
            require(evidence.eligible && evidence.combinations.size()==1,"first root retains exact stored history");
            DmzCacheRestrictionSet set=DmzCacheRestrictionSet.begin(evidence,q).append(choice(evidence.combinations),q,10000);
            String state=DmzObservationStateFingerprint.capture(q);DmzReplayProvenance.SourceCheckpoint sources=DmzReplayProvenance.sourceCheckpoint(q);
            DmzTypedNativeResume.Result result=DmzTypedNativeResume.runAccumulated(set,q,10000);
            require(!result.compiled && result.retractedCaches.size()==1 && !result.conflicts.isEmpty(),"continuation reaches separate cache conflict");
            try {set.next(result,q,10000);throw new AssertionError("Expected missing successive history refusal");}
            catch(IllegalStateException expected){require(expected.getMessage().contains("Covered live"),"next root must have full native storage coverage");}
            require(set.selections.size()==1 && set.isCurrent(q) && state.equals(DmzObservationStateFingerprint.capture(q)) && sources.isCurrent(q)
                    && !q.hasPendingTransactions() && user.getCurrentMind()==q && DmzTerminalRestriction.activeNoGoods(q).isEmpty(),"missing next history preserves Q and original immutable prefix");
        }
    }
    private static int selectedCount(DmzCacheRestrictionSet set,String sign,String predicate,String atom) throws Exception {
        TerminalSupportCapture.Ground ground=DmzGroundReplay.parseIdentity("!"+sign+predicate+"("+atom+");").ground();
        int count=0;
        for(DmzProofWitnesses.Witness choice:set.selections)if(ground.equivalent(choice.graph.observed.nodes.get(choice.node).ground))++count;
        return count;
    }
    private static void require(boolean value,String message){++checks;if(!value)throw new AssertionError(message);}
}
