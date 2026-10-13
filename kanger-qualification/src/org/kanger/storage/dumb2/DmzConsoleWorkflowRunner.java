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
        try { run();restrictions(false);restrictions(true);staleAuthority();System.out.println("DMZ_CONSOLE_WORKFLOW_PASS checks="+checks);System.exit(0); }
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
                script.append(count==0?"ctx dmz \"cho\" 1\nctx dmz explain\n":"dmz choose 1\nctx dmz explain\n");
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
            script.append("ctx tree 1\nctx dmz choose 1\n").append(base).append(" choice=foreign-proof\n").append("ctx dmz ").append(pin(qp)).append(" ?seed(1);\n");
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
            require(err.toString("UTF-8").contains("DMZ refused:") && !err.toString("UTF-8").contains("\tat org.kanger"),
                    "invalid choice remains readable and console continues to ordinary query");
            Mind reopened=(Mind)user.getCurrentMind().useStorage("Q");user.setCurrentMind(reopened);
            require(state.equals(DmzSourceMaterializationQualification.fingerprint(reopened)) && !db.hasConnectedContexts(),
                    "SDK and command successes/refusals preserve durable Q and topology");
            user.setCurrentMind(reopened.closeStorage());
        } finally { if(user.getCurrentMind().isStorageUsed())user.setCurrentMind(user.getCurrentMind().closeStorage()); }
    }
    private static void reject(Attempt attempt,Class<? extends Exception> type) throws Exception {
        try { attempt.run();throw new AssertionError("Expected rejection "+type); }
        catch(Exception expected) { if(!type.isInstance(expected))throw expected;++checks; }
    }
    private static void staleAuthority() throws Exception {
        Path dir=Files.createTempDirectory("dmz-saved-q-revision-");System.setProperty("user.home",dir.toString());
        create(dir,"Q","!seed(1); !~cache(1);");
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
            String command="ctx dmz "+db.getContextId()+"@"+revision+" ?cache(1); from=Rules@"+pin(units)+" restrict="+pin(units)+":"+rule;
            DmzSourcesQuery.Result saved=processor.execute(PARSER.parse(command),user).getDmzResult();
            require(q.compile("!later(9);",null,false),"Q advances after saving a restriction frontier");
            user.checkpoint(q);
            require(db.getRevision()>revision,"Q publishes a new immutable revision");
            String advanced=DmzSourceMaterializationQualification.fingerprint(q);
            try {
                processor.execute(PARSER.parse("ctx dmz choose 1"),user);
                throw new AssertionError("Saved old Q pin resumed against a new revision");
            } catch(org.kanger.exception.CommandErrorException refusal) {
                require(refusal.toString().contains("Q revision mismatch"),"saved selection must reacquire its original Q pin");
            }
            require(user.getLastDmzResult()==null && advanced.equals(DmzSourceMaterializationQualification.fingerprint(q))
                    && user.getCurrentMind()==q && saved.restrictionOutcome.availableChoices.size()==1,
                    "stale replay refuses without changing advanced Q or old detached alternatives");
        } finally { user.setCurrentMind(q.closeStorage()); }
    }
    private static void require(boolean value,String reason) { ++checks;if(!value)throw new AssertionError(reason); }
}
