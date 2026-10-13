/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import org.kanger.Mind;
import org.kanger.User;
import org.kanger.CanonicalConsole;
import org.kanger.CanonicalCommandProcessor;
import org.kanger.ShutdownHook;
import org.kanger.DmzSourceMaterializationQualification;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.kanger.command.CommandFormatter;
import org.kanger.command.CommandIntent;
import org.kanger.command.CommandInvocation;
import org.kanger.command.CommandParseException;
import org.kanger.command.CommandParser;
import org.kanger.interfaces.DmzSourcesQuery;
import org.kanger.interfaces.IContextResults;
import org.kanger.storage.dumb2.DB;

/** Real Console scripts and shared canonical dispatch over exact-pinned DUMB2 sources. */
public final class DmzConsoleWorkflowRunner {
    private static int checks;
    private interface Attempt { void run() throws Exception; }
    private static final CommandParser PARSER=new CommandParser();
    private static final CommandFormatter FORMATTER=new CommandFormatter();
    public static void main(String[] args) {
        try { run();restrictions(false);restrictions(true);staleAuthority();lateDiagnostic(false);lateDiagnostic(true);
            answerDiagnostic(false,1,Boolean.TRUE);answerDiagnostic(true,1,Boolean.TRUE);
            for(boolean negative:new boolean[]{false,true})for(Boolean truth:new Boolean[]{Boolean.TRUE,Boolean.FALSE,null})answerDiagnostic(negative,3,truth);
            System.out.println("DMZ_CONSOLE_WORKFLOW_PASS checks="+checks);System.exit(0); }
        catch(Throwable failure) { failure.printStackTrace();System.exit(1); }
    }
    private static String pin(IContextResults.Revision p) { return p.getContextId()+"@"+p.getRevision(); }
    private static void run() throws Exception {
        Path dir=Files.createTempDirectory("dmz-console-");System.setProperty("user.home",dir.toString());
        create(dir,"Q","!authority(1);");
        IContextResults.Revision a=create(dir,"A","!left(2); !source(3); !@x source(x) -> result(x);");
        IContextResults.Revision b=create(dir,"B","!~left(2); !~authority(1);");
        IContextResults.Revision decimal=create(dir,"Decimal","!fraction(1.0);");
        IContextResults.Revision spaced=create(dir,"Named Empty","");
        IContextResults.Revision unit=create(dir,"Unit","!@x authority(x) -> more(x);");
        IContextResults.Revision badUnit=create(dir,"BadUnit","!@x authority(x) -> ~authority(x);");
        User user=new User();user.setUserDir(Files.createDirectories(dir.resolve("operator")).toString());
        user.setDatabaseDir(dir.toString()+java.io.File.separator);DB db=new DB();db.init(user);
        Mind initial=new Mind(user);user.setCurrentMind(initial);final Mind q=(Mind)initial.useStorage("Q");user.setCurrentMind(q);
        IContextResults.Revision qp=new IContextResults.Revision(db.getContextId(),db.getRevision());
        String base="ctx dmz "+pin(qp)+" \"?left(2);\" from=A@"+pin(a)+" from=B@"+pin(b);
        String state=DmzSourceMaterializationQualification.fingerprint(q);
        CanonicalCommandProcessor processor=new CanonicalCommandProcessor();
        CommandInvocation invocation=PARSER.parse(base);
        require(invocation.getIntent()==CommandIntent.CTX_DMZ && processor.handles(invocation),"shared explicit DMZ intent");
        DmzSourcesQuery.Request request=(DmzSourcesQuery.Request)invocation.getArgument("request");
        require(request.sources.external.size()==2 && request.sources.expectedQ.getRevision()==qp.getRevision()
                && request.limits.maximumVisits==1000 && request.limits.nodes==128,"pins and bounded default profile normalized");
        CanonicalCommandProcessor.Result result=processor.execute(invocation,user);
        require(result.isHandled() && result.isSuccess() && result.getMind()==q && result.getDmzResult()!=null,"shared dispatcher returns detached public result");
        require(!result.getDmzResult().restartRequired && result.getDmzResult().opinions.size()>=2,"full opposite-source traversal through canonical command");
        String quoted="ctx dmz "+pin(qp)+" \"?authority(1);\" \"from=Named Empty@"+pin(spaced)+"\" rows=999 preparation=9000 audit=8000 lookup=7000 recognition=6000 projection=5000 application=4000 query=3000 depth=7 nodes=127 work=900000 visits=999";
        CommandInvocation custom=PARSER.parse(quoted);String echo=FORMATTER.format(custom);
        DmzSourcesQuery.Request normalized=(DmzSourcesQuery.Request)PARSER.parse(echo).getArgument("request");
        require("Named Empty".equals(normalized.sources.external.get(0).locator) && normalized.limits.audit==8000
                && normalized.limits.depth==7 && normalized.limits.maximumVisits==999 && normalized.sources.maximumRows==999,"quoted name and all explicit limits survive canonical round trip");
        require(echo.equals(FORMATTER.format(PARSER.parse(echo))),"canonical DMZ echo is stable");
        require(PARSER.parse("ctx dis A").getIntent()==CommandIntent.CTX_DISCONNECT,"unique disconnect prefix remains bound");
        String[] malformed={"ctx dmz","ctx dmz "+pin(qp),"ctx dmz HEAD ?p(1);",
                "ctx dmz "+qp.getContextId()+"@-1 ?p(1);",base+" from=A@"+pin(a),base+" from=../A@"+pin(a),
                base+" from=Q@"+pin(qp),base+" from=A@HEAD",base+" nodes=0",base+" nodes=10001",base+" work=1000001",
                base+" depth=17",base+" visits=0",base+" rows=10001",base+" audit=-1",base+" visits=2 visits=3",base+" unknown=3",base+" 7",
                base+" audit=99999999999999999999999","ctx dmz "+qp.getContextId()+"@99999999999999999999999 ?p(1);"};
        for(String invalid:malformed)reject(() -> PARSER.parse(invalid),CommandParseException.class);
        String syntax=new org.kanger.command.CommandHelpRenderer().render();
        require(syntax.contains("dmz <name>@<revision>") && syntax.contains("from=<name>@<revision>"),"shared help includes explicit operation grammar");
        reject(() -> processor.execute(PARSER.parse("ctx dmz "+pin(qp)+" ?authority(1); from=BadUnit@"+pin(badUnit)),user),Exception.class);
        require(state.equals(DmzSourceMaterializationQualification.fingerprint(q)) && user.getCurrentMind()==q && !db.hasConnectedContexts(),"command phase failure closes private owner and preserves Q");
        String oldAnswer="ctx dmz "+pin(qp)+" ?result(3); from=A@"+pin(a);
        IContextResults.Revision advanced=create(dir,"A","!later(9);");
        require(advanced.getRevision()>a.getRevision(),"source head advances before Console invocation");
        String script="ctx version Q\nctx version A\n"+base+"\nctx tree 1\nctx tree 2\nctx dmz explain\n"+base+" visits=2\nctx dmz explain\n"+base+" work=1\nctx dmz explain\n"
                +"ctx dmz "+pin(qp)+" ?authority(1); from=Decimal@"+pin(decimal)+"\n"
                +"ctx dmz "+pin(qp)+" ?authority(1); from=Missing@"+pin(a)+"\n"
                +"ctx dmz "+pin(qp)+" ?authority(1); from=BadUnit@"+pin(badUnit)+"\n"
                +"ctx dmz "+qp.getContextId()+"@"+(qp.getRevision()+1)+" ?authority(1);\n"
                +oldAnswer+"\nctx dmz explain\n"+"ctx dmz "+pin(qp)+" ?more(1); from=Unit@"+pin(unit)+"\nctx dmz explain\n"
                +"ctx dmz "+pin(qp)+" ?authority(1);\n"+"ctx dmz "+pin(qp)+" ?authority(1); visits=0\n"
                +"?authority(1);\n";
        InputStream originalIn=System.in;PrintStream originalOut=System.out,originalErr=System.err;
        ByteArrayOutputStream stdout=new ByteArrayOutputStream(),stderr=new ByteArrayOutputStream();
        ShutdownHook hook=new ShutdownHook(q);
        try {
            System.setIn(new ByteArrayInputStream(script.getBytes("UTF-8")));
            System.setOut(new PrintStream(stdout,true,"UTF-8"));System.setErr(new PrintStream(stderr,true,"UTF-8"));
            CanonicalConsole.session(q,hook);
        } finally { System.setIn(originalIn);System.setOut(originalOut);System.setErr(originalErr); }
        String out=stdout.toString("UTF-8"),err=stderr.toString("UTF-8");
        Files.write(dir.resolve("console-out.txt"),out.getBytes("UTF-8"));Files.write(dir.resolve("console-err.txt"),err.getBytes("UTF-8"));
        // Preserve the real terminal transcript in CI artifacts if a presentation check fails.
        System.out.println("DMZ_CONSOLE_STDOUT_BEGIN\n"+out+"\nDMZ_CONSOLE_STDOUT_END");
        System.out.println("DMZ_CONSOLE_STDERR_BEGIN\n"+err+"\nDMZ_CONSOLE_STDERR_END");
        require(out.contains("UUID: "+qp.getContextId()) && out.contains("UUID: "+a.getContextId()),"read-only history provides exact Q and unconnected source UUIDs for operator selection");
        require(out.contains("DMZ policy:") && out.contains("Q: Q "+pin(qp)),"real session displays applied policy and physical authority pin");
        require(out.contains("Source: A "+pin(a)) && out.contains("Fixed productions: 1") && out.contains("Ordered grounds: 4"),"display retains historical exact source and every input selection");
        require(out.contains("Opinion 1:") && out.contains(": TRUE") && out.contains(": FALSE") && out.contains("DECLINED_Q"),"opposing branch answers and Q decisions displayed separately");
        require(out.contains("DMZ traversal: PARTIAL") && out.contains("Completed leaves below are partial historical observations")
                && out.contains("Interrupted input:") && out.contains("Conditional event at input") && out.contains("WORK_LIMIT"),"both stop kinds are explicit incomplete histories");
        require(out.contains("visits=2") && out.contains("work=1"),"display shows actual requested limits");
        require(err.contains("DMZ refused: UNSUPPORTED_DECLARATION at "+decimal.getContextId()) && err.contains("DMZ Q revision mismatch"),"typed planning and Q revision refusals readable");
        require(err.contains("DMZ refused:") && err.contains("INVALID_ARGUMENT_SHAPE"),"phase/acquisition and malformed command refusals keep session alive");
        require(!err.contains("DMZ refused: null") && !err.contains("\tat org.kanger") && !err.contains("Unsupported canonical intent"),"expected DMZ failures are readable and routed");
        require(out.contains("Query: ?result(3);") && out.contains("Query: ?more(1);") && out.contains("KANGER III Session closed"),"fresh exact-pinned retry, unit-only execution and graceful session closure");
        require(out.contains("Result: TRUE"),"finite input executes the final Core query before EOF closes the session");
        require(!user.getCurrentMind().isStorageUsed(),"Console shutdown tracks active physical Mind");
        Mind reopen=(Mind)user.getCurrentMind().useStorage("Q");user.setCurrentMind(reopen);
        try {
            require(state.equals(DmzSourceMaterializationQualification.fingerprint(reopen)) && !db.hasConnectedContexts(),"Console entire script leaves Q durable declarations and topology unchanged");
            DmzSourcesQuery.Result after=processor.execute(PARSER.parse("ctx dmz "+db.getContextId()+"@"+db.getRevision()+" ?authority(1);"),user).getDmzResult();
            require(Boolean.TRUE.equals(after.opinions.get(0).answer.value),"fresh owner works after Console storage reopen");
        } finally { user.setCurrentMind(reopen.closeStorage()); }
    }
    private static IContextResults.Revision create(Path dir,String name,String source) throws Exception {
        User user=new User();user.setDatabaseDir(dir.toString()+java.io.File.separator);DB db=new DB();db.init(user);
        Mind mind=new Mind(user);user.setCurrentMind(mind);mind=(Mind)mind.useStorage(name);user.setCurrentMind(mind);
        try { if(!source.isEmpty())require(mind.compile(source,null,false),"fixture "+name); }finally { user.setCurrentMind(mind.closeStorage()); }
        try(org.kanger.storage.dumb2.ContextSnapshot snapshot=org.kanger.storage.dumb2.ContextSnapshot.open(dir.resolve(name))) {
            return new IContextResults.Revision(snapshot.getContextId(),snapshot.getRevision());
        }
    }
    private static void restrictions(boolean negative) throws Exception {
        Path dir=Files.createTempDirectory("dmz-restriction-console-");System.setProperty("user.home",dir.toString());
        String sign=negative?"~":"",opposite=negative?"":"~";
        create(dir,"Q","!seed(1); !"+opposite+"cache(1); !seed(2); !"+opposite+"cache(2);");
        IContextResults.Revision units=create(dir,"Named Units","!@x seed(x) -> "+sign+"cache(x);");
        long rule;
        try(SnapshotMindRuntime snapshot=SnapshotMindRuntime.open(dir.resolve("Named Units"),new RevisionRef(units.getContextId(),units.getRevision()),"trigger")) {
            rule=org.kanger.DmzSourceShape.inspect(snapshot.getMind(),100).declarations.get(0).sourceRule;
        }
        User user=new User();user.setUserDir(Files.createDirectories(dir.resolve("operator")).toString());
        user.setDatabaseDir(dir.toString()+java.io.File.separator);DB db=new DB();db.init(user);
        Mind initial=new Mind(user);user.setCurrentMind(initial);Mind q=(Mind)initial.useStorage("Q");user.setCurrentMind(q);
        IContextResults.Revision qp=new IContextResults.Revision(db.getContextId(),db.getRevision());
        String base="ctx dmz "+pin(qp)+" ?"+sign+"cache(1); \"from=Named Units@"+pin(units)+"\" restrict="+pin(units)+":"+rule+" restriction-budget=9000";
        String named="dmz Q@"+qp.getRevision()+" ?"+sign+"cache(1); \"from=Named Units@"+units.getRevision()+"\" \"restrict=Named Units@"+units.getRevision()+":"+rule+"\" restriction-budget=9000";
        CanonicalCommandProcessor processor=new CanonicalCommandProcessor();
        String state=DmzSourceMaterializationQualification.fingerprint(q);
        try {
            DmzSourcesQuery.Request request=(DmzSourcesQuery.Request)PARSER.parse(base).getArgument("request");
            require(request.restrictions.triggerRule==rule && request.restrictions.budget==9000 && request.restrictions.choices.isEmpty(),
                    "exact command normalizes explicit opt-in trigger and budget");
            org.kanger.interfaces.IMind sdk=q;
            DmzSourcesQuery.Result result=sdk.executeDmzSources(request);
            require(result.restrictionOutcome.status==DmzSourcesQuery.RestrictionStatus.NEXT_CONFLICT && result.opinions.isEmpty(),
                    "public SDK interface returns detached restriction choices");
            String firstIdentity=result.restrictionOutcome.availableChoices.get(0).identity();
            reject(() -> new Mind(new User()).executeDmzSources(request),IllegalStateException.class);
            Mind pending=new Mind(q);
            try { reject(() -> sdk.executeDmzSources(request),IllegalStateException.class); }
            finally { q.release(pending); }
            String echoed=FORMATTER.format(PARSER.parse(named));
            require(echoed.equals(FORMATTER.format(PARSER.parse(echoed))),"named restriction command canonical echo is stable");
            DmzSourcesQuery.Result byName=processor.execute(PARSER.parse(named),user).getDmzResult();
            require(byName.restrictionOutcome.availableChoices.get(0).identity().equals(result.restrictionOutcome.availableChoices.get(0).identity()),
                    "named pins and exact UUID pins expose the same proof identities");
            DmzSourcesQuery.Request next=byName.chooseRestriction(1);
            require(next.sources==byName.request.sources && next.limits==byName.request.limits
                    && next.restrictions.choices.equals(java.util.Collections.singletonList(firstIdentity)),
                    "SDK historical selection builds a fresh request preserving exact pins and limits");
            reject(() -> byName.chooseRestriction(Long.MAX_VALUE),IllegalArgumentException.class);
            reject(() -> processor.execute(PARSER.parse("ctx dmz choose 99999"),user),IllegalArgumentException.class);
            require(user.getLastDmzResult()==byName,"invalid alternative number preserves the saved result for correction");
            String selections="";StringBuilder script=new StringBuilder(base+"\nctx dmz choose 0\nctx dmz choose 99999\nctx dmz explain\n");
            for(int count=0;result.restrictionOutcome.status==DmzSourcesQuery.RestrictionStatus.NEXT_CONFLICT;count++) {
                require(count<3,"bounded command replay");
                selections+=" \"choice="+result.restrictionOutcome.availableChoices.get(0).identity()+"\"";
                String command=base+selections;
                DmzSourcesQuery.Request parsed=(DmzSourcesQuery.Request)PARSER.parse(FORMATTER.format(PARSER.parse(command))).getArgument("request");
                require(parsed.restrictions.choices.size()==count+1 && parsed.restrictions.budget==9000,
                        "full proof identities and ordered prefix survive format/parse");
                result=processor.execute(PARSER.parse(count==0?"dmz choose 1":"ctx dmz choose 1"),user).getDmzResult();
                require(result.request.restrictions.choices.equals(parsed.restrictions.choices),
                        "saved ordinal selection replays the identical whole proof prefix");
                script.append(count==0?"ctx dmz \"cho\" 1 projection=9999\nctx dmz explain\n":"dmz choose 1 visits=3\nctx dmz explain\n");
            }
            require(result.restrictionOutcome.status==DmzSourcesQuery.RestrictionStatus.COMPLETE && result.restrictionProofs.size()==2
                    && Boolean.FALSE.equals(result.opinions.get(0).answer.value),"named command completes both conflicts and queries protected Q fact");
            reject(() -> processor.execute(PARSER.parse("ctx dmz choose 1"),user),IllegalStateException.class);
            require(user.getLastDmzResult()==result,"completed historical result cannot be reused as pending choices");
            String[] invalid={base+" restrict="+pin(units)+":"+rule,base+" restriction-budget=1",base.replace("restriction-budget=9000","restriction-budget=0"),
                    base+" choice=duplicate choice=duplicate",base.replace(":"+rule+" restriction-budget",":-1 restriction-budget"),
                    "ctx dmz "+pin(qp)+" ?seed(1); choice=orphan","ctx dmz "+pin(qp)+" ?seed(1); restriction-budget=100",named+" restrict=Missing@1:0"};
            for(String bad:invalid)reject(() -> PARSER.parse(bad),CommandParseException.class);
            reject(() -> processor.execute(PARSER.parse(base+" choice=foreign-proof"),user),IllegalArgumentException.class);
            require(((User)user).getLastDmzResult()==null,"failed restriction command clears saved historical result");
            reject(() -> processor.execute(PARSER.parse("dmz choose 1"),user),org.kanger.exception.CommandErrorException.class);
            processor.execute(PARSER.parse("ctx dmz "+pin(qp)+" ?seed(1);"),user);
            reject(() -> processor.execute(PARSER.parse("ctx dmz choose 1"),user),IllegalStateException.class);
            processor.execute(PARSER.parse(base+" visits=1"),user);
            processor.execute(PARSER.parse("ctx dmz choose 1"),user);
            DmzSourcesQuery.Result bounded=user.getLastDmzResult();
            reject(() -> processor.execute(PARSER.parse("ctx dmz choose 1"),user),IllegalStateException.class);
            require(user.getLastDmzResult()==bounded,"bounded prefix refusal leaves the historical frontier readable");
            reject(() -> PARSER.parse("ctx dmz choose 1 projection=0"),CommandParseException.class);
            require(user.getLastDmzResult()==bounded,"malformed replacement limit preserves the saved frontier");
            DmzSourcesQuery.Result enlarged=processor.execute(PARSER.parse("dmz choose 1 visits=2 projection=9999"),user).getDmzResult();
            require(enlarged.restrictionOutcome.status==DmzSourcesQuery.RestrictionStatus.COMPLETE
                    && enlarged.request.sources==bounded.request.sources && enlarged.request.limits.maximumVisits==2
                    && enlarged.request.limits.projection==9999 && enlarged.request.limits.audit==bounded.request.limits.audit
                    && enlarged.request.restrictions.budget==9000
                    && enlarged.request.restrictions.choices.subList(0,1).equals(bounded.request.restrictions.choices),
                    "saved choice overrides only explicit limits and extends the exact full prefix beyond the old visit bound");
            processor.execute(PARSER.parse(named),user);
            IContextResults.Revision advanced=create(dir,"Named Units","!later(99);");
            require(advanced.getRevision()>units.getRevision(),"source HEAD advances before saved choice replay");
            DmzSourcesQuery.Result historical=processor.execute(PARSER.parse("ctx dmz choose 1"),user).getDmzResult();
            require(historical.request.restrictions.triggerRevision.getRevision()==units.getRevision(),
                    "saved command retries exact historical pins after source HEAD changes");
            processor.execute(PARSER.parse(base),user);
            Mind occupied=new Mind(q);
            try { reject(() -> processor.execute(PARSER.parse("ctx dmz choose 1"),user),IllegalStateException.class); }
            finally { q.release(occupied); }
            require(user.getLastDmzResult()==null,"executed replay refusal clears saved result and keeps Q unchanged");
            String syntax=new org.kanger.command.CommandHelpRenderer().render();
            require(syntax.contains("restrict=") && syntax.contains("choice=<proof-identity>") && syntax.contains("restriction-budget="),"shared help exposes opt-in grammar");
            script.append(base).append("\nctx dmz choose 1 projection=1\n").append("ctx tree 1\nctx dmz choose 1\n").append(base).append(" choice=foreign-proof\n").append("ctx dmz ").append(pin(qp)).append(" ?seed(1);\n");
            InputStream savedIn=System.in;PrintStream savedOut=System.out,savedErr=System.err;
            ByteArrayOutputStream out=new ByteArrayOutputStream(),err=new ByteArrayOutputStream();
            try {
                System.setIn(new ByteArrayInputStream(script.toString().getBytes("UTF-8")));
                System.setOut(new PrintStream(out,true,"UTF-8"));System.setErr(new PrintStream(err,true,"UTF-8"));
                CanonicalConsole.session(q,new ShutdownHook(q));
            } finally { System.setIn(savedIn);System.setOut(savedOut);System.setErr(savedErr); }
            String display=out.toString("UTF-8");
            System.out.println("DMZ_SAVED_REPLAY_STDOUT_BEGIN\n"+display+"\nDMZ_SAVED_REPLAY_STDOUT_END");
            System.out.println("DMZ_SAVED_REPLAY_STDERR_BEGIN\n"+err.toString("UTF-8")+"\nDMZ_SAVED_REPLAY_STDERR_END");
            require(display.contains("DMZ restrictions: NEXT_CONFLICT; selected=0") && display.contains("Restriction choice 1:")
                    && display.contains("\"choice="+firstIdentity+"\"") && display.contains("Replay the full invocation"),"real console prints copyable exact choices and replay instructions");
            require(display.contains("ctx dmz choose 1"),"console exposes the saved exact-alternative replay command");
            require(display.contains("DMZ restrictions: COMPLETE; selected=2") && display.contains("Restricted source chain 2:")
                    && display.contains("Opinion 1: FALSE") && display.contains("completed restricted native branch"),"console explains restriction outcome and selected proof trees");
            require(display.contains("Restriction step 1: selected alternative 1/1 -> NEXT_CONFLICT")
                    && display.contains("Restriction step 2: selected alternative 1/1 -> COMPLETE"),"saved explanation displays successive historical branch outcomes");
            require(display.contains("Current covered conflict evidence:") && display.contains("Positive evidence:")
                    && display.contains("Negative evidence:") && display.contains("Conflict pair 1:"),
                    "console explains both covered native endpoints for pending and historical conflicts");
            require(err.toString("UTF-8").contains("DMZ refused:") && !err.toString("UTF-8").contains("\tat org.kanger"),
                    "invalid choice remains readable and console continues to ordinary query");
            String diagnostic=null;
            for(String line:err.toString("UTF-8").split("\\R"))if(line.startsWith("Exact replay request: ")) {
                String attempted=line.substring("Exact replay request: ".length());
                DmzSourcesQuery.Request parsed=(DmzSourcesQuery.Request)PARSER.parse(attempted).getArgument("request");
                if(parsed.limits.projection==1) {
                    require(diagnostic==null,"prepared selection projection refusal emits one exact request");
                    diagnostic=attempted;
                }
            }
            require(diagnostic!=null,"Console execution refusal retains a copyable exact attempted request");
            require(err.toString("UTF-8").contains("phase=CONFLICT_EVIDENCE; completedChoices=0; projection=1; restriction-budget=9000; allowance=1"),
                    "Console refusal identifies the initial evidence phase and configured effective cap");
            DmzSourcesQuery.Request refused=(DmzSourcesQuery.Request)PARSER.parse(diagnostic).getArgument("request");
            require(refused.sources.expectedQ.getContextId().equals(qp.getContextId())
                    && refused.sources.expectedQ.getRevision()==qp.getRevision() && refused.limits.projection==1
                    && refused.restrictions.budget==9000 && refused.restrictions.triggerRevision.getContextId().equals(units.getContextId())
                    && refused.restrictions.triggerRevision.getRevision()==units.getRevision() && refused.restrictions.triggerRule==rule
                    && refused.restrictions.choices.equals(java.util.Collections.singletonList(firstIdentity)),
                    "refusal diagnostic preserves exact Q/source trigger, selected prefix and overridden projection");
            require(FORMATTER.format(PARSER.parse(diagnostic)).equals(diagnostic),"refused full request has a stable canonical round trip");
            Mind reopened=(Mind)user.getCurrentMind().useStorage("Q");user.setCurrentMind(reopened);
            require(state.equals(DmzSourceMaterializationQualification.fingerprint(reopened)) && !db.hasConnectedContexts(),
                    "SDK and command successes/refusals preserve durable Q and topology");
            user.setLastDmzResult(null);
            String retry=diagnostic.replace(" projection=1"," projection=3");
            DmzSourcesQuery.Request adjusted=(DmzSourcesQuery.Request)PARSER.parse(retry).getArgument("request");
            require(adjusted.restrictions.choices.equals(refused.restrictions.choices)
                    && adjusted.restrictions.triggerRevision.getContextId().equals(refused.restrictions.triggerRevision.getContextId())
                    && adjusted.restrictions.triggerRevision.getRevision()==refused.restrictions.triggerRevision.getRevision()
                    && adjusted.restrictions.triggerRule==refused.restrictions.triggerRule
                    && adjusted.restrictions.budget==refused.restrictions.budget && adjusted.limits.projection==3,
                    "editing diagnostic projection preserves the complete exact selected prefix and trigger");
            out.reset();err.reset();
            try {
                System.setIn(new ByteArrayInputStream((diagnostic+"\n"+retry+"\nctx dmz explain\nctx dmz choose 1 projection=3\nctx dmz explain\n").getBytes("UTF-8")));
                System.setOut(new PrintStream(out,true,"UTF-8"));System.setErr(new PrintStream(err,true,"UTF-8"));
                CanonicalConsole.session(reopened,new ShutdownHook(reopened));
            } finally { System.setIn(savedIn);System.setOut(savedOut);System.setErr(savedErr); }
            require(err.toString("UTF-8").contains("Detached source proof budget exceeded")
                    && err.toString("UTF-8").contains("Exact replay request: "+diagnostic) && !err.toString("UTF-8").contains("\tat org.kanger"),
                    "unchanged diagnostic still refuses as a fresh initial request without requiring a saved result");
            require(out.toString("UTF-8").contains("DMZ restrictions: NEXT_CONFLICT; selected=1")
                    && out.toString("UTF-8").contains("DMZ restrictions: COMPLETE; selected=2")
                    && out.toString("UTF-8").contains("Opinion 1: FALSE"),
                    "enlarged diagnostic reconstructs its historical prefix and Console completes the remaining conflict");
            DmzSourcesQuery.Result recovered=user.getLastDmzResult();
            require(recovered!=null && recovered.restrictionOutcome.status==DmzSourcesQuery.RestrictionStatus.COMPLETE
                    && recovered.restrictionOutcome.trace.size()==2 && recovered.request.restrictions.choices.get(0).equals(firstIdentity)
                    && recovered.restrictionOutcome.trace.get(0).selected.identity().equals(firstIdentity)
                    && recovered.request.sources.expectedQ.getContextId().equals(qp.getContextId())
                    && recovered.request.sources.expectedQ.getRevision()==qp.getRevision()
                    && recovered.request.sources.external.get(0).revision.getContextId().equals(units.getContextId())
                    && recovered.request.sources.external.get(0).revision.getRevision()==units.getRevision()
                    && recovered.request.limits.projection==3 && refused.limits.projection==1,
                    "fresh Console recovery retains old exact pins after source HEAD advancement and unchanged diagnostic data");
            Mind afterRetry=(Mind)user.getCurrentMind().useStorage("Q");user.setCurrentMind(afterRetry);
            require(state.equals(DmzSourceMaterializationQualification.fingerprint(afterRetry)) && db.getRevision()==qp.getRevision()
                    && !db.hasConnectedContexts(),"diagnostic failures and successful retries preserve durable Q, revision and topology");
            user.setCurrentMind(afterRetry.closeStorage());
        } finally { if(user.getCurrentMind().isStorageUsed())user.setCurrentMind(user.getCurrentMind().closeStorage()); }
    }
    private static void reject(Attempt attempt,Class<? extends Exception> type) throws Exception {
        try { attempt.run();throw new AssertionError("Expected rejection "+type); }
        catch(Exception expected) { if(!type.isInstance(expected))throw expected;++checks; }
    }
    private static void staleAuthority() throws Exception {
        Path dir=Files.createTempDirectory("dmz-saved-q-revision-");System.setProperty("user.home",dir.toString());
        create(dir,"Q","!seed(1); !~cache(1); !seed(2); !~cache(2);");
        IContextResults.Revision units=create(dir,"Rules","!@x seed(x) -> cache(x);");
        long rule;
        try(SnapshotMindRuntime snapshot=SnapshotMindRuntime.open(dir.resolve("Rules"),new RevisionRef(units.getContextId(),units.getRevision()),"trigger")) {
            rule=org.kanger.DmzSourceShape.inspect(snapshot.getMind(),100).declarations.get(0).sourceRule;
        }
        User user=new User();user.setDatabaseDir(dir.toString()+java.io.File.separator);DB db=new DB();db.init(user);
        Mind initial=new Mind(user);user.setCurrentMind(initial);Mind q=(Mind)initial.useStorage("Q");user.setCurrentMind(q);
        CanonicalCommandProcessor processor=new CanonicalCommandProcessor();
        try {
            long revision=db.getRevision();
            user.setUserDir(Files.createDirectories(dir.resolve("operator")).toString());
            String command="ctx dmz "+db.getContextId()+"@"+revision+" ?cache(1); from=Rules@"+pin(units)+" restrict="+pin(units)+":"+rule;
            processor.execute(PARSER.parse(command),user);
            DmzSourcesQuery.Result saved=processor.execute(PARSER.parse("ctx dmz choose 1"),user).getDmzResult();
            String historicalPair=saved.restrictionOutcome.trace.get(0).conflicts.get(0).identity();
            DmzSourcesQuery.Request selected=saved.chooseRestriction(1);
            DmzSourcesQuery.Limits l=selected.limits;
            DmzSourcesQuery.Request adjusted=selected.withLimits(new DmzSourcesQuery.Limits(l.preparation,l.audit,l.lookup,l.recognition,
                    9000,l.application,l.query,l.depth,l.nodes,l.work,99));
            String exactAttempt=FORMATTER.format(CommandInvocation.command(CommandIntent.CTX_DMZ,
                    java.util.Collections.<String,Object>singletonMap("request",adjusted),""));
            String beforeReplacement=DmzSourceMaterializationQualification.fingerprint(q);
            IContextResults.Revision replacement=create(dir,"Replacement","!@x seed(x) -> cache(x);");
            require(!replacement.getContextId().equals(units.getContextId()) && replacement.getRevision()==units.getRevision(),
                    "replacement source has the same declarations and revision number but a different Context identity");
            moveContextFixture(dir,"Rules","Rules-original");
            moveContextFixture(dir,"Replacement","Rules");
            try {
                processor.execute(PARSER.parse("ctx dmz choose 1 projection=9000 visits=99"),user);
                throw new AssertionError("Saved pin followed a foreign source under the same name");
            } catch(IllegalStateException refusal) {
                require(refusal.getMessage().contains("source identity mismatch"),"saved replay rejects source name rebound to a foreign UUID");
            }
            require(user.getLastDmzResult()==null && beforeReplacement.equals(DmzSourceMaterializationQualification.fingerprint(q))
                    && saved.restrictionOutcome.trace.size()==1 && saved.restrictionOutcome.trace.get(0).conflicts.get(0).identity().equals(historicalPair),
                    "source replacement preserves physical Q and detached historical endpoint proofs");
            user.setLastDmzResult(saved);
            String sourceErrors=boundaryConsole(q,"ctx dmz choose 1 projection=9000 visits=99\n"+exactAttempt+"\n");
            requireBoundaryDiagnostics(sourceErrors,exactAttempt,"source identity mismatch");
            q=(Mind)user.getCurrentMind().useStorage("Q");user.setCurrentMind(q);
            require(user.getLastDmzResult()==null && beforeReplacement.equals(DmzSourceMaterializationQualification.fingerprint(q))
                    && db.getRevision()==revision && !db.hasConnectedContexts()
                    && saved.restrictionOutcome.trace.get(0).conflicts.get(0).identity().equals(historicalPair),
                    "saved and fresh source-boundary refusals preserve durable Q and historical evidence");
            moveContextFixture(dir,"Rules","Replacement");
            moveContextFixture(dir,"Rules-original","Rules");
            processor.execute(PARSER.parse(command),user);
            DmzSourcesQuery.Result restored=processor.execute(PARSER.parse("ctx dmz choose 1"),user).getDmzResult();
            require(restored.restrictionOutcome.trace.get(0).selected.identity().equals(saved.restrictionOutcome.trace.get(0).selected.identity()),
                    "restored exact source reproduces the historical branch choice through fresh reconstruction");
            require(q.compile("!later(9);",null,false),"Q advances after saving a restriction frontier");
            user.checkpoint(q);
            require(db.getRevision()>revision,"Q publishes a new immutable revision");
            // Compare reloaded durable Q on both sides; this diagnostic fingerprint also
            // includes live attribution metadata, which is rebuilt when a revision opens.
            user.setCurrentMind(q.closeStorage());
            q=(Mind)user.getCurrentMind().useStorage("Q");user.setCurrentMind(q);
            String advanced=DmzSourceMaterializationQualification.fingerprint(q);
            try {
                processor.execute(PARSER.parse("ctx dmz choose 1 projection=9000 visits=99"),user);
                throw new AssertionError("Saved old Q pin resumed against a new revision");
            } catch(org.kanger.exception.CommandErrorException refusal) {
                require(refusal.toString().contains("Q revision mismatch"),"saved selection must reacquire its original Q pin");
            }
            require(user.getLastDmzResult()==null && advanced.equals(DmzSourceMaterializationQualification.fingerprint(q))
                    && user.getCurrentMind()==q && saved.restrictionOutcome.availableChoices.size()==1,
                    "stale replay refuses without changing advanced Q or old detached alternatives");
            user.setLastDmzResult(restored);
            String qErrors=boundaryConsole(q,"ctx dmz choose 1 projection=9000 visits=99\n"+exactAttempt+"\n");
            requireBoundaryDiagnostics(qErrors,exactAttempt,"Q revision mismatch");
            q=(Mind)user.getCurrentMind().useStorage("Q");user.setCurrentMind(q);
            require(user.getLastDmzResult()==null && advanced.equals(DmzSourceMaterializationQualification.fingerprint(q))
                    && db.getRevision()>revision && !db.hasConnectedContexts()
                    && restored.restrictionOutcome.trace.get(0).conflicts.get(0).identity().equals(historicalPair),
                    "saved and fresh stale-Q diagnostic retries preserve the advanced Q and old endpoint history");
        } finally { user.setCurrentMind(q.closeStorage()); }
    }
    private static void lateDiagnostic(boolean negative) throws Exception {
        Path dir=Files.createTempDirectory("dmz-late-diagnostic-");System.setProperty("user.home",dir.toString());
        String sign=negative?"~":"",opposite=negative?"":"~";
        create(dir,"Q","!seed(1); !deep(2); !"+opposite+"cache(2);");
        IContextResults.Revision sa=create(dir,"ShortA","!@x seed(x) -> "+sign+"cache(x);");
        IContextResults.Revision sb=create(dir,"ShortB","!@x seed(x) -> "+sign+"cache(x);");
        String recursive="!@x deep(x) -> middle(x); !@x middle(x) -> "+sign+"cache(x);";
        IContextResults.Revision la=create(dir,"LongA",recursive),lb=create(dir,"LongB",recursive);
        IContextResults.Revision blocker=create(dir,"Blocker","!"+opposite+"cache(1);");
        long trigger;
        try(SnapshotMindRuntime snapshot=SnapshotMindRuntime.open(dir.resolve("Blocker"),new RevisionRef(blocker.getContextId(),blocker.getRevision()),"late-trigger")) {
            trigger=org.kanger.DmzSourceShape.inspect(snapshot.getMind(),100).declarations.get(0).sourceRule;
        }
        User user=new User();user.setUserDir(Files.createDirectories(dir.resolve("operator")).toString());
        user.setDatabaseDir(dir.toString()+java.io.File.separator);DB db=new DB();db.init(user);
        Mind initial=new Mind(user);user.setCurrentMind(initial);Mind q=(Mind)initial.useStorage("Q");user.setCurrentMind(q);
        CanonicalCommandProcessor processor=new CanonicalCommandProcessor();
        try {
            IContextResults.Revision qp=new IContextResults.Revision(db.getContextId(),db.getRevision());
            String state=DmzSourceMaterializationQualification.fingerprint(q);
            String base="ctx dmz "+pin(qp)+" ?"+sign+"cache(1); from=ShortA@"+pin(sa)+" from=ShortB@"+pin(sb)
                    +" from=LongA@"+pin(la)+" from=LongB@"+pin(lb)+" from=Blocker@"+pin(blocker)
                    +" restrict="+pin(blocker)+":"+trigger+" projection=16";
            DmzSourcesQuery.Result first=processor.execute(PARSER.parse(base),user).getDmzResult();
            require(first.restrictionOutcome.availableChoices.size()==2,"late fixture offers two short source derivations at its exact first conflict");
            require(evidenceNodes(first.restrictionOutcome.availableConflicts)==6,"six nodes fully cover the initial short conflict evidence");
            processor.execute(PARSER.parse("ctx dmz choose 1"),user);
            DmzSourcesQuery.Result saved=processor.execute(PARSER.parse("ctx dmz choose 1"),user).getDmzResult();
            require(saved.restrictionOutcome.trace.size()==2 && saved.restrictionOutcome.availableChoices.size()>=3,
                    "two explicit selections retain a longer mixed-source frontier");
            require(evidenceNodes(saved.restrictionOutcome.availableConflicts)>6,
                    "the remaining recursive conflict requires more evidence than the first-conflict allowance");
            DmzSourcesQuery.Limits l=saved.request.limits;
            DmzSourcesQuery.Request attempted=saved.chooseRestriction(1,new DmzSourcesQuery.Limits(l.preparation,l.audit,l.lookup,l.recognition,
                    6,l.application,l.query,l.depth,l.nodes,l.work,l.maximumVisits));
            String command=FORMATTER.format(CommandInvocation.command(CommandIntent.CTX_DMZ,
                    java.util.Collections.<String,Object>singletonMap("request",attempted),""));
            String errors=boundaryConsole(q,"ctx dmz choose 1 projection=6\n");
            require(errors.contains("Detached source proof budget exceeded") && errors.contains("Exact replay request: "+command)
                    && !errors.contains("\tat org.kanger") && user.getLastDmzResult()==null,
                    "late projection refusal reports the third selection and both earlier exact identities");
            require(errors.contains("phase=CONFLICT_EVIDENCE") && errors.contains("projection=6; restriction-budget=10000; allowance=6")
                    && (errors.contains("completedChoices=1;") || errors.contains("completedChoices=2;")),
                    "late Console refusal identifies its actual replayed prefix count and evidence projection allowance");
            DmzSourcesQuery.Request parsed=(DmzSourcesQuery.Request)PARSER.parse(command).getArgument("request");
            require(parsed.restrictions.choices.size()==3 && parsed.restrictions.choices.subList(0,2).equals(saved.request.restrictions.choices),
                    "canonical late diagnostic retains the entire ordered three-choice prefix");
            q=(Mind)user.getCurrentMind().useStorage("Q");user.setCurrentMind(q);
            require(state.equals(DmzSourceMaterializationQualification.fingerprint(q)) && db.getRevision()==qp.getRevision(),
                    "late refusal preserves durable Q after replayed earlier native branches");
            String retry=command.replace(" projection=6"," projection=16");
            errors=boundaryConsole(q,command+"\n"+retry+"\nctx dmz explain\n");
            require(errors.contains("Detached source proof budget exceeded") && errors.contains("Exact replay request: "+command),
                    "unchanged late command refuses again before the enlarged-budget recovery");
            DmzSourcesQuery.Result recovered=user.getLastDmzResult();
            require(recovered!=null && recovered.restrictionOutcome.status==DmzSourcesQuery.RestrictionStatus.NEXT_CONFLICT
                    && recovered.restrictionOutcome.trace.size()==3 && recovered.request.restrictions.choices.equals(attempted.restrictions.choices),
                    "fresh enlarged retry reconstructs the exact three selected identities and a next frontier");
            for(int i=0;i<2;i++) {
                DmzSourcesQuery.RestrictionStep before=saved.restrictionOutcome.trace.get(i),after=recovered.restrictionOutcome.trace.get(i);
                require(before.selected.identity().equals(after.selected.identity()) && before.conflicts.size()==after.conflicts.size(),
                        "long-prefix recovery preserves earlier selected histories and pair counts");
                for(int j=0;j<before.conflicts.size();j++)require(before.conflicts.get(j).identity().equals(after.conflicts.get(j).identity()),
                        "long-prefix recovery reproduces both sides of every earlier conflict");
            }
            q=(Mind)user.getCurrentMind().useStorage("Q");user.setCurrentMind(q);
            DmzSourcesQuery.Result done=recovered;
            for(int count=0;done.restrictionOutcome.status==DmzSourcesQuery.RestrictionStatus.NEXT_CONFLICT;count++) {
                require(count<3,"remaining recursive alternatives stay bounded after recovery");
                done=processor.execute(PARSER.parse("ctx dmz choose 1"),user).getDmzResult();
            }
            require(done.restrictionOutcome.status==DmzSourcesQuery.RestrictionStatus.COMPLETE && done.restrictionOutcome.trace.size()==6
                    && done.request.restrictions.choices.subList(0,3).equals(attempted.restrictions.choices)
                    && Boolean.FALSE.equals(done.opinions.get(0).answer.value),"all six exact choices complete without duplicating the diagnostic prefix");
            require(saved.restrictionOutcome.trace.size()==2 && recovered.restrictionOutcome.trace.size()==3
                    && attempted.limits.projection==6 && done.request.limits.projection==16
                    && state.equals(DmzSourceMaterializationQualification.fingerprint(q)) && db.getRevision()==qp.getRevision()
                    && !db.hasConnectedContexts(),"long-prefix failures and recovery preserve Q, topology and detached earlier observations");
        } finally { if(user.getCurrentMind().isStorageUsed())user.setCurrentMind(user.getCurrentMind().closeStorage()); }
    }
    private static void answerDiagnostic(boolean negative,int conflicts,Boolean truth) throws Exception {
        Path dir=Files.createTempDirectory("dmz-answer-diagnostic-");System.setProperty("user.home",dir.toString());
        String sign=negative?"~":"",opposite=negative?"":"~";
        int answerIndex=conflicts+1;
        StringBuilder facts=new StringBuilder();
        for(int i=1;i<=conflicts;i++)facts.append("!seed(").append(i).append("); !").append(opposite).append("cache(").append(i).append("); ");
        facts.append("!seed(").append(answerIndex).append(");");
        create(dir,"Q",facts.toString());
        IContextResults.Revision shortSource=create(dir,"Short","!@x seed(x) -> "+sign+"cache(x);");
        IContextResults.Revision answerSource=create(dir,"Answer","!@x seed(x) -> a(x); !@x a(x) -> b(x); !@x b(x) -> "+sign+"answer(x);");
        long trigger;
        try(SnapshotMindRuntime snapshot=SnapshotMindRuntime.open(dir.resolve("Short"),new RevisionRef(shortSource.getContextId(),shortSource.getRevision()),"answer-trigger")) {
            trigger=org.kanger.DmzSourceShape.inspect(snapshot.getMind(),100).declarations.get(0).sourceRule;
        }
        User user=new User();user.setUserDir(Files.createDirectories(dir.resolve("operator")).toString());
        user.setDatabaseDir(dir.toString()+java.io.File.separator);DB db=new DB();db.init(user);
        Mind initial=new Mind(user);user.setCurrentMind(initial);Mind q=(Mind)initial.useStorage("Q");user.setCurrentMind(q);
        CanonicalCommandProcessor processor=new CanonicalCommandProcessor();
        try {
            IContextResults.Revision qp=new IContextResults.Revision(db.getContextId(),db.getRevision());
            String state=DmzSourceMaterializationQualification.fingerprint(q);
            String query=truth==null?"?missing("+answerIndex+");":"?"+(truth?sign:opposite)+"answer("+answerIndex+");";
            String base="ctx dmz "+pin(qp)+" "+query+" from=Short@"+pin(shortSource)+" from=Answer@"+pin(answerSource)
                    +" restrict="+pin(shortSource)+":"+trigger+" projection=3";
            DmzSourcesQuery.Result first=processor.execute(PARSER.parse(base),user).getDmzResult();
            require(first.restrictionOutcome.availableChoices.size()==1 && evidenceNodes(first.restrictionOutcome.availableConflicts)==3,
                    "Console answer fixture completely projects its conflict evidence under three nodes");
            String firstPair=first.restrictionOutcome.availableConflicts.get(0).identity();
            DmzSourcesQuery.Result retained=first;
            for(int i=1;i<conflicts;i++)retained=processor.execute(PARSER.parse("ctx dmz choose 1"),user).getDmzResult();
            require(retained.restrictionOutcome.trace.size()==conflicts-1
                    && retained.restrictionOutcome.status==DmzSourcesQuery.RestrictionStatus.NEXT_CONFLICT,
                    "answer recovery retains every earlier executed conflict before the final explicit selection");
            DmzSourcesQuery.Request selected=retained.chooseRestriction(1);
            if(truth==null) {
                ByteArrayOutputStream output=new ByteArrayOutputStream();
                String errors=boundaryConsole(q,"ctx dmz choose 1\nctx dmz explain\nctx tree 1\n",output);
                DmzSourcesQuery.Result unknown=user.getLastDmzResult();
                require(errors.isEmpty() && unknown!=null && unknown.restrictionOutcome.status==DmzSourcesQuery.RestrictionStatus.COMPLETE
                        && unknown.restrictionOutcome.trace.size()==conflicts && unknown.request.restrictions.choices.equals(selected.restrictions.choices)
                        && unknown.opinions.get(0).answer.value==null && unknown.opinions.get(0).answer.proofs.isEmpty()
                        && unknown.viewOpinions.size()==1 && unknown.viewOpinions.get(0).value==null && unknown.viewOpinions.get(0).proofs.isEmpty(),
                        "unknown multi-choice branch completes without fabricated answer or truth-view proofs under the three-node cap");
                require(unknown.restrictionProofs.size()==conflicts && unknown.opinions.get(0).restrictionProofs.size()==conflicts
                        && unknown.viewOpinions.get(0).restrictionProofs.size()==conflicts && !unknown.restartRequired
                        && output.toString("UTF-8").contains("Opinion 1: UNKNOWN"),
                        "unknown Console opinion retains restriction provenance and does not become a projection stop");
                q=(Mind)user.getCurrentMind().useStorage("Q");user.setCurrentMind(q);
                require(state.equals(DmzSourceMaterializationQualification.fingerprint(q)),"unknown completion preserves durable Q");
                IContextResults.Revision advanced=create(dir,"Answer","!later(9);");
                require(advanced.getRevision()>answerSource.getRevision(),"answer source HEAD advances before exact unknown replay");
                String command=FORMATTER.format(CommandInvocation.command(CommandIntent.CTX_DMZ,
                        java.util.Collections.<String,Object>singletonMap("request",selected),""));
                output.reset();
                errors=boundaryConsole(q,command.replace(" projection=3"," projection=2")+"\n"+command+"\nctx dmz explain\nctx tree 1\n",output);
                require(errors.contains("phase=CONFLICT_EVIDENCE; completedChoices=0; projection=2") && !errors.contains("phase=QUERY_ANSWER")
                        && !errors.contains("\tat org.kanger"),"unknown answer needs no answer proof budget but still enforces complete conflict evidence");
                DmzSourcesQuery.Result replayed=user.getLastDmzResult();
                require(replayed!=null && replayed.restrictionOutcome.status==DmzSourcesQuery.RestrictionStatus.COMPLETE
                        && replayed.opinions.get(0).answer.value==null && replayed.opinions.get(0).answer.proofs.isEmpty()
                        && replayed.request.restrictions.choices.equals(selected.restrictions.choices)
                        && replayed.sources.stream().anyMatch(source->source.revision.getContextId().equals(answerSource.getContextId())
                                && source.revision.getRevision()==answerSource.getRevision())
                        && output.toString("UTF-8").contains("Opinion 1: UNKNOWN"),"fresh unknown replay retains exact old pins and full prefix after evidence refusal");
                for(int i=0;i<conflicts;i++)require(unknown.restrictionOutcome.trace.get(i).selected.identity().equals(replayed.restrictionOutcome.trace.get(i).selected.identity())
                        && unknown.restrictionOutcome.trace.get(i).conflicts.get(0).identity().equals(replayed.restrictionOutcome.trace.get(i).conflicts.get(0).identity()),
                        "unknown fresh replay preserves all selected histories and opposing proof pairs");
                q=(Mind)user.getCurrentMind().useStorage("Q");user.setCurrentMind(q);
                require(retained.restrictionOutcome.trace.size()==conflicts-1 && unknown.restrictionOutcome.trace.size()==conflicts
                        && state.equals(DmzSourceMaterializationQualification.fingerprint(q)) && db.getRevision()==qp.getRevision()
                        && !db.hasConnectedContexts(),"unknown completions and bounded evidence refusal preserve Q, topology and retained history");
                return;
            }
            String errors=boundaryConsole(q,"ctx dmz choose 1\n");
            require(errors.contains("phase=QUERY_ANSWER; completedChoices="+conflicts+"; projection=3; restriction-budget=10000; allowance=3")
                    && user.getLastDmzResult()==null && !errors.contains("\tat org.kanger"),
                    "real Console reports final answer projection refusal after the selected branch completes");
            String diagnostic=null;
            for(String line:errors.split("\\R"))if(line.startsWith("Exact replay request: ")) {
                require(diagnostic==null,"answer projection refusal reports exactly one attempted request");
                diagnostic=line.substring("Exact replay request: ".length());
            }
            require(diagnostic!=null && FORMATTER.format(PARSER.parse(diagnostic)).equals(diagnostic),
                    "answer-phase diagnostic is a canonical full replay command");
            DmzSourcesQuery.Request attempted=(DmzSourcesQuery.Request)PARSER.parse(diagnostic).getArgument("request");
            require(attempted.restrictions.choices.equals(selected.restrictions.choices) && attempted.queryStatement.equals(selected.queryStatement)
                    && attempted.restrictions.triggerRule==trigger && attempted.sources.expectedQ.getContextId().equals(qp.getContextId())
                    && attempted.sources.expectedQ.getRevision()==qp.getRevision(),
                    "answer diagnostic preserves native query, exact trigger, Q pin and completed restriction identity");
            q=(Mind)user.getCurrentMind().useStorage("Q");user.setCurrentMind(q);
            require(state.equals(DmzSourceMaterializationQualification.fingerprint(q)) && db.getRevision()==qp.getRevision(),
                    "answer projection failure preserves durable Q after native branch rollback and reopening");
            IContextResults.Revision advanced=create(dir,"Answer","!later(9);");
            require(advanced.getRevision()>answerSource.getRevision(),"answer source HEAD advances before exact diagnostic recovery");
            String retry=diagnostic.replace(" projection=3"," projection=4");
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            errors=boundaryConsole(q,diagnostic+"\n"+retry+"\nctx dmz explain\nctx tree 1\n",output);
            require(errors.contains("phase=QUERY_ANSWER; completedChoices="+conflicts+"; projection=3")
                    && errors.contains("Exact replay request: "+diagnostic) && !errors.contains("\tat org.kanger"),
                    "unchanged full command repeats its answer-phase refusal before recovery");
            DmzSourcesQuery.Result done=user.getLastDmzResult();
            require(done!=null && done.restrictionOutcome.status==DmzSourcesQuery.RestrictionStatus.COMPLETE
                    && done.request.restrictions.choices.equals(selected.restrictions.choices) && done.restrictionOutcome.trace.size()==conflicts
                    && done.restrictionOutcome.trace.get(0).conflicts.get(0).identity().equals(firstPair)
                    && truth.equals(done.opinions.get(0).answer.value) && done.opinions.get(0).answer.proofs.size()==1,
                    "fresh four-node Console retry returns the same completed branch, historical pair and requested native truth value");
            for(int i=0;i<retained.restrictionOutcome.trace.size();i++) {
                DmzSourcesQuery.RestrictionStep before=retained.restrictionOutcome.trace.get(i),after=done.restrictionOutcome.trace.get(i);
                require(before.selected.identity().equals(after.selected.identity()) && before.alternatives.size()==after.alternatives.size()
                        && before.conflicts.size()==after.conflicts.size() && before.after==after.after,
                        "final answer recovery preserves earlier selected alternatives and branch outcomes");
                for(int j=0;j<before.conflicts.size();j++)require(before.conflicts.get(j).identity().equals(after.conflicts.get(j).identity()),
                        "final answer recovery reproduces both opposing proof chains in every retained step");
            }
            DmzSourcesQuery.Proof answer=done.opinions.get(0).answer.proofs.get(0);
            require(proofNodes(answer)==4 && answer.source.revision.getContextId().equals(answerSource.getContextId())
                    && answer.source.revision.getRevision()==answerSource.getRevision() && answer.conclusion.equals("!"+sign+"answer("+answerIndex+");"),
                    "recovered final proof retains all four nodes and the historical source revision after HEAD advances");
            require(output.toString("UTF-8").contains("DMZ restrictions: COMPLETE; selected="+conflicts)
                    && output.toString("UTF-8").contains("Opinion 1: "+(truth?"TRUE":"FALSE")) && output.toString("UTF-8").contains(sign+"answer("+answerIndex+")"),
                    "Console displays the recovered completed opinion and signed answer proof tree");
            q=(Mind)user.getCurrentMind().useStorage("Q");user.setCurrentMind(q);
            require(first.restrictionOutcome.trace.isEmpty() && retained.restrictionOutcome.trace.size()==conflicts-1
                    && selected.restrictions.choices.size()==conflicts && attempted.limits.projection==3 && done.request.limits.projection==4
                    && state.equals(DmzSourceMaterializationQualification.fingerprint(q)) && db.getRevision()==qp.getRevision()
                    && !db.hasConnectedContexts(),"final answer refusal and recovery preserve Q, topology and detached original observations");
        } finally { if(user.getCurrentMind().isStorageUsed())user.setCurrentMind(user.getCurrentMind().closeStorage()); }
    }
    private static int proofNodes(DmzSourcesQuery.Proof proof) {
        int nodes=1;for(DmzSourcesQuery.Proof premise:proof.premises)nodes+=proofNodes(premise);return nodes;
    }
    private static int evidenceNodes(java.util.List<DmzSourcesQuery.RestrictionConflict> conflicts) {
        int nodes=0;for(DmzSourcesQuery.RestrictionConflict pair:conflicts)nodes+=proofNodes(pair.positive)+proofNodes(pair.negative);return nodes;
    }
    private static String boundaryConsole(Mind q,String script) throws Exception {
        return boundaryConsole(q,script,new ByteArrayOutputStream());
    }
    private static String boundaryConsole(Mind q,String script,ByteArrayOutputStream output) throws Exception {
        InputStream in=System.in;PrintStream out=System.out,err=System.err;
        ByteArrayOutputStream errors=new ByteArrayOutputStream();
        try {
            System.setIn(new ByteArrayInputStream(script.getBytes("UTF-8")));
            System.setOut(new PrintStream(output,true,"UTF-8"));
            System.setErr(new PrintStream(errors,true,"UTF-8"));
            CanonicalConsole.session(q,new ShutdownHook(q));
        } finally { System.setIn(in);System.setOut(out);System.setErr(err); }
        return errors.toString("UTF-8");
    }
    private static void requireBoundaryDiagnostics(String errors,String exactAttempt,String reason) throws Exception {
        require(errors.contains(reason) && !errors.contains("\tat org.kanger"),"boundary refusal remains readable in real Console");
        int count=0;
        for(String line:errors.split("\\R"))if(line.startsWith("Exact replay request: ")) {
            String command=line.substring("Exact replay request: ".length());
            require(command.equals(exactAttempt) && FORMATTER.format(PARSER.parse(command)).equals(command),
                    "saved and fresh boundary refusals retain identical exact attempted commands");
            ++count;
        }
        require(count==2,"both saved selection and fresh diagnostic retry report their exact request");
    }
    private static void require(boolean value,String reason) { ++checks;if(!value)throw new AssertionError(reason); }
    private static void moveContextFixture(Path directory,String from,String to) throws Exception {
        Path source=directory.resolve(from),target=directory.resolve(to);
        Files.move(ContextStore.contextPath(source),ContextStore.contextPath(target));
        Files.move(ContextStore.revisionPath(source),ContextStore.revisionPath(target));
        Files.move(ContextStore.stateRoot(source),ContextStore.stateRoot(target));
    }
}
