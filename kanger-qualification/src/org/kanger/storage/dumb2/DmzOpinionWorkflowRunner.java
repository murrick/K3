/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.command.*;
import org.kanger.interfaces.*;

/** User output, exact detached proofs, protected TRUST components and real query routing. */
public final class DmzOpinionWorkflowRunner {
    private static int checks;
    private interface Attempt { void run() throws Exception; }
    public static void main(String[] args) {
        try { run();System.out.println("DMZ_OPINION_WORKFLOW_PASS checks="+checks);System.exit(0); }
        catch(Throwable failure) { failure.printStackTrace();System.exit(1); }
    }
    private static void run() throws Exception {
        Path dir=Files.createTempDirectory("dmz-opinions-");System.setProperty("user.home",dir.toString());
        create(dir,"Q","!authority(1); !@x component(x) -> protected(x);");
        IContextResults.Revision t=create(dir,"T","!component(1);");
        IContextResults.Revision a=create(dir,"A","!left(2); !irrelevant(9); !@x left(x) -> result(x);");
        IContextResults.Revision b=create(dir,"B","!~left(2); !~component(1);");
        IContextResults.Revision plain=create(dir,"PlainB","!~left(2);");
        IContextResults.Revision c=create(dir,"C","!left(2);");
        create(dir,"Empty","");
        create(dir,"all","!@x p(x) -> q(x); !p(1);");
        create(dir,"tree","!marker(7);");
        User user=new User();user.setDatabaseDir(dir.toString()+File.separator);
        user.setUserDir(Files.createDirectories(dir.resolve("operator")).toString());
        DB db=new DB();db.init(user);Mind root=new Mind(user);user.setCurrentMind(root);
        Mind q=(Mind)root.useStorage("Q");user.setCurrentMind(q);
        try {
            db.connectContext("all");db.connectContext("tree");
            CanonicalCommandProcessor inspection=new CanonicalCommandProcessor();CommandParser grammar=new CommandParser();
            org.kanger.interfaces.internal.IContextFederation.RuleBlock base=inspection.execute(grammar.parse("ctx ask all rules base"),user).getContextRules().get(0);
            require(base.locator.equals("all") && base.rules.stream().noneMatch(v->v.statement.contains("@x")),"reserved all Context and base assertion filter");
            require(inspection.execute(grammar.parse("ctx ask tree rules"),user).getContextRules().get(0).locator.equals("tree"),"reserved tree Context remains literal");
            long generated=inspection.execute(grammar.parse("ctx ask all rules produced"),user).getContextRules().get(0).rules.get(0).id;
            String inference=inspection.execute(grammar.parse("ctx ask all rules tree "+generated),user).getContextRules().get(0).rules.get(0).tree.toString();
            String structure=inspection.execute(grammar.parse("ctx ask all rules structure "+generated),user).getContextRules().get(0).rules.get(0).tree.toString();
            require(inference.contains("Cause:"),"Context rule tree is actual inference");
            require(!structure.contains("Cause:") && !structure.isEmpty(),"structure retains separate internal layout");
            require(inspection.execute(grammar.parse("ctx ask all rules 0"),user).getContextRules().get(0).rules.get(0).statement.contains("@x"),"ID zero selects all's production");
            require(inspection.execute(grammar.parse("ctx ask tree rules 0"),user).getContextRules().get(0).rules.get(0).statement.contains("marker"),"same ID selects tree's distinct fact");
            for(String unaddressed:new String[]{"ctx rules 0","ctx rules tree 0","ctx rules structure 0","ctx rules comment 0"})
                reject(() -> grammar.parse(unaddressed));
            final Mind inspectionMind=q;
            reject(() -> db.inspectRules(inspectionMind,null,org.kanger.interfaces.internal.IContextFederation.RuleSelection.SHOW,0L));
            db.disconnectContext("all");db.disconnectContext("tree");
            db.connectContext("T","own");
            String state=DmzSourceMaterializationQualification.fingerprint(q);
            IContextResults.Revision qp=pin(db);
            DmzSourcesQuery.Result protectedAnswer=db.executeDmzSources(q,request(qp,"?protected(1);",1000,
                    new DmzSourcePlan.Selection("B",b)));
            require(protectedAnswer.acquisition.authorityComponents.size()==1,"TRUST component acquired as authority");
            require(protectedAnswer.viewOpinions.size()==1 && Boolean.TRUE.equals(protectedAnswer.viewOpinions.get(0).value),"component and Q rule jointly protect derived answer");
            Set<UUID> support=contexts(protectedAnswer.viewOpinions.get(0).proofs);
            require(support.contains(qp.getContextId()) && support.contains(t.getContextId()) && !support.contains(b.getContextId()),"only actual Q/component proof sources retained");
            require(protectedAnswer.opinions.stream().flatMap(o -> o.steps.stream()).anyMatch(s -> s.decision==DmzSourcesQuery.Decision.DECLINED_Q),"external contradiction of component authority declined");
            require(state.equals(DmzSourceMaterializationQualification.fingerprint(q)),"composite Q query preserves physical Q");
            DmzSourcesQuery.Result opposite=db.executeDmzSources(q,request(qp,"?left(2);",1000,
                    new DmzSourcePlan.Selection("A",a),new DmzSourcePlan.Selection("B",b)));
            require(opposite.viewOpinions.size()==2 && Boolean.TRUE.equals(opposite.viewOpinions.get(0).value)
                    && Boolean.FALSE.equals(opposite.viewOpinions.get(1).value),"stable TRUE/FALSE user numbering");
            require(contexts(opposite.viewOpinions.get(0).proofs).equals(Collections.singleton(a.getContextId())),"TRUE caused only by A");
            require(contexts(opposite.viewOpinions.get(1).proofs).equals(Collections.singleton(b.getContextId())),"FALSE caused only by B");
            require(!proofText(opposite.viewOpinions.get(0).proofs).contains("irrelevant"),"unrelated admitted input excluded from causes");
            DmzSourcesQuery.Result derived=db.executeDmzSources(q,request(qp,"?result(2);",1000,new DmzSourcePlan.Selection("A",a)));
            require(derived.viewOpinions.get(0).proofs.stream().anyMatch(p -> !p.premises.isEmpty()
                    && p.source.statement.contains("left(x) -> result(x)") && p.premises.get(0).conclusion.equals("left(2)")),"derived answer retains rule and primary premise tree");
            DmzSourcesQuery.Result alternatives=db.executeDmzSources(q,request(qp,"?left(2);",1000,
                    new DmzSourcePlan.Selection("A",a),new DmzSourcePlan.Selection("C",c)));
            require(alternatives.viewOpinions.size()==1 && alternatives.viewOpinions.get(0).proofs.size()>=2,"equal answer preserves independent proof alternatives");
            require(contexts(alternatives.viewOpinions.get(0).proofs).containsAll(Arrays.asList(a.getContextId(),c.getContextId())),"duplicate primary attribution survives branch closure");
            DmzSourcesQuery.Result unknown=db.executeDmzSources(q,request(qp,"?absent(7);",1000));
            require(unknown.viewOpinions.size()==1 && unknown.viewOpinions.get(0).value==null && unknown.viewOpinions.get(0).proofs.isEmpty(),"UNKNOWN never fabricates a proof");
            DmzSourcesQuery.Result partial=db.executeDmzSources(q,request(qp,"?left(2);",1,new DmzSourcePlan.Selection("A",a)));
            require(partial.restartRequired,"bounded history remains partial");
            try { derived.viewOpinions.get(0).proofs.clear();throw new AssertionError("mutable trees"); }
            catch(UnsupportedOperationException expected) { ++checks; }
            // Default queries use selected physical pins, excluding TRUST members from external branching.
            db.connectContext("A");db.connectContext("PlainB");
            DmzSourcesQuery.Result connected=db.executeConnectedDmz(q,"?left(2);");
            require(connected.acquisition.authorityComponents.size()==1 && connected.acquisition.external.size()==2,"connected request separates Q components from autonomous sources");
            require(connected.viewOpinions.size()==2,"ordinary connected request returns both opinions");
            db.connectContext("Empty");db.applyConnectionCommand(q,"Empty","!private(4);");
            DmzSourcesQuery.Result configured=db.executeConnectedDmz(q,"?private(4);");
            require(Boolean.TRUE.equals(configured.viewOpinions.get(0).value)
                    && configured.acquisition.external.stream().anyMatch(s -> s.locator.equals("Empty") && s.configured),"private initialized external view is explicit proof source");
            db.applyConnectionCommand(q,"T","!component(5);");
            DmzSourcesQuery.Result configuredQ=db.executeConnectedDmz(q,"?protected(5);");
            require(Boolean.TRUE.equals(configuredQ.viewOpinions.get(0).value)
                    && configuredQ.acquisition.authorityComponents.get(0).configured,"private initialized TRUST view belongs to authority");
            String historical=proofText(derived.viewOpinions.get(0).proofs);
            IContextResults.Revision newer=create(dir,"A","!later(8);");
            require(newer.getRevision()>a.getRevision(),"source HEAD advances");
            require(historical.equals(proofText(derived.viewOpinions.get(0).proofs)),"retained proof unaffected by source advance");
            CanonicalCommandProcessor processor=new CanonicalCommandProcessor();CommandParser parser=new CommandParser();CommandFormatter formatter=new CommandFormatter();
            for(String cmd:new String[]{"ctx tree 1","ctx dmz explain"})require(formatter.format(parser.parse(cmd)).equals(cmd),"new command round trip "+cmd);
            reject(() -> parser.parse("ctx tree 0"));reject(() -> parser.parse("ctx opinion 1 other"));
            reject(() -> parser.parse("ctx dmz explain extra"));
            user.setLastDmzResult(derived);
            User detached=new User();detached.setCurrentMind(new Mind(detached));detached.setLastDmzResult(derived);
            require(processor.execute(parser.parse("ctx opinions A tree 1"),detached).getDmzResult()==derived,"saved tree needs no open provider");
            require(processor.execute(parser.parse("ctx op A"),user).getDmzResult()==derived,"source opinions reuse detached result");
            require(processor.execute(parser.parse("ctx op A tree 1"),user).getDmzResult()==derived,"source tree reuses detached result");
            require(DmzOpinionSelection.select(derived,"A",null).size()==1,"A source opinion filter");
            require(DmzOpinionSelection.select(opposite,"B",null).size()==1,"opposite source opinion filter");
            require(DmzOpinionSelection.select(alternatives,"A",null).equals(DmzOpinionSelection.select(alternatives,"C",null)),"one opinion belongs to every supporting source");
            reject(() -> processor.execute(parser.parse("ctx opinions A tree 2"),user));
            reject(() -> processor.execute(parser.parse("ctx opinions Unknown"),user));
            String named="dmz Q@"+db.getRevision()+" ?left(2); from=A@"+a.getRevision()+" from=PlainB@"+plain.getRevision();
            DmzSourcesQuery.Result namedResult=processor.execute(parser.parse(named),user).getDmzResult();
            require(namedResult.acquisition.external.stream().anyMatch(v->v.revision.getContextId().equals(a.getContextId())
                    && v.revision.getRevision()==a.getRevision()),"named DMZ resolves old exact revision after source advances");
            reject(() -> processor.execute(parser.parse("dmz Q@"+db.getRevision()+" ?left(2); from=A@999999"),user));
            user.setLastDmzResult(derived);
            require(processor.execute(parser.parse("ctx tr 1"),user).getDmzResult()==derived,"tree reads detached history without inference");
            require(processor.execute(parser.parse("ctx dmz explain"),user).getDmzResult()==derived,"explain reads same detached result");
            reject(() -> processor.execute(parser.parse("ctx tree 99"),user));
            User other=new User();other.setCurrentMind(new Mind(other));reject(() -> processor.execute(parser.parse("ctx dmz explain"),other));
            q=(Mind)db.publishContext(q,"opinion workflow fixture");user.setCurrentMind(q);
            String beforeScript=DmzSourceShape.inspect(q,10000).primaryDigest;
            String topology=topology(db);
            String output=console(q,"?left(2);\nctx op A tree 1\nctx opinions PlainB tree\nctx dmz ex\n?protected(5);\nctx tree 1\n");
            require(output.contains("Opinion 1: TRUE from A") && output.contains("Opinion 2: FALSE from PlainB"),"real ordinary Console query presents compact source opinions");
            require(output.contains("Cause: left(2)") && output.contains("Cause: ~left(2)"),"real causes reflect answer literals");
            require(output.contains("Use \"ctx opinions <locator> tree [<N>]\" for inference tree."),"compact tree hint");
            require(output.contains("Inference 1:") && output.contains("Rule: !@x component(x) -> protected(x);")
                    && output.contains("[configured by Q]"),"real tree includes applied rule and configured authority provenance");
            int firstTree=output.indexOf("Inference 1:");
            require(!output.substring(0,firstTree).contains("DMZ policy:") && !output.substring(0,firstTree).contains("variant="),"default answer excludes traversal analytics");
            require(output.contains("DMZ policy:") && output.contains("Q component: T"),"explicit explain contains technical composite-Q report");
            q=(Mind)user.getCurrentMind().useStorage("Q");user.setCurrentMind(q);
            require(beforeScript.equals(DmzSourceShape.inspect(q,10000).primaryDigest) && topology.equals(topology(db)),"ordinary script preserves durable Q");
            // A failed new DMZ attempt clears history; reading it cannot accidentally show the previous answer.
            user.setLastDmzResult(derived);
            final Mind active=q;
            String invalid="ctx dmz "+UUID.randomUUID()+"@0 ?left(2);";
            reject(() -> processor.execute(parser.parse(invalid),user));
            require(user.getLastDmzResult()==null,"failed execution clears old history");
            reject(() -> processor.execute(parser.parse("ctx dmz explain"),user));
            require(Boolean.TRUE.equals(active.query("?authority(1);",null,false)),"Core remains usable after refused request");
        } finally { user.setCurrentMind(q.closeStorage()); }
    }
    private static String topology(DB db) {
        List<String> rows=new ArrayList<String>();
        for(ContextConnection c:db.dmzTopology())rows.add(c.getTarget()+":"+c.getTrustGroup()+":"+c.getInitialization());
        Collections.sort(rows);return rows.toString();
    }
    private static String console(Mind q,String script) throws Exception {
        InputStream in=System.in;PrintStream out=System.out,err=System.err;
        ByteArrayOutputStream captured=new ByteArrayOutputStream(),errors=new ByteArrayOutputStream();
        try {
            System.setIn(new ByteArrayInputStream(script.getBytes("UTF-8")));
            System.setOut(new PrintStream(captured,true,"UTF-8"));System.setErr(new PrintStream(errors,true,"UTF-8"));
            CanonicalConsole.session(q,new ShutdownHook(q));
        } finally {System.setIn(in);System.setOut(out);System.setErr(err);}
        if(errors.size()!=0)throw new AssertionError(errors.toString("UTF-8"));
        return captured.toString("UTF-8");
    }
    private static DmzSourcesQuery.Request request(IContextResults.Revision q,String query,int visits,DmzSourcePlan.Selection... sources) {
        return new DmzSourcesQuery.Request(new DmzSourcePlan.Request(q,Arrays.asList(sources),10000),
                DmzSourcesQuery.Policy.FIXED_UNITS_THEN_ORDERED_GROUNDS_CONTEXT_UUID_RULE_ID,query,
                new DmzSourcesQuery.Limits(10000,10000,10000,10000,10000,10000,10000,8,128,1000000,visits));
    }
    private static IContextResults.Revision pin(DB db) {return new IContextResults.Revision(db.getContextId(),db.getRevision());}
    private static IContextResults.Revision create(Path dir,String name,String source) throws Exception {
        User user=new User();user.setDatabaseDir(dir.toString()+File.separator);DB db=new DB();db.init(user);
        Mind root=new Mind(user);user.setCurrentMind(root);Mind m=(Mind)root.useStorage(name);user.setCurrentMind(m);
        try {if(!source.isEmpty())require(m.compile(source,null,false),"fixture "+name);}finally {user.setCurrentMind(m.closeStorage());}
        try(ContextSnapshot snapshot=ContextSnapshot.open(dir.resolve(name))) {return new IContextResults.Revision(snapshot.getContextId(),snapshot.getRevision());}
    }
    private static Set<UUID> contexts(List<DmzSourcesQuery.Proof> proofs) {
        Set<UUID> result=new LinkedHashSet<UUID>();for(DmzSourcesQuery.Proof p:proofs) {result.add(p.source.revision.getContextId());result.addAll(contexts(p.premises));}return result;
    }
    private static String proofText(List<DmzSourcesQuery.Proof> proofs) {
        StringBuilder text=new StringBuilder();for(DmzSourcesQuery.Proof p:proofs)text.append(p.identity()).append(p.source.statement).append(proofText(p.premises));return text.toString();
    }
    private static void reject(Attempt attempt) throws Exception {try {attempt.run();throw new AssertionError("Expected refusal");}catch(Exception expected){++checks;}}
    private static void require(boolean value,String reason) {++checks;if(!value)throw new AssertionError(reason);}
}
