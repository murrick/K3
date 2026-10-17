/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.command.*;

/** Shared grammar/dispatch and actual read-only Console diagnostics over pinned views. */
public final class ContextUniversalCandidateConsoleRunner {
    private static int checks;
    private static final CommandParser PARSER=new CommandParser();
    private static final CommandFormatter FORMATTER=new CommandFormatter();
    private interface Attempt { void run() throws Exception; }
    private static void require(boolean yes,String why) { ++checks; if(!yes) throw new AssertionError(why); }
    private static void reject(Attempt attempt,String why) throws Exception {
        boolean rejected=false; try { attempt.run(); } catch(Exception expected) { rejected=true; } require(rejected,why);
    }
    private static final class Fixture implements AutoCloseable {
        final User user=new User(); final DB db=new DB(); final Mind mind;
        Fixture(Path dir,String name) throws Exception {
            user.setDatabaseDir(dir.toString()+File.separator); user.setUserDir(Files.createDirectories(dir.resolve("operator")).toString()); db.init(user);
            Mind root=new Mind(user); user.setCurrentMind(root); mind=(Mind)root.useStorage(name); user.setCurrentMind(mind);
        }
        public void close() throws Exception { if(user.getCurrentMind().isStorageUsed()) user.setCurrentMind(user.getCurrentMind().closeStorage()); }
    }
    private static ContextUniversalCandidateAudit.Audit command(Fixture f,String text) throws Exception {
        CanonicalCommandProcessor.Result r=new CanonicalCommandProcessor().execute(PARSER.parse(text),f.user);
        require(r.isHandled() && r.isSuccess() && r.getMind()==f.mind && r.getCandidateAudit()!=null,"shared canonical diagnostic result");
        return r.getCandidateAudit();
    }
    private static String[] console(Mind mind,String script) throws Exception {
        InputStream in=System.in; PrintStream out=System.out,err=System.err;
        ByteArrayOutputStream output=new ByteArrayOutputStream(),errors=new ByteArrayOutputStream();
        try {
            System.setIn(new ByteArrayInputStream(script.getBytes("UTF-8")));
            System.setOut(new PrintStream(output,true,"UTF-8")); System.setErr(new PrintStream(errors,true,"UTF-8"));
            CanonicalConsole.session(mind,new ShutdownHook(mind));
        } finally { System.setIn(in);System.setOut(out);System.setErr(err); }
        return new String[]{output.toString("UTF-8"),errors.toString("UTF-8")};
    }
    public static void main(String[] args) {
        try {
            Path dir=Files.createTempDirectory("universal-candidate-console-");System.setProperty("user.home",dir.toString());
            String safe="ctx candidate \"!@x left(x)&&right(x)->q(x);\"";
            String clash="ctx candidate \"!@x left(x)&&right(x)->p(x);\"";
            CommandInvocation parsed=PARSER.parse(safe);
            require(parsed.getIntent()==CommandIntent.CTX_CANDIDATE && new CanonicalCommandProcessor().handles(parsed),"shared candidate intent, no Console-only alternate parser");
            for(String c:new String[]{safe,safe+" rows=999 rounds=8 local=9 depth=7 instances=400 models=1234 preparation=20000 events=3000 work=50000 audit=60000",safe+" depth=0"}) {
                String echo=FORMATTER.format(PARSER.parse(c));
                require(echo.equals(FORMATTER.format(PARSER.parse(echo))),"canonical diagnostic echo stable");
                require(PARSER.parse(c).getArgument("request").equals(PARSER.parse(echo).getArgument("request")),"all request fields survive round trip");
            }
            for(String bad:new String[]{"ctx candidate","ctx candidate ?p(1);",safe+" work=0",safe+" depth=-1",safe+" rows=10001",safe+" work=1 work=2",safe+" unknown=3",safe+" 5",safe+" models=9999999999999999999",safe+" audit=",safe+" rounds=0",safe+" events=0"}) reject(() -> PARSER.parse(bad),"invalid candidate grammar/limits");
            require(PARSER.parse("!@x p(x)->q(x);").isCoreLanguage(),"ordinary Core assertion input is not redirected");
            require(PARSER.parse("ctx dmz family").getIntent()==CommandIntent.CTX_DMZ_FAMILY,"existing DMZ grammar unchanged");
            require(new CommandHelpRenderer().render().contains("ctx candidate <assertion>"),"shared help exposes read-only syntax");
            Map<String,Integer> overrides=new HashMap<>();overrides.put("depth",0);
            ContextUniversalCandidateRequest immutable=new ContextUniversalCandidateRequest("!p(1);",overrides);overrides.put("depth",99);
            require(immutable.limit("depth")==0,"caller cannot mutate normalized request");
            reject(() -> immutable.limits.clear(),"request limits immutable");
            try(Fixture a=new Fixture(dir,"A")) {require(a.mind.compile("!left(1); !p(1);",null,false),"A fixture");}
            try(Fixture b=new Fixture(dir,"B")) {require(b.mind.compile("!right(1); !~p(1);",null,false),"B fixture");}
            long before; int rows; UUID context;
            try(Fixture x=new Fixture(dir,"X")) {
                require(x.mind.compile("!anchor(1);",null,false),"actual X fixture");x.db.connectContext("A");x.db.connectContext("B");
                before=x.db.getRevision();context=x.db.getContextId();rows=x.mind.getRules().size();
                int logs=x.mind.getLog().size(),solutions=x.mind.getSolutions().size(),hypotheses=x.mind.getHypothesis().size();
                CanonicalCommandProcessor processor=new CanonicalCommandProcessor();
                processor.execute(PARSER.parse("ctx dmz "+context+"@"+before+" \"?anchor(1);\""),x.user);
                Object saved=x.user.getLastDmzResult();require(saved!=null,"existing saved DMZ diagnostic fixture");
                ContextUniversalCandidateAudit.Audit collision=command(x,clash);
                require(collision.verified && collision.evaluation.status==ContextUniversalFamilyInspection.Status.COLLISION && collision.evaluation.qualification.completedBarriers==2,"shared dispatch returns audited actual masked collision");
                require(collision.oppositions.stream().anyMatch(p -> p.usesCandidate),"typed candidate dependence reaches adapters");
                ContextUniversalCandidateAudit.Audit good=command(x,safe);
                require(good.verified && good.evaluation.status==ContextUniversalFamilyInspection.Status.CONSISTENT,"shared dispatch returns checked all-value model evidence");
                ContextUniversalCandidateAudit.Audit stopped=command(x,safe+" work=1");
                require(!stopped.verified && stopped.evaluation.status==ContextUniversalFamilyInspection.Status.INCOMPLETE,"incomplete inference remains diagnostic");
                ContextUniversalCandidateAudit.Audit auditStop=command(x,safe+" audit=1");
                require(!auditStop.verified && auditStop.evaluation.status==ContextUniversalFamilyInspection.Status.CONSISTENT,"final audit stop cannot be rendered as approval");
                require(x.user.getLastDmzResult()==saved,"candidate diagnostics preserve saved DMZ history");
                try(ContextProductionSnapshotSession lease=ContextProductionSnapshotSession.open(x.db,x.mind,1000)) {
                    reject(() -> lease.inspectUniversalCandidate(new ContextUniversalCandidateRequest("!trial(1);",Collections.singletonMap("rows",1))),"diagnostic cannot silently ignore a different explicit row limit");
                }
                require(x.db.getRevision()==before && x.mind.getRules().size()==rows,"typed diagnostic dispatch is physically read-only");
                require(x.mind.getLog().size()==logs && x.mind.getSolutions().size()==solutions && x.mind.getHypothesis().size()==hypotheses,"source presentation stores preserved");
                String[] display=console(x.mind,clash+"\n"+safe+"\n"+safe+" work=1\n"+safe+" audit=1\nctx candidate \"!@x p(x)->q(x+1);\"\nctx candidate \"!a(1); !b(1);\"\n"+safe+" unknown=1\n");
                require(display[0].contains("Candidate check: COLLISION (read-only)") && display[0].contains("Candidate check: CONSISTENT (read-only)") && display[0].contains("Candidate check: INCOMPLETE (read-only)"),"actual Console renders three disjoint diagnostic outcomes");
                require(display[0].contains("uses candidate=yes") && display[0].contains("foreign ") && display[0].contains("candidate ") && display[0].contains("Completed barriers: 2"),"Console retains exact causes and barrier count");
                require(display[0].contains("all admissible values") && display[0].contains("No assertion written"),"Console states actual scope/read-only result");
                require(display[1].contains("Invalid candidate limit") && !display[1].contains("\tat org.kanger"),"malformed diagnostic produces readable boundary error");
                require(x.user.getLastDmzResult()==saved,"actual Console does not replace existing DMZ saved result");
            }
            try(Fixture reopened=new Fixture(dir,"X")) {
                require(reopened.db.getRevision()==before && reopened.db.getContextId().equals(context) && reopened.mind.getRules().size()==rows,"all Console trials leave only original physical source after reopen");
                try(ContextProductionSnapshotSession lease=ContextProductionSnapshotSession.open(reopened.db,reopened.mind,1000)) {
                    require(lease.inventory().x.primary.size()==1 && lease.inventory().x.primary.get(0).origin.contains("anchor"),"no candidate or query became a physical primary");
                }
            }
            User plain=new User();Mind root=new Mind(plain);plain.setCurrentMind(root);
            reject(() -> new CanonicalCommandProcessor().execute(parsed,plain),"no storage refuses without fallback to Core authoring");
            System.out.println("CONTEXT_UNIVERSAL_CANDIDATE_CONSOLE_PASS checks="+checks);System.exit(0);
        } catch(Throwable failure) {failure.printStackTrace();System.exit(1);}
    }
}
