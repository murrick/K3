/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.DmzCandidateSession;
import org.kanger.DmzSourceShape;
import org.kanger.interfaces.DmzSourcePlan;
import org.kanger.interfaces.IContextResults;

/** Ordered sourced inputs continue while their exact predecessor branches remain live. */
public final class DmzSourceSequenceRunner {
    private static int checks;
    private interface Attempt { void run() throws Exception; }
    public static void main(String[] args) {
        try {
            for(boolean negative:new boolean[]{false,true})
                for(boolean alternative:new boolean[]{false,true})run(negative,alternative);
            System.out.println("DMZ_SOURCE_SEQUENCE_PASS checks="+checks);System.exit(0);
        } catch(Throwable failure) { failure.printStackTrace();System.exit(1); }
    }
    private static DmzCandidateSession.Limits limits(int nodes) {
        return new DmzCandidateSession.Limits(10000,10000,10000,10000,10000,10000,10000,8,nodes,1000000);
    }
    private static void run(boolean negative,boolean alternative) throws Exception {
        Path directory=Files.createTempDirectory("dmz-source-sequence-");System.setProperty("user.home",directory.toString());
        create(directory,"Q","!authority(1); !keep(1);");
        String consequence=(negative ? "~" : "")+"derived";
        String opposite=(negative ? "" : "~")+"derived";
        String first="!@x source(x) -> middle(x);",second="!@x middle(x) -> "+consequence+"(x);";
        IContextResults.Revision a=create(directory,"A",first+" "+second+" !left(2); !left(3); !source(2); !source(3); !new(4);");
        String bad="!@x trigger(x) -> ~keep(x);";
        IContextResults.Revision b=create(directory,"B","!~left(2); !"+opposite+"(2); !~keep(1); !trigger(1); "+bad);
        String route="!@x alternative(x) -> "+consequence+"(x);";
        IContextResults.Revision c=create(directory,"C","!left(2); !alternative(2); !source(2); !authority(1); !"+consequence+"(2); "+route);
        User user=new User();user.setDatabaseDir(directory.toString()+java.io.File.separator);
        DB data=new DB();data.init(user);Mind q=new Mind(user);user.setCurrentMind(q);q=(Mind)q.useStorage("Q");user.setCurrentMind(q);
        try {
            IContextResults.Revision pin=new IContextResults.Revision(data.getContextId(),data.getRevision());
            DmzSourcePlan.Request request=new DmzSourcePlan.Request(pin,Arrays.asList(new DmzSourcePlan.Selection("A",a),
                    new DmzSourcePlan.Selection("B",b),new DmzSourcePlan.Selection("C",c)),1000);
            DmzSourcePreflight.Inputs acquired=DmzSourcePreflight.acquire(q,"Q",request,directory::resolve);
            List<DmzCandidateSession.SourceRule> units=new ArrayList<DmzCandidateSession.SourceRule>(Arrays.asList(rule(acquired,a,first),rule(acquired,a,second),rule(acquired,b,bad)));
            List<DmzCandidateSession.SourceRule> ordered=new ArrayList<DmzCandidateSession.SourceRule>(Arrays.asList(
                    rule(acquired,a,"!left(2);"),rule(acquired,b,"!~left(2);"),rule(acquired,c,"!left(2);"),rule(acquired,a,"!source(2);")));
            if(alternative) { units.add(rule(acquired,c,route));ordered.add(rule(acquired,c,"!alternative(2);")); }
            ordered.addAll(Arrays.asList(rule(acquired,b,"!"+opposite+"(2);"),rule(acquired,a,"!source(3);"),
                    rule(acquired,b,"!~keep(1);"),rule(acquired,b,"!trigger(1);"),rule(acquired,a,"!new(4);")));
            String state=org.kanger.DmzSourceMaterializationQualification.fingerprint(q);
            DmzCandidateSession.SequenceResult retained;
            try(DmzSourceSession session=data.openDmzCandidateSession(q,request,10000)) {
                retained=session.evaluateSequence(ordered,units,Collections.emptyList(),"?authority(1);",limits(128),1000);
                family(retained,ordered.size());
                boolean keepOld=false,keepNew=false,reused=false;
                for(DmzCandidateSession.SequenceOpinion opinion:retained.opinions) {
                    keepOld|=opinion.steps.get(1).decision==DmzCandidateSession.SequenceDecision.KEEP_EXISTING;
                    keepNew|=opinion.steps.get(1).decision==DmzCandidateSession.SequenceDecision.KEEP_CANDIDATE;
                    reused|=opinion.steps.get(2).decision==DmzCandidateSession.SequenceDecision.REUSED_PRIMARY;
                    require(opinion.steps.get(ordered.size()-3).decision==DmzCandidateSession.SequenceDecision.DECLINED_Q,"original Q rejects its opposing direct input after external branches");
                    DmzCandidateSession.SequenceDecision triggerDecision=opinion.steps.get(ordered.size()-2).decision;
                    require(triggerDecision==DmzCandidateSession.SequenceDecision.DECLINED_Q
                            || triggerDecision==DmzCandidateSession.SequenceDecision.KEEP_EXISTING
                            || triggerDecision==DmzCandidateSession.SequenceDecision.KEEP_CANDIDATE,
                            "Q-related external production conflict requires rejection or an exact external support choice");
                    require(opinion.steps.get(ordered.size()-1).decision==DmzCandidateSession.SequenceDecision.ADMITTED,"subsequent fresh input reaches every retained branch");
                }
                require(keepOld && keepNew && reused,"both initial sides and additional primary occurrence survive ordered continuation");
                require("!left(2);".equals(retained.candidateStatements.get(0)) && first.equals(retained.productionStatements.get(0)),
                        "detached result preserves acquired statements alongside exact source IDs");
                require(retained.candidateRevisions.size()==ordered.size() && retained.productionRevisions.size()==units.size()
                        && retained.authorityRevision.getContextId().equals(pin.getContextId()),"detached ordered selections retain exact source pins");
                family(session.evaluateSequence(ordered,units,Collections.emptyList(),"?"+consequence+"(3);",limits(128),1000),ordered.size());
                family(session.evaluateSequence(ordered,units,Collections.emptyList(),"?new(4);",limits(128),1000),ordered.size());
                for(String branchQuery:new String[]{"?left(2);","?~left(2);","?"+consequence+"(2);","?"+opposite+"(2);"}) {
                    DmzCandidateSession.SequenceResult split=session.evaluateSequence(ordered,units,Collections.emptyList(),branchQuery,limits(128),1000);
                    boolean yes=false,other=false;
                    require(!split.restartRequired,"branch answer split completes");
                    for(DmzCandidateSession.SequenceOpinion opinion:split.opinions) {
                        yes|=Boolean.TRUE.equals(opinion.answer.value);other|=!Boolean.TRUE.equals(opinion.answer.value);
                    }
                    require(yes && other,"different retained alternatives answer differently: "+branchQuery);
                }
                DmzCandidateSession.SequenceResult qAlias=session.evaluateSequence(Collections.singletonList(rule(acquired,c,"!authority(1);")),
                        Collections.emptyList(),Collections.emptyList(),"?authority(1);",limits(128),1000);
                require(!qAlias.restartRequired && qAlias.opinions.size()==1 && Boolean.TRUE.equals(qAlias.opinions.get(0).answer.value)
                        && qAlias.opinions.get(0).steps.get(0).decision==DmzCandidateSession.SequenceDecision.REUSED_PRIMARY,
                        "additional external primary alias retains original Q authority");
                List<DmzCandidateSession.SourceRule> reversed=Arrays.asList(rule(acquired,b,"!"+opposite+"(2);"),
                        rule(acquired,a,"!source(2);"),rule(acquired,a,"!source(3);"));
                family(session.evaluateSequence(reversed,units,Collections.emptyList(),"?"+consequence+"(3);",limits(128),1000),reversed.size());
                List<DmzCandidateSession.SourceRule> promoted=Arrays.asList(rule(acquired,a,"!source(2);"),rule(acquired,c,"!"+consequence+"(2);"));
                DmzCandidateSession.SequenceResult promotion=session.evaluateSequence(promoted,units,Collections.emptyList(),"?"+consequence+"(2);",limits(128),1000);
                require(!promotion.restartRequired && promotion.opinions.size()==1 && Boolean.TRUE.equals(promotion.opinions.get(0).answer.value)
                        && promotion.opinions.get(0).steps.get(1).decision==DmzCandidateSession.SequenceDecision.ADMITTED,
                        "supported derived ground can acquire its explicit primary occurrence in the next live input");
                List<DmzCandidateSession.SourceRule> revived=Arrays.asList(rule(acquired,a,"!source(2);"),
                        rule(acquired,b,"!"+opposite+"(2);"),rule(acquired,c,"!source(2);"),rule(acquired,a,"!source(3);"));
                family(session.evaluateSequence(revived,units,Collections.emptyList(),"?"+consequence+"(3);",limits(128),1000),revived.size());
                for(String branchQuery:new String[]{"?"+consequence+"(2);","?"+opposite+"(2);"}) {
                    DmzCandidateSession.SequenceResult split=session.evaluateSequence(revived,units,Collections.emptyList(),branchQuery,limits(128),1000);
                    boolean yes=false,other=false;
                    require(!split.restartRequired,"new primary alias after a prior exact cut is audited completely");
                    for(DmzCandidateSession.SequenceOpinion opinion:split.opinions) {
                        yes|=Boolean.TRUE.equals(opinion.answer.value);other|=!Boolean.TRUE.equals(opinion.answer.value);
                    }
                    require(yes && other,"revived source aliases retain both consequence alternatives: "+branchQuery);
                }
                DmzCandidateSession.SequenceResult limited=session.evaluateSequence(ordered,units,Collections.emptyList(),"?authority(1);",limits(128),2);
                require(limited.visitLimit && limited.restartRequired && limited.visits==2 && limited.interruptedInput>=0,"global ordered visit stop records interruption and requires fresh invocation");
                DmzCandidateSession.SequenceResult partial=session.evaluateSequence(ordered,units,Collections.emptyList(),"?authority(1);",limits(128),ordered.size()+2);
                require(partial.visitLimit && partial.restartRequired && !partial.opinions.isEmpty()
                        && partial.opinions.get(0).steps.size()==ordered.size(),
                        "completed leaves before an interrupted sibling remain explicitly partial");
                DmzCandidateSession.SequenceResult nodeStop=session.evaluateSequence(ordered,units,Collections.emptyList(),"?authority(1);",limits(1),1000);
                require(nodeStop.restartRequired && !nodeStop.events.isEmpty(),"conditional node stop propagates to ordered result");
                family(session.evaluateSequence(ordered,units,Collections.emptyList(),"?authority(1);",limits(128),1000),ordered.size());
                reject(() -> session.evaluateSequence(Arrays.asList(ordered.get(0),ordered.get(0)),units,Collections.emptyList(),"?authority(1);",limits(128),1000),"Distinct ordered");
                reject(() -> session.evaluateSequence(ordered,units,Collections.singletonList(ordered.get(0)),"?authority(1);",limits(128),1000),"Distinct prerequisite");
                require(!Boolean.TRUE.equals(session.queryQ("?new(4);",10000).value),"external accepted prefix never becomes Q authority");
                require(state.equals(org.kanger.DmzSourceMaterializationQualification.fingerprint(q)) && user.getCurrentMind()==q
                        && data.getRevision()==pin.getRevision() && !data.hasConnectedContexts(),"sequence, stops and refusals preserve physical Q and topology");
                try { retained.candidateStatements.clear();throw new AssertionError("mutable source text"); }catch(UnsupportedOperationException expected) { ++checks; }
                try { retained.opinions.clear();throw new AssertionError("mutable sequence"); }catch(UnsupportedOperationException expected) { ++checks; }
                try { retained.opinions.get(0).steps.clear();throw new AssertionError("mutable decisions"); }catch(UnsupportedOperationException expected) { ++checks; }
            }
            family(retained,ordered.size());
            try(DmzSourceSession fresh=data.openDmzCandidateSession(q,request,10000)) {
                require(!Boolean.TRUE.equals(fresh.queryQ("?new(4);",10000).value),"fresh sequence owner retains no prior input");
            }
        } finally { user.setCurrentMind(q.closeStorage()); }
    }
    private static void family(DmzCandidateSession.SequenceResult result,int size) {
        require(!result.restartRequired && !result.visitLimit && result.opinions.size()>=2,"complete ordered branch traversal");
        for(DmzCandidateSession.SequenceOpinion opinion:result.opinions)require(opinion.steps.size()==size && Boolean.TRUE.equals(opinion.answer.value),"each completed opinion contains all ordered decisions and the requested answer");
    }
    private static DmzCandidateSession.SourceRule rule(DmzSourcePreflight.Inputs acquired,IContextResults.Revision pin,String statement) {
        for(int n=0;n<acquired.external.size();++n)if(acquired.plan.external.get(n).revision.getContextId().equals(pin.getContextId()))
            for(DmzSourceShape.Declaration input:acquired.external.get(n).declarations)if(statement.equals(input.statement))
                return new DmzCandidateSession.SourceRule(pin.getContextId(),input.sourceRule);
        throw new AssertionError("Missing acquired input "+statement);
    }
    private static IContextResults.Revision create(Path directory,String name,String statements) throws Exception {
        User user=new User();user.setDatabaseDir(directory.toString()+java.io.File.separator);DB data=new DB();data.init(user);
        Mind mind=new Mind(user);user.setCurrentMind(mind);mind=(Mind)mind.useStorage(name);user.setCurrentMind(mind);
        try { require(mind.compile(statements,null,false),"source fixture "+name); }finally { user.setCurrentMind(mind.closeStorage()); }
        try(ContextSnapshot snapshot=ContextSnapshot.open(directory.resolve(name))) { return new IContextResults.Revision(snapshot.getContextId(),snapshot.getRevision()); }
    }
    private static void reject(Attempt attempt,String fragment) throws Exception {
        boolean rejected=false;try { attempt.run(); }catch(Exception expected) { if(!expected.toString().contains(fragment))throw expected;rejected=true; }
        require(rejected,"explicit sequence refusal "+fragment);
    }
    private static void require(boolean value,String reason) { ++checks;if(!value)throw new AssertionError(reason); }
}
