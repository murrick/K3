/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collections;
import org.kanger.*;
import org.kanger.command.*;
import org.kanger.interfaces.*;

/** Disposable operator fixture; no arguments qualify its real Console walkthrough. */
public final class DmzManualFixtureRunner {
    private static int checks;
    private static final CommandParser PARSER=new CommandParser();
    private static final CommandFormatter FORMATTER=new CommandFormatter();
    public static void main(String[] args) {
        try {
            Path root=args.length==0?Files.createTempDirectory("dmz-manual-gate-"):Paths.get(args[0]).toAbsolutePath();
            String mode=args.length==0?"smoke":args[1];
            if(!mode.equals("prepare") && !mode.equals("smoke") && !mode.equals("console"))throw new IllegalArgumentException("Unknown fixture mode");
            System.setProperty("user.home",root.toString());
            run(root,mode);
            if(mode.equals("smoke"))System.out.println("DMZ_MANUAL_FIXTURE_PASS checks="+checks);
            System.exit(0);
        } catch(Throwable failure) { failure.printStackTrace();System.exit(1); }
    }
    private static User operator(Path directory,Path home) throws Exception {
        User user=new User();user.setUserDir(Files.createDirectories(home).toString()+File.separator);
        user.setDatabaseDir(directory.toString()+File.separator);return user;
    }
    private static IContextResults.Revision create(Path directory,String name,String source) throws Exception {
        User user=operator(directory,directory.getParent().resolve("setup-"+name));
        DB db=new DB();db.init(user);Mind mind=new Mind(user);user.setCurrentMind(mind);
        mind=(Mind)mind.useStorage(name);user.setCurrentMind(mind);
        try { require(mind.compile(source,null,false),"fixture declarations accepted: "+name); }
        finally { user.setCurrentMind(mind.closeStorage()); }
        try(ContextSnapshot snapshot=ContextSnapshot.open(directory.resolve(name))) {
            return new IContextResults.Revision(snapshot.getContextId(),snapshot.getRevision());
        }
    }
    private static String pin(IContextResults.Revision revision) {return revision.getContextId()+"@"+revision.getRevision();}
    private static void run(Path root,String mode) throws Exception {
        // Refuse an existing database directory; never seed over operator storage.
        Path directory=Files.createDirectory(root.resolve("database"));
        IContextResults.Revision qp=create(directory,"Q","!seed(1); !~cache(1); !seed(2); !~cache(2); !seed(3);");
        IContextResults.Revision rules=create(directory,"Rules","!@x seed(x) -> cache(x); !@x cache(x) -> tail(x);");
        long trigger=-1;
        try(SnapshotMindRuntime snapshot=SnapshotMindRuntime.open(directory.resolve("Rules"),
                new RevisionRef(rules.getContextId(),rules.getRevision()),"Rules")) {
            for(DmzSourceShape.Declaration declaration:DmzSourceShape.inspect(snapshot.getMind(),100).declarations)
                if(declaration.statement.contains("seed(x)"))trigger=declaration.sourceRule;
        }
        require(trigger>=0,"trigger discovered from the exact Rules snapshot");
        User user=operator(directory,root.resolve("operator"));DB db=new DB();db.init(user);
        Mind q=new Mind(user);user.setCurrentMind(q);q=(Mind)q.useStorage("Q");user.setCurrentMind(q);
        String state=DmzSourceMaterializationQualification.fingerprint(q);
        String base="ctx dmz "+pin(qp)+" ?tail(3); from=Rules@"+pin(rules)+" restrict="+pin(rules)+":"+trigger;
        try {
            DmzSourcesQuery.Request request=(DmzSourcesQuery.Request)PARSER.parse(base).getArgument("request");
            DmzSourcesQuery.Result first=q.executeDmzSources(request);
            require(first.restrictionOutcome.status==DmzSourcesQuery.RestrictionStatus.NEXT_CONFLICT
                    && first.restrictionOutcome.availableChoices.size()==1,"one explicit derived choice at the first covered conflict");
            DmzSourcesQuery.Limits small=new DmzSourcesQuery.Limits(10000,10000,10000,10000,1,10000,10000,8,128,1000000,1000);
            String refused=FORMATTER.format(CommandInvocation.command(CommandIntent.CTX_DMZ,
                    Collections.<String,Object>singletonMap("request",first.chooseRestriction(1,small)),""));
            String retry=refused.replace(" projection=1"," projection=3");
            require(!retry.equals(refused),"recovery changes only the requested projection cap");
            String qOnly="ctx dmz "+pin(qp)+" ?tail(3);";
            String script="ctx version Q\nctx version Rules\n"+base+"\nctx dmz explain\nctx dmz choose 0\n"
                    +"ctx dmz choose 1 projection=1\nctx dmz explain\n"+retry+"\nctx dmz explain\n"
                    +"ctx dmz choose 1 projection=3\nctx dmz explain\nctx tree 1\n"+qOnly+"\nctx tree 1\n";
            Files.write(root.resolve("walkthrough.commands"),script.getBytes(StandardCharsets.UTF_8));
            String guide="Q owns seeds 1, 2, 3 and protected ~cache(1), ~cache(2).\n"
                    +"Rules derives cache and tail; tail(3) exists only in the private source workflow.\n"
                    +"Start: "+base+"\nShow evidence: ctx dmz explain\n"
                    +"Choose explicitly: ctx dmz choose 1\nRepeat for the second conflict.\n"
                    +"Expected: COMPLETE selected=2, Opinion 1: TRUE, three-node tail(3) proof.\n"
                    +"Budget exercise: restart the Start command, then ctx dmz choose 1 projection=1.\n"
                    +"Expected: CONFLICT_EVIDENCE refusal and Exact replay request; saved result cleared.\n"
                    +"Recovery after that first failed choice: "+retry+"\n"
                    +"Then ctx dmz choose 1 projection=3; inspect ctx tree 1.\n"
                    +"Q-only control: "+qOnly+"\nExpected UNKNOWN with no answer proof.\n"
                    +"This replaces the active restricted result. Exit with quit (or EOF).\n"
                    +"Full scripted walkthrough: walkthrough.commands (includes intentional refusals).\n"
                    +"Each launcher invocation creates a new fixture. No implicit choice or user storage is reused.\n";
            Files.write(root.resolve("WALKTHROUGH.txt"),guide.getBytes(StandardCharsets.UTF_8));
            System.out.println("DMZ manual fixture\nQ: "+pin(qp)+"\nRules: "+pin(rules)+"; trigger rule "+trigger+"\n"+guide);
            if(mode.equals("prepare"))return;
            if(mode.equals("console"))CanonicalConsole.session(q,new ShutdownHook(q));
            else smoke(q,script,root,user,qp,rules);
            require(!user.getCurrentMind().isStorageUsed(),"Console closes its active storage at EOF");
            q=(Mind)user.getCurrentMind().useStorage("Q");user.setCurrentMind(q);
            require(state.equals(DmzSourceMaterializationQualification.fingerprint(q)) && db.getRevision()==qp.getRevision()
                    && !db.hasConnectedContexts(),"walkthrough preserves Q, published revision and topology after reopen");
            try(ContextSnapshot snapshot=ContextSnapshot.open(directory.resolve("Rules"))) {
                require(snapshot.getContextId().equals(rules.getContextId()) && snapshot.getRevision()==rules.getRevision(),
                        "external Rules remains at its original published pin");
            }
            System.out.println("DMZ fixture Q preservation: PASS");
        } finally {if(user.getCurrentMind().isStorageUsed())user.setCurrentMind(user.getCurrentMind().closeStorage());}
    }
    private static void smoke(Mind q,String script,Path root,User user,IContextResults.Revision qp,
            IContextResults.Revision rules) throws Exception {
        InputStream input=System.in;PrintStream output=System.out,error=System.err;
        ByteArrayOutputStream out=new ByteArrayOutputStream(),err=new ByteArrayOutputStream();
        try {
            System.setIn(new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)));
            System.setOut(new PrintStream(out,true,"UTF-8"));System.setErr(new PrintStream(err,true,"UTF-8"));
            CanonicalConsole.session(q,new ShutdownHook(q));
        } finally {System.setIn(input);System.setOut(output);System.setErr(error);}
        String displayed=out.toString("UTF-8"),refusals=err.toString("UTF-8");
        Files.write(root.resolve("console-out.txt"),out.toByteArray());Files.write(root.resolve("console-err.txt"),err.toByteArray());
        require(displayed.contains("UUID: "+qp.getContextId()) && displayed.contains("UUID: "+rules.getContextId()),"Console discovers the actual source identities");
        require(displayed.contains("Positive evidence:") && displayed.contains("Negative evidence:"),"both opposing proof sides are visible");
        require(displayed.contains("DMZ restrictions: NEXT_CONFLICT; selected=1")
                && displayed.contains("DMZ restrictions: COMPLETE; selected=2") && displayed.contains("Opinion 1: TRUE"),
                "corrected replay reconstructs the first choice and explicitly completes the second conflict");
        require(displayed.contains("Restricted source chain 2:") && displayed.contains("tail(3)"),"completed proof and restriction provenance are visible");
        require(refusals.contains("phase=CONFLICT_EVIDENCE") && refusals.contains("projection=1")
                && refusals.contains("Exact replay request:") && refusals.contains("No saved DMZ query")
                && !refusals.contains("\tat org.kanger"),"budget refusal clears active history and exposes a readable exact retry");
        DmzSourcesQuery.Result last=user.getLastDmzResult();
        require(last!=null && last.request.restrictions==null && last.opinions.get(0).answer.value==null
                && last.opinions.get(0).answer.proofs.isEmpty() && displayed.contains("Opinion 1: UNKNOWN"),
                "fresh Q-only control has no external answer proof or inherited restrictions");
    }
    private static void require(boolean value,String message) {++checks;if(!value)throw new AssertionError(message);}
}
