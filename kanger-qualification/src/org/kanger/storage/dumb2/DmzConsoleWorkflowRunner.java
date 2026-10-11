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
        try { run();System.out.println("DMZ_CONSOLE_WORKFLOW_PASS checks="+checks);System.exit(0); }
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
        require(PARSER.parse("ctx d A").getIntent()==CommandIntent.CTX_DISCONNECT,"legacy disconnect prefix remains bound");
        String[] malformed={"ctx dmz","ctx dmz "+pin(qp),"ctx dmz HEAD ?p(1);","ctx dmz 1-1-1-1-1@0 ?p(1);",
                "ctx dmz "+qp.getContextId()+"@-1 ?p(1);",base+" from=A@"+pin(a),base+" from=../A@"+pin(a),
                base+" from=Q@"+pin(qp),base+" from=A@HEAD",base+" nodes=0",base+" nodes=10001",base+" work=1000001",
                base+" depth=17",base+" visits=0",base+" rows=10001",base+" audit=-1",base+" visits=2 visits=3",base+" unknown=3",base+" 7",
                base+" audit=99999999999999999999999","ctx dmz "+qp.getContextId()+"@99999999999999999999999 ?p(1);"};
        for(String invalid:malformed)reject(() -> PARSER.parse(invalid),CommandParseException.class);
        String syntax=new org.kanger.command.CommandHelpRenderer().render();
        require(syntax.contains("ctx dmz") && syntax.contains("from=<name>@<UUID>@<revision>"),"shared help includes explicit operation grammar");
        reject(() -> processor.execute(PARSER.parse("ctx dmz "+pin(qp)+" ?authority(1); from=BadUnit@"+pin(badUnit)),user),Exception.class);
        require(state.equals(DmzSourceMaterializationQualification.fingerprint(q)) && user.getCurrentMind()==q && !db.hasConnectedContexts(),"command phase failure closes private owner and preserves Q");
        String oldAnswer="ctx dmz "+pin(qp)+" ?result(3); from=A@"+pin(a);
        IContextResults.Revision advanced=create(dir,"A","!later(9);");
        require(advanced.getRevision()>a.getRevision(),"source head advances before Console invocation");
        String script="ctx version Q\nctx version A\n"+base+"\n"+base+" visits=2\n"+base+" work=1\n"
                +"ctx dmz "+pin(qp)+" ?authority(1); from=Decimal@"+pin(decimal)+"\n"
                +"ctx dmz "+pin(qp)+" ?authority(1); from=Missing@"+pin(a)+"\n"
                +"ctx dmz "+pin(qp)+" ?authority(1); from=BadUnit@"+pin(badUnit)+"\n"
                +"ctx dmz "+qp.getContextId()+"@"+(qp.getRevision()+1)+" ?authority(1);\n"
                +oldAnswer+"\n"+"ctx dmz "+pin(qp)+" ?more(1); from=Unit@"+pin(unit)+"\n"
                +"ctx dmz "+pin(qp)+" ?authority(1);\n"+"ctx dmz "+pin(qp)+" ?authority(1); visits=0\n"
                +"?authority(1);\nquit\n";
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
    private static void reject(Attempt attempt,Class<? extends Exception> type) throws Exception {
        try { attempt.run();throw new AssertionError("Expected rejection "+type); }
        catch(Exception expected) { if(!type.isInstance(expected))throw expected;++checks; }
    }
    private static void require(boolean value,String reason) { ++checks;if(!value)throw new AssertionError(reason); }
}
