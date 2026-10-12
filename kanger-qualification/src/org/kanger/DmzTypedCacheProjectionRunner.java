/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.nio.file.Files;
import java.util.*;
import org.kanger.interfaces.*;
import org.kanger.units.Rule;

/** Real materialized caches, exact source alternatives and read-only downstream projection. */
public final class DmzTypedCacheProjectionRunner {
    private static int checks;
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",Files.createTempDirectory("dmz-typed-cache-").toString());
            for(String shape:new String[]{"ordinary","permutation","constants","sets"})
                for(String support:new String[]{"sole","aliases","independent","primary"})run(shape,support);
            pending();recursive();
            System.out.println("DMZ_TYPED_CACHE_PROJECTION_PASS checks="+checks);System.exit(0);
        } catch(Throwable failure) {failure.printStackTrace();System.exit(1);}
    }
    private static IContextResults.Revision pin(){return new IContextResults.Revision(UUID.randomUUID(),1);}
    private static void input(Mind q,IContextResults.Revision source,long id,DmzReplayProvenance.Authority authority,String text,boolean infer) throws Exception {
        if(infer)require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q,source,id,authority,text)),"native accepted input: "+text);
        else {DmzReplayProvenance.replayRule(q,source,id,authority,text);++checks;}
    }
    private static DmzTypedCacheProjection.Cache cache(DmzTypedCacheProjection plan,String statement) throws Exception {
        TerminalSupportCapture.Ground expected=DmzGroundReplay.parseIdentity("!"+statement+";").ground();
        for(DmzTypedCacheProjection.Cache c:plan.caches)if(c.ground.equivalent(expected))return c;
        throw new AssertionError("Missing native cache: "+statement);
    }
    private static void run(String shape,String support) throws Exception {
        User user=new User();Mind q=new Mind(user);user.setCurrentMind(q);
        IContextResults.Revision a=pin(),b=pin(),facts=pin(),other=pin();
        String signature=shape.equals("permutation")?"y,x":shape.equals("constants")?"x,y,Fixed":"x,y";
        String values=shape.equals("sets")?"[John,Tom],7":"John,Tom";
        String root=shape.equals("permutation")?"Tom,John":shape.equals("constants")?"John,Tom,Fixed":values;
        String otherValues=shape.equals("sets")?"[Mary,Sarah],9":"Mary,Sarah";
        String otherRoot=shape.equals("permutation")?"Sarah,Mary":shape.equals("constants")?"Mary,Sarah,Fixed":otherValues;
        String familyRule=shape.equals("constants")?"!@x @y @z male(x,y,z) -> downstream(x,y,z);":"!@x @y male(x,y) -> downstream(x,y);";
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(q);TerminalSupportCapture capture=TerminalSupportCapture.begin(q)) {
            input(q,a,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y a(x,y) -> male("+signature+");",true);
            input(q,a,1,DmzReplayProvenance.Authority.EXTERNAL,familyRule,true);
            input(q,facts,0,DmzReplayProvenance.Authority.TARGET_Q,"!a("+values+");",true);
            input(q,facts,1,DmzReplayProvenance.Authority.TARGET_Q,"!a("+otherValues+");",true);
            if(support.equals("aliases")) {
                input(q,other,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y a(x,y) -> male("+signature+");",false);
                input(q,other,1,DmzReplayProvenance.Authority.EXTERNAL,"!a("+values+");",false);
            } else if(support.equals("independent")) {
                input(q,other,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y c(x,y) -> male("+signature+");",true);
                input(q,other,1,DmzReplayProvenance.Authority.EXTERNAL,"!c("+values+");",true);
            } else if(support.equals("primary"))
                input(q,facts,2,DmzReplayProvenance.Authority.TARGET_Q,"!male("+root+");",true);
            int liveCaches=0;for(IRule rule:q.getRules())if(!rule.isDeleted(q)&&q.getRules().isGenerated(rule))++liveCaches;
            require(liveCaches==(support.equals("primary")?3:4),"warm cache count accounts for asserted root promotion: "+shape+"/"+support+" "+liveCaches);
            input(q,b,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y b(x,y) -> ~male("+signature+");",false);
            input(q,facts,3,DmzReplayProvenance.Authority.TARGET_Q,"!b("+values+");",false);
            DmzGroundCollisionRestriction guard=DmzGroundCollisionRestriction.beforeOperation(capture,q,10000);
            DmzProvisionalCollisionProof proof;
            try(CollisionProofCapture collisions=CollisionProofCapture.begin()) {
                require(!q.compile("!anchor();",null,false),"warm native conflict operation refused");
                require(!collisions.snapshot().isEmpty(),"warm native collision captured");
                proof=DmzProvisionalCollisionProof.build(collisions.snapshot().get(0),10000,10000);
            }
            DmzProofWitnesses.Witness selected=null;
            for(DmzWitnessConflicts.Combination pair:proof.combinations)for(DmzProofWitnesses.Witness side:Arrays.asList(pair.left,pair.right))
                if(side.step>=0 && side.source.context.equals(a.getContextId()) && side.source.sourceRule==0
                        && side.premises.size()==1 && side.premises.get(0).step<0 && side.premises.get(0).source.context.equals(facts.getContextId()))selected=side;
            require(selected!=null,"direct external witness with Q primary donor captured");
            require(guard.project(proof,selected,q,10000).eligible,"warm native typed routes covered");
            String state=DmzObservationStateFingerprint.capture(q);DmzReplayProvenance.SourceCheckpoint sources=DmzReplayProvenance.sourceCheckpoint(q);
            DmzTypedCacheProjection plan=DmzTypedCacheProjection.project(guard,proof,selected,q,10000);
            require(plan.eligible && !plan.truncated && !plan.complete && plan.isCurrent(q),"current bounded warm cache plan");
            require(plan.caches.size()==liveCaches,"every live native generated cache audited");
            DmzTypedCacheProjection.Cache downstream=cache(plan,"downstream("+root+")");
            int before=support.equals("aliases")?4:support.equals("sole")?1:2;
            if(!support.equals("primary")) {
                DmzTypedCacheProjection.Cache rootCache=cache(plan,"male("+root+")");
                require(rootCache.before==before && rootCache.after==before-1,"exact one root source choice excluded");
                require(rootCache.losesLastSupport()==support.equals("sole"),"root retracted only after last support");
            } else {
                TerminalSupportCapture.Ground primary=DmzGroundReplay.parseIdentity("!male("+root+");").ground();
                require(plan.caches.stream().noneMatch(c->c.ground.equivalent(primary)),"asserted root is a primary, not a generated cache deletion candidate");
                boolean seen=false;
                for(int i=0;i<plan.graph.observed.nodes.size();++i)if(primary.equivalent(plan.graph.observed.nodes.get(i).ground)) {
                    DmzProofWitnesses.Result retained=DmzProofWitnesses.enumerate(plan.graph,i,10000);
                    require(retained.witnesses.size()==1 && retained.witnesses.get(0).step<0 && retained.witnesses.get(0).source.authority==DmzReplayProvenance.Authority.TARGET_Q,"Q primary proof retained after external application exclusion");seen=true;
                }
                require(seen,"primary ground retained in detached graph");
            }
            require(downstream.before==before && downstream.after==before-1,"exclusion propagates through dependent cache proofs");
            require(downstream.losesLastSupport()==support.equals("sole"),"dependent cache retracted only after last support");
            DmzTypedCacheProjection.Cache otherCache=cache(plan,"male("+otherRoot+")");
            require(otherCache.after==otherCache.before && otherCache.after>0 && !cache(plan,"downstream("+otherRoot+")").losesLastSupport(),"different native substitution keeps all its source alternatives");
            for(DmzTypedCacheProjection.Cache c:plan.caches) {
                IRule actual=null;for(IRule rule:q.getRules())if(rule.getId()==c.nativeRule)actual=rule;
                require(actual!=null && !actual.isDeleted(q) && DmzGroundIdentity.capture(actual,q).ground().equivalent(c.ground),"cache diagnostic ID identifies live exact signed ground");
            }
            require(state.equals(DmzObservationStateFingerprint.capture(q)) && sources.isCurrent(q) && !q.hasPendingTransactions() && user.getCurrentMind()==q,"planning preserves Q, primary sources and ownership");
            try {plan.caches.clear();throw new AssertionError("Mutable cache receipt");}catch(UnsupportedOperationException expected){++checks;}
            DmzTypedCacheProjection shortPlan=DmzTypedCacheProjection.project(guard,proof,selected,q,1);
            require(!shortPlan.eligible && shortPlan.truncated && shortPlan.caches.isEmpty() && shortPlan.graph==null,"budget exhaustion exposes no partial deletion plan");
            require(!DmzTypedCacheProjection.project(guard,proof,selected.premises.get(0),q,10000).eligible,"primary input is not a cache restriction");
            Rule changed=null;for(IRule rule:q.getRules())if(rule.getId()==otherCache.nativeRule)changed=(Rule)rule;
            changed.setDeleted(true,q);require(!plan.isCurrent(q),"changed physical cache invalidates current receipt");
            changed.setDeleted(false,q);require(plan.isCurrent(q),"exact restored native boundary revalidates read-only receipt");
            input(q,other,9,DmzReplayProvenance.Authority.EXTERNAL,"!a("+values+");",false);
            require(!plan.isCurrent(q),"new source occurrence invalidates old cache receipt");
            require(!DmzTypedCacheProjection.project(guard,proof,selected,q,10000).eligible,"stale collision boundary refuses a new plan");
            require(downstream.after==before-1,"detached plan stays readable after source change");
            require(!q.hasPendingTransactions(),"projection opens no native child");
        }
    }
    private static void pending() throws Exception {
        User user=new User();Mind root=new Mind(user);user.setCurrentMind(root);IContextResults.Revision a=pin(),b=pin(),facts=pin();
        String state=DmzObservationStateFingerprint.capture(root);
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(root);TechnicalMindTransaction tx=TechnicalMindTransaction.beginIsolated(root);
                TerminalSupportCapture capture=TerminalSupportCapture.begin(tx.mind())) {
            Mind q=tx.mind();
            input(q,a,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y a(x,y) -> male(x,y);",true);
            input(q,facts,0,DmzReplayProvenance.Authority.TARGET_Q,"!a(John,Tom);",true);
            input(q,b,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y b(x,y) -> ~male(x,y);",false);
            input(q,facts,1,DmzReplayProvenance.Authority.TARGET_Q,"!b(John,Tom);",false);
            DmzGroundCollisionRestriction guard=DmzGroundCollisionRestriction.beforeOperation(capture,q,10000);
            DmzProvisionalCollisionProof proof;
            try(CollisionProofCapture collisions=CollisionProofCapture.begin()) {
                require(!q.compile("!anchor();",null,false),"pending warm native conflict");
                proof=DmzProvisionalCollisionProof.build(collisions.snapshot().get(0),10000,10000);
            }
            DmzProofWitnesses.Witness selected=null;
            for(DmzWitnessConflicts.Combination pair:proof.combinations)for(DmzProofWitnesses.Witness side:Arrays.asList(pair.left,pair.right))
                if(side.step>=0 && side.source.context.equals(a.getContextId()))selected=side;
            require(selected!=null && guard.project(proof,selected,q,10000).eligible,"pending detached collision still inspectable");
            DmzTypedCacheProjection plan=DmzTypedCacheProjection.project(guard,proof,selected,q,10000);
            require(!plan.eligible && plan.caches.isEmpty() && plan.graph==null,"pending sources cannot produce a settled cache plan");
        }
        require(state.equals(DmzObservationStateFingerprint.capture(root)) && !root.hasPendingTransactions(),"pending planning refusal and rollback restore physical Q");
    }
    private static void recursive() throws Exception {
        User user=new User();Mind q=new Mind(user);user.setCurrentMind(q);IContextResults.Revision a=pin(),b=pin(),facts=pin();
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(q);TerminalSupportCapture capture=TerminalSupportCapture.begin(q)) {
            input(q,a,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y a(x,y) -> middle(x,y);",true);
            input(q,a,1,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y middle(x,y) -> male(x,y);",true);
            input(q,facts,0,DmzReplayProvenance.Authority.TARGET_Q,"!a(John,Tom);",true);
            input(q,b,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y b(x,y) -> ~male(x,y);",false);
            input(q,facts,1,DmzReplayProvenance.Authority.TARGET_Q,"!b(John,Tom);",false);
            DmzGroundCollisionRestriction guard=DmzGroundCollisionRestriction.beforeOperation(capture,q,10000);
            DmzProvisionalCollisionProof proof;
            try(CollisionProofCapture collisions=CollisionProofCapture.begin()) {
                require(!q.compile("!anchor();",null,false),"recursive warm native conflict");
                proof=DmzProvisionalCollisionProof.build(collisions.snapshot().get(0),10000,10000);
            }
            DmzProofWitnesses.Witness selected=null;
            for(DmzWitnessConflicts.Combination pair:proof.combinations)for(DmzProofWitnesses.Witness side:Arrays.asList(pair.left,pair.right))
                if(side.step>=0 && side.source.context.equals(a.getContextId()) && side.premises.get(0).step>=0)selected=side;
            require(selected!=null && guard.project(proof,selected,q,10000).eligible,"recursive detached restriction recognized independently");
            String state=DmzObservationStateFingerprint.capture(q);
            DmzTypedCacheProjection plan=DmzTypedCacheProjection.project(guard,proof,selected,q,10000);
            require(!plan.eligible && plan.graph==null && plan.caches.isEmpty(),"recursive selected cuts are not silently admitted by direct cache protocol");
            require(state.equals(DmzObservationStateFingerprint.capture(q)),"recursive refusal preserves caches and Q");
        }
    }
    private static void require(boolean value,String message){++checks;if(!value)throw new AssertionError(message);}
}
