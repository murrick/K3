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
import org.kanger.interfaces.DmzConditionalResult;
import org.kanger.interfaces.DmzSourcePlan;
import org.kanger.interfaces.IContextResults;

/** External unit origins and exact application gates inside independent owned trials. */
public final class DmzUnitSourceSessionRunner {
    private static int checks;
    private interface Attempt { void run() throws Exception; }
    public static void main(String[] args) {
        try {
            for(boolean negative:new boolean[]{false,true})
                for(boolean independent:new boolean[]{false,true})
                    for(boolean alias:new boolean[]{false,true})run(negative,independent,alias);
            System.out.println("DMZ_UNIT_SOURCE_SESSION_PASS checks="+checks); System.exit(0);
        } catch(Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
    private static DmzCandidateSession.Limits limits(int nodes) {
        return new DmzCandidateSession.Limits(10000,10000,10000,10000,10000,10000,10000,4,nodes,1000000);
    }
    private static void run(boolean negative,boolean independent,boolean alias) throws Exception {
        Path directory=Files.createTempDirectory("dmz-unit-source-"); System.setProperty("user.home",directory.toString());
        String last="!@x middle(x) -> "+(negative ? "~" : "")+"derived(x);";
        create(directory,"Q","!authority(1); !keep(1); !@x keeper(x) -> safe(x);");
        IContextResults.Revision a=create(directory,"A","!@x source(x) -> middle(x); "+last+" !source(2); !source(3);");
        String candidate=negative ? "!derived(2);" : "!~derived(2);";
        IContextResults.Revision b=create(directory,"B",candidate);
        String independentRule="!@x alternative(x) -> "+(negative ? "~" : "")+"derived(x);";
        IContextResults.Revision c=create(directory,"C",independentRule+" !alternative(2);");
        String aliasRule="!@y middle(y) -> "+(negative ? "~" : "")+"derived(y);";
        String qAlias="!@y keeper(y) -> safe(y);";
        IContextResults.Revision same=create(directory,"Alias",aliasRule+" "+qAlias);
        String badRule="!@x authority(x) -> ~keep(x);";
        IContextResults.Revision bad=create(directory,"Bad",badRule);
        User user=new User(); user.setDatabaseDir(directory.toString()+java.io.File.separator);
        DB data=new DB(); data.init(user);
        Mind q=new Mind(user); user.setCurrentMind(q); q=(Mind)q.useStorage("Q"); user.setCurrentMind(q);
        try {
            IContextResults.Revision pin=new IContextResults.Revision(data.getContextId(),data.getRevision());
            DmzSourcePlan.Request request=new DmzSourcePlan.Request(pin,Arrays.asList(
                    new DmzSourcePlan.Selection("A",a),new DmzSourcePlan.Selection("B",b),new DmzSourcePlan.Selection("C",c),
                    new DmzSourcePlan.Selection("Alias",same),new DmzSourcePlan.Selection("Bad",bad)),1000);
            DmzSourcePreflight.Inputs inputs=DmzSourcePreflight.acquire(q,"Q",request,directory::resolve);
            DmzCandidateSession.SourceRule selected=rule(inputs,b,candidate);
            DmzCandidateSession.SourceRule first=rule(inputs,a,"!@x source(x) -> middle(x);");
            DmzCandidateSession.SourceRule second=rule(inputs,a,last);
            DmzCandidateSession.SourceRule source2=rule(inputs,a,"!source(2);");
            DmzCandidateSession.SourceRule source3=rule(inputs,a,"!source(3);");
            List<DmzCandidateSession.SourceRule> productions=new ArrayList<DmzCandidateSession.SourceRule>(Arrays.asList(first,second));
            List<DmzCandidateSession.SourceRule> supports=new ArrayList<DmzCandidateSession.SourceRule>(Arrays.asList(source2,source3));
            if(independent) { productions.add(rule(inputs,c,independentRule)); supports.add(rule(inputs,c,"!alternative(2);")); }
            productions.add(rule(inputs,same,qAlias));
            if(alias)productions.add(rule(inputs,same,aliasRule));
            String state=org.kanger.DmzSourceMaterializationQualification.fingerprint(q);
            DmzCandidateSession.Result retained;
            try(DmzSourceSession session=data.openDmzCandidateSession(q,request,10000)) {
                DmzCandidateSession.Result legacy=session.evaluate(selected,Collections.emptyList(),"?authority(1);",limits(64));
                require(legacy.status==DmzCandidateSession.Status.PROBED_NEW_INPUT && legacy.conditional==null,
                        "acquired external productions are not imported without explicit selection");
                retained=session.evaluateWithProductions(selected,productions,supports,"?authority(1);",limits(64));
                family(retained);
                require(retained.productions.size()==productions.size() && retained.productionRevisions.size()==productions.size(),
                        "detached result identifies each selected production occurrence");
                require(retained.productionRevisions.get(0).getContextId().equals(a.getContextId())
                        && retained.productionRevisions.get(0).getRevision()==a.getRevision(),"production pins remain exact");
                if(alias)require(retained.productionRevisions.get(productions.size()-1).getContextId().equals(same.getContextId()),
                        "alpha-equivalent duplicate keeps its additional physical source occurrence");
                String independentQuery=negative ? "?~derived(3);" : "?derived(3);";
                family(session.evaluateWithProductions(selected,productions,supports,independentQuery,limits(64)));
                require(!Boolean.TRUE.equals(session.queryQ(independentQuery,10000).value),"external rule consequences never become original Q authority");
                DmzCandidateSession.Result stopped=session.evaluateWithProductions(selected,productions,supports,"?authority(1);",limits(1));
                require(stopped.conditional.restartRequired && stopped.conditional.family.outcome==DmzConditionalResult.Stop.NODE_LIMIT,
                        "unit prerequisites preserve coordinated stop/restart semantics");
                family(session.evaluateWithProductions(selected,productions,supports,"?authority(1);",limits(64)));
                reject(() -> session.evaluateWithProductions(selected,Arrays.asList(first,first),supports,"?authority(1);",limits(64)),"Distinct production");
                reject(() -> session.evaluateWithProductions(selected,Collections.singletonList(source2),supports,"?authority(1);",limits(64)),"shared-variable implication");
                reject(() -> session.evaluateWithProductions(selected,productions,Collections.singletonList(first),"?authority(1);",limits(64)),"Distinct support");
                DmzCandidateSession.SourceRule badProduction=rule(inputs,bad,badRule);
                reject(() -> session.evaluateWithProductions(selected,Collections.singletonList(badProduction),Collections.emptyList(),"?authority(1);",limits(64)),null);
                require(Boolean.TRUE.equals(session.queryQ("?keep(1);",10000).value),"refused prerequisite cannot weaken Q and leaves session usable");
                require(state.equals(org.kanger.DmzSourceMaterializationQualification.fingerprint(q))
                        && user.getCurrentMind()==q && data.getRevision()==pin.getRevision() && !data.hasConnectedContexts(),
                        "unit replay, branching and refusal leave physical Q and topology unchanged");
                try { retained.productions.clear(); throw new AssertionError("mutable production observations"); }
                catch(UnsupportedOperationException expected) { ++checks; }
            }
            family(retained);
            try(DmzSourceSession fresh=data.openDmzCandidateSession(q,request,10000)) {
                require(!Boolean.TRUE.equals(fresh.queryQ(negative ? "?~derived(3);" : "?derived(3);",10000).value),
                        "fresh owner retains no external productions or consequences");
            }
        } finally { user.setCurrentMind(q.closeStorage()); }
    }
    private static void family(DmzCandidateSession.Result result) {
        require(result.status==DmzCandidateSession.Status.CONDITIONAL_RESULT && result.conditional!=null
                && result.conditional.family!=null && result.conditional.family.outcome==DmzConditionalResult.Stop.COMPLETE
                && result.conditional.family.opinions.size()>=2 && !result.conditional.restartRequired,"complete conditional family with external units");
        for(DmzConditionalResult.Opinion opinion:result.conditional.family.opinions)
            require(Boolean.TRUE.equals(opinion.answer.value),"exact conflicting substitution cut retains queried Q or independent other-argument consequence");
    }
    private static DmzCandidateSession.SourceRule rule(DmzSourcePreflight.Inputs inputs,IContextResults.Revision pin,String statement) {
        for(int i=0;i<inputs.external.size();++i)if(inputs.plan.external.get(i).revision.getContextId().equals(pin.getContextId()))
            for(DmzSourceShape.Declaration input:inputs.external.get(i).declarations)
                if(input.statement.equals(statement))return new DmzCandidateSession.SourceRule(pin.getContextId(),input.sourceRule);
        throw new AssertionError("Missing fixture declaration: "+statement);
    }
    private static IContextResults.Revision create(Path directory,String name,String statements) throws Exception {
        User user=new User(); user.setDatabaseDir(directory.toString()+java.io.File.separator);
        DB data=new DB(); data.init(user);
        Mind mind=new Mind(user); user.setCurrentMind(mind); mind=(Mind)mind.useStorage(name); user.setCurrentMind(mind);
        try { require(mind.compile(statements,null,false),"persistent unit fixture "+name); }
        finally { user.setCurrentMind(mind.closeStorage()); }
        try(ContextSnapshot snapshot=ContextSnapshot.open(directory.resolve(name))) {
            return new IContextResults.Revision(snapshot.getContextId(),snapshot.getRevision());
        }
    }
    private static void reject(Attempt attempt,String fragment) throws Exception {
        boolean refused=false;
        try { attempt.run(); }catch(Exception expected) { if(fragment!=null && !expected.toString().contains(fragment))throw expected; refused=true; }
        require(refused,"explicit prerequisite refusal: "+fragment);
    }
    private static void require(boolean value,String reason) { ++checks; if(!value)throw new AssertionError(reason); }
}
