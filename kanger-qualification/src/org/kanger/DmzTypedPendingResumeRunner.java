/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.nio.file.Files;
import java.util.*;
import org.kanger.interfaces.*;

/** Exact full-chain restrictions with actual Core cache state and rollback. */
public final class DmzTypedPendingResumeRunner {
    private static int checks;
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",Files.createTempDirectory("dmz-typed-pending-").toString());
            for(boolean warm:new boolean[]{false,true})for(boolean negative:new boolean[]{false,true})
                for(String support:new String[]{"sole","aliases","independent","primary"})run(warm,negative,support,3);
            run(false,false,"sole",4);run(true,false,"sole",4);
            for(boolean warm:new boolean[]{false,true})for(String support:new String[]{"sole","aliases","independent","primary"})run(warm,false,support,1);
            ownership();
            System.out.println("DMZ_TYPED_PENDING_RESUME_PASS checks="+checks);System.exit(0);
        } catch(Throwable failure){failure.printStackTrace();System.exit(1);}
    }
    private static IContextResults.Revision pin(){return new IContextResults.Revision(UUID.randomUUID(),1);}
    private static void input(Mind q,IContextResults.Revision pin,long id,DmzReplayProvenance.Authority authority,String text,boolean warm) throws Exception {
        if(warm)require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q,pin,id,authority,text)),"native sourced input "+text);
        else {DmzReplayProvenance.replayRule(q,pin,id,authority,text);++checks;}
    }
    private static boolean present(DmzTypedNativeResume.Result result,String atom) throws Exception {
        TerminalSupportCapture.Ground expected=DmzGroundReplay.parseIdentity("!"+atom+";").ground();
        for(DmzTypedNativeResume.Materialization m:result.materializations)if(m.ground.equivalent(expected))return true;
        return false;
    }
    private static void run(boolean warm,boolean negative,String support,int depth) throws Exception {

        User user=new User();Mind authority=new Mind(user);user.setCurrentMind(authority);IContextResults.Revision rules=pin(),facts=pin(),b=pin(),other=pin();
        String sign=negative?"~":"",opposite=negative?"":"~";
        String root=sign+"p"+(depth-1),end=sign+"p"+depth,tail=sign+"tail";
        String authorityState=DmzObservationStateFingerprint.capture(authority);
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(authority)) {
          try(TechnicalMindTransaction owner=TechnicalMindTransaction.beginIsolated(authority);
                  TerminalSupportCapture capture=TerminalSupportCapture.begin(owner.mind())) {
            Mind q=owner.mind();
            for(int i=0;i<depth;++i)
                input(q,rules,i,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y "+(i==0?"":sign)+"p"+i+"(x,y) -> "+sign+"p"+(i+1)+"(x,y);",warm);
            input(q,rules,depth,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y "+end+"(x,y) -> "+tail+"(x,y);",warm);
            input(q,facts,0,DmzReplayProvenance.Authority.TARGET_Q,"!p0(John,Tom);",warm);
            input(q,facts,1,DmzReplayProvenance.Authority.TARGET_Q,"!p0(Mary,Sarah);",warm);
            if(support.equals("aliases")) {
                input(q,other,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y p0(x,y) -> "+sign+"p1(x,y);",false);
                input(q,other,1,DmzReplayProvenance.Authority.EXTERNAL,"!p0(John,Tom);",false);
            } else if(support.equals("independent")) {
                input(q,other,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y c(x,y) -> "+sign+"p1(x,y);",warm);
                input(q,other,1,DmzReplayProvenance.Authority.EXTERNAL,"!c(John,Tom);",warm);
            } else if(support.equals("primary"))
                input(q,facts,2,DmzReplayProvenance.Authority.TARGET_Q,"!"+sign+"p1(John,Tom);",warm);
            input(q,b,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y b(x,y) -> "+opposite+"p"+depth+"(x,y);",false);
            input(q,facts,3,DmzReplayProvenance.Authority.TARGET_Q,"!b(John,Tom);",false);
            DmzGroundCollisionRestriction guard=DmzGroundCollisionRestriction.beforeOperation(capture,q,10000);
            DmzProvisionalCollisionProof proof;
            try(CollisionProofCapture collisions=CollisionProofCapture.begin()) {
                require(!q.compile("!anchor();",null,false),"recursive native baseline conflict");
                require(!collisions.snapshot().isEmpty(),"recursive native event captured");
                proof=DmzProvisionalCollisionProof.build(collisions.snapshot().get(0),10000,10000);
            }
            DmzProofWitnesses.Witness selected=null;
            for(DmzWitnessConflicts.Combination pair:proof.combinations)for(DmzProofWitnesses.Witness side:Arrays.asList(pair.left,pair.right))
                if(side.step>=0 && side.source.context.equals(rules.getContextId())
                        && (depth==1 || side.premises.get(0).step>=0) && leaf(side).source.context.equals(facts.getContextId()) && leaf(side).source.sourceRule==0)selected=side;
            require(selected!=null,"actual recursive proof from the protected p0 donor selected");
            int selectedIndex=(int)selected.source.sourceRule+1;
            root=sign+"p"+selectedIndex;
            require(DmzProofWitnesses.NoGood.fromTyped(selected).depth==selectedIndex+1,"every selected chain step retains its typed identity");
            require(guard.project(proof,selected,q,10000).eligible,"whole recursive native route covered");
            String state=DmzObservationStateFingerprint.capture(q);DmzReplayProvenance.SourceCheckpoint sources=DmzReplayProvenance.sourceCheckpoint(q);
            DmzTypedCacheProjection plan=DmzTypedCacheProjection.projectPending(owner,guard,proof,selected,10000);
            require(plan.eligible && plan.isCurrent(q),"current full-chain cache plan: warm="+warm+" declared="+depth+" selected="+selectedIndex+" witness="+DmzProofWitnesses.NoGood.fromTyped(selected).depth+" truncated="+plan.truncated);
            require(!DmzTypedCacheProjection.projectRecursive(guard,proof,selected,q,10000).eligible,"settled entry refuses pending sources");
            List<DmzReplayProvenance.SourceObservation> pendingSources=new ArrayList<DmzReplayProvenance.SourceObservation>();
            for(IRule item:q.getRules())pendingSources.addAll(DmzReplayProvenance.observedSources(q,item.getId()));
            require(!pendingSources.isEmpty(),"pending source observations captured");
            for(DmzReplayProvenance.SourceObservation source:pendingSources)require(source.outcome==DmzReplayProvenance.Outcome.PENDING,"source remains pending before owned continuation");
            DmzTypedNativeResume.Result result=DmzTypedNativeResume.runPending(owner,guard,proof,selected,10000);
            require(result.provisional && !result.complete && result.compiled==support.equals("sole"),"actual recursive result: "+warm+"/"+negative+"/"+support+"/"+depth);
            require(result.retractedCaches.size()==(warm && support.equals("sole")?depth-selectedIndex+2:0),"only root/downstream materializations lose full-chain support");
            boolean retainedVisible=warm && !support.equals("sole");
            require(present(result,root+"(John,Tom)")== (retainedVisible || (support.equals("primary") && selectedIndex==1)) && present(result,end+"(John,Tom)")== (retainedVisible || (support.equals("primary") && depth==1))
                    && present(result,tail+"(John,Tom)")==retainedVisible,"actual retained caches or failed cold-frame rollback: "+warm+"/"+negative+"/"+support);
            for(int i=1;i<selectedIndex;++i)require(present(result,sign+"p"+i+"(John,Tom)")==(warm || result.compiled || (support.equals("primary") && i==1)),"upstream native state respects inner settlement: "+i);
            require(present(result,root+"(Mary,Sarah)")== (warm || result.compiled) && present(result,tail+"(Mary,Sarah)")== (warm || result.compiled),"other substitution remains warm or rolls back with a refused cold frame");
            require(present(result,"p0(John,Tom)") && present(result,"b(John,Tom)"),"Q primary donors stay live");
            if(support.equals("sole"))require(result.applicationDenied>0 && result.queryDenied>0,"both actual native gates block recursive resurrection");
            else require(!result.conflicts.isEmpty(),"remaining full-chain path keeps actual native conflict");
            TerminalSupportCapture.Ground expectedRoot=DmzGroundReplay.parseIdentity("!"+root+"(John,Tom);").ground();boolean seen=false;
            for(int i=0;i<result.graph.observed.nodes.size();++i)if(result.graph.observed.nodes.get(i).ground.equivalent(expectedRoot)) {
                seen=true;DmzProofWitnesses.Result actual=DmzProofWitnesses.enumerate(result.graph,i,10000);
                require(!actual.truncated && actual.witnesses.size()==(support.equals("sole")?0:support.equals("aliases")?3:1),"observed recursive root proof count");
            }
            if(!support.equals("sole"))require(seen,"retained recursive root observed");
            require(state.equals(DmzObservationStateFingerprint.capture(q)) && sources.isCurrent(q) && user.getCurrentMind()==authority
                    && !q.hasPendingTransactions() && DmzTerminalRestriction.activeNoGoods(q).isEmpty() && plan.isCurrent(q),"recursive rollback restores Q, source state and ownership");
            for(DmzReplayProvenance.SourceObservation source:pendingSources) {
                boolean current=false;
                for(DmzReplayProvenance.SourceObservation after:DmzReplayProvenance.observedSources(q,source.binding.nativeRule))
                    if(after.binding==source.binding && after.outcome==DmzReplayProvenance.Outcome.PENDING)current=true;
                require(current,"continuation never promotes pending provenance");
            }
            try {DmzTypedNativeResume.runPending(owner,guard,proof,selected,1);throw new AssertionError("Expected budget refusal");}
            catch(IllegalStateException expected){++checks;}
            require(state.equals(DmzObservationStateFingerprint.capture(q)),"budget refusal preserves Q");
            try(TechnicalMindTransaction occupied=TechnicalMindTransaction.beginIsolated(q)) {
                try {DmzTypedNativeResume.runPending(owner,guard,proof,selected,10000);throw new AssertionError("Expected occupied boundary refusal");}
                catch(IllegalStateException expected){require(expected.getMessage().contains("Exclusive"),"pending owner cannot authorize a concurrent child");}
            }
            try(TechnicalMindTransaction wrong=TechnicalMindTransaction.beginIsolated(new Mind(user))) {
                try {DmzTypedNativeResume.runPending(wrong,guard,proof,selected,10000);throw new AssertionError("Expected wrong owner refusal");}
                catch(IllegalStateException expected){++checks;}
            }
            require(state.equals(DmzObservationStateFingerprint.capture(q)) && sources.isCurrent(q),"ownership refusal preserves pending boundary");
            input(q,other,9,DmzReplayProvenance.Authority.EXTERNAL,"!p0(John,Tom);",false);
            try {DmzTypedNativeResume.runPending(owner,guard,proof,selected,10000);throw new AssertionError("Expected stale refusal");}
            catch(IllegalStateException expected){++checks;}
            require(!q.hasPendingTransactions() && DmzTerminalRestriction.activeNoGoods(q).isEmpty(),"stale full-chain receipt cannot acquire scope");
          }
          require(authorityState.equals(DmzObservationStateFingerprint.capture(authority)) && !authority.hasPendingTransactions(),"outer pending rollback restores authority");
          require(journal.settlementSnapshot().pending==0,"outer rollback leaves no pending source frames");
        }
    }
    private static DmzProofWitnesses.Witness leaf(DmzProofWitnesses.Witness witness) {
        while(witness.step>=0)witness=witness.premises.get(0);return witness;
    }
    private static void ownership() throws Exception {
        User user=new User();Mind q=new Mind(user);user.setCurrentMind(q);
        final TechnicalMindTransaction owner=TechnicalMindTransaction.beginIsolated(q);
        final boolean[] refused={false};
        Thread foreign=new Thread(new Runnable(){public void run(){
            try {owner.pendingBoundary();}catch(IllegalStateException expected){refused[0]=true;}
        }});foreign.start();foreign.join();require(refused[0],"foreign thread cannot acquire pending boundary");
        owner.rollback();
        try {DmzTypedNativeResume.runPending(owner,null,null,null,10000);throw new AssertionError("Expected closed owner refusal");}
        catch(IllegalStateException expected){require(expected.getMessage().contains("Open owning"),"settled owner cannot authorize continuation");}
        require(!q.hasPendingTransactions(),"ownership refusal restores reservation");
    }
    private static void require(boolean value,String message){++checks;if(!value)throw new AssertionError(message);}
}
