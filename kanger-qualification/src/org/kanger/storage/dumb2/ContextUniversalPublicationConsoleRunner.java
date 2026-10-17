/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.command.*;
import org.kanger.exception.TransactionSettlementException;

/** Explicit canonical publication, stale diagnostics and truthful committed-failure Console output. */
public final class ContextUniversalPublicationConsoleRunner {
    private static int checks;
    private static final CommandParser PARSER=new CommandParser();
    private static final CommandFormatter FORMATTER=new CommandFormatter();
    private interface Attempt { void run() throws Exception; }
    private static void require(boolean yes,String why) { ++checks; if(!yes) throw new AssertionError(why); }
    private static void reject(Attempt attempt,String why) throws Exception {
        boolean rejected=false; try { attempt.run(); } catch(Exception expected) { rejected=true; } require(rejected,why);
    }
    private static final class Fixture implements AutoCloseable {
        final User user; final DB db=new DB(); final Mind mind;
        Fixture(Path dir,String name) throws Exception { this(dir,name,new User()); }
        Fixture(Path dir,String name,User user) throws Exception {
            this.user=user; user.setDatabaseDir(dir.toString()+File.separator); user.setUserDir(Files.createDirectories(dir.resolve("operator")).toString()); db.init(user);
            Mind root=new Mind(user); user.setCurrentMind(root); mind=(Mind)root.useStorage(name); user.setCurrentMind(mind);
        }
        public void close() throws Exception { if(user.getCurrentMind().isStorageUsed()) user.setCurrentMind(user.getCurrentMind().closeStorage()); }
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
    private static final class FailOnceUser extends User {
        boolean fail; int failures;
        final IOException injected=new IOException("injected post-durable finalization error");
        FailOnceUser() throws Exception { super(); }
        @Override public void flush() throws Exception { super.flush(); if(fail) { fail=false; ++failures; throw injected; } }
    }
    private static ContextUniversalCandidatePublication write(Fixture f,String text) throws Exception {
        CanonicalCommandProcessor.Result r=new CanonicalCommandProcessor().execute(PARSER.parse(text),f.user);
        require(r.isHandled() && r.getMind()==f.mind && r.getCandidatePublication()!=null,"shared explicit publication report");
        ContextUniversalCandidatePublication p=r.getCandidatePublication();
        require(r.isSuccess()==(p.status==ContextUniversalCandidatePublication.Status.PUBLISHED),"command success cannot conceal a declined publication");return p;
    }
    /** Native-valid language outside this writer must never fall through to ordinary authoring. */
    private static void languageBoundaries(Path dir) throws Exception {
        String[][] cases={
            {"FourVariables","!@x @y @z @w quad(x,y,z,w);"},
            {"FourLiterals","!@x a(x)&&b(x)&&c(x)->d(x);"},
            {"VariableFunction","!@x p(x)->q(x+1);"},
            {"Witness","!$w witness(w);"}
        };
        for(String[] test:cases) {
            User nativeUser=new User();Mind nativeMind=new Mind(nativeUser);nativeUser.setCurrentMind(nativeMind);
            require(nativeMind.compile(test[1],null,false),test[0]+": valid native assertion, not malformed input");
            long revision;String fingerprint;
            try(Fixture x=new Fixture(dir,"Boundary"+test[0])) {
                require(x.mind.compile("!anchor(1);",null,false),"boundary anchor");
                revision=x.db.getRevision();int rows=x.mind.getRules().size(),logs=x.mind.getLog().size(),solutions=x.mind.getSolutions().size(),hypotheses=x.mind.getHypothesis().size();
                try(ContextProductionSnapshotSession lease=ContextProductionSnapshotSession.open(x.db,x.mind,1000)) {fingerprint=lease.inventory().x.fingerprint;}
                ContextUniversalCandidatePublication result=write(x,"ctx candidate write \""+test[1]+"\"");
                require(result.status==ContextUniversalCandidatePublication.Status.INCOMPLETE && result.trial.status==ContextUniversalFamilyInspection.Status.INCOMPLETE && result.audit==null,
                        test[0]+": unsupported candidate cannot acquire audited publication");
                require(result.trial.reason!=null && !result.trial.reason.isEmpty(),test[0]+": explicit stop reason");
                require(result.before.getRevision()==revision && result.after.getRevision()==revision && x.db.getRevision()==revision && x.mind.getRules().size()==rows,
                        test[0]+": no fallback or partial physical publication");
                require(x.mind.getLog().size()==logs && x.mind.getSolutions().size()==solutions && x.mind.getHypothesis().size()==hypotheses,test[0]+": no presentation leakage");
                System.out.println("LANGUAGE_BOUNDARY "+test[0]+": "+result.trial.reason);
            }
            try(Fixture x=new Fixture(dir,"Boundary"+test[0]);ContextProductionSnapshotSession lease=ContextProductionSnapshotSession.open(x.db,x.mind,1000)) {
                require(x.db.getRevision()==revision && lease.inventory().x.fingerprint.equals(fingerprint) && lease.inventory().x.primary.size()==1,test[0]+": exact primary inventory survives reopen unchanged");
            }
        }
        for(boolean foreign:new boolean[]{false,true}) {
            String name=foreign?"Foreign":"Local";
            if(foreign) try(Fixture a=new Fixture(dir,"UnsupportedA")) {require(a.mind.compile(cases[0][1],null,false),"native foreign four-variable knowledge");}
            try(Fixture x=new Fixture(dir,"Existing"+name)) {
                require(x.mind.compile(foreign?"!anchor(1);":cases[0][1],null,false),"existing scope fixture");
                if(foreign) x.db.connectContext("UnsupportedA");
                long revision=x.db.getRevision();int rows=x.mind.getRules().size();
                Exception failure=null;
                try {write(x,"ctx candidate write \"!safe(1);\"");} catch(IllegalStateException expected) {failure=expected;}
                require(failure!=null && failure.getMessage().contains("native binding incomplete") && failure.getMessage().contains("variable limit"),name+": whole family binding must stop, not skip unsupported knowledge");
                require(x.db.getRevision()==revision && x.mind.getRules().size()==rows,name+": existing unsupported row never allows safe-prefix publication");
                String[] display=console(x.mind,"ctx candidate write \"!safe(1);\"\n");
                require(display[1].contains("Candidate write failed:") && !display[0].contains("Candidate write: PUBLISHED"),name+": Console reports binding failure without success");
            }
        }
        try(Fixture x=new Fixture(dir,"SupportedBoundary")) {
            ContextUniversalCandidatePublication result=write(x,"ctx candidate write \"!@x @y @z a(x,y)&&b(y,z)->c(x,z);\"");
            require(result.status==ContextUniversalCandidatePublication.Status.PUBLISHED && result.audit.verified,"three variables and three literals are supported at the boundary");
        }
    }
    private static void threeVariablePublication(Path dir) throws Exception {
        try(Fixture x=new Fixture(dir,"ThreeVariableJoin")) {
            require(x.mind.compile("!edge(1,1); !edge(1,2);",null,false),"native join premises");
            ContextUniversalCandidatePublication result=write(x,"ctx candidate write \"!@x @y @z edge(x,y)&&edge(y,z)->path(x,z);\"");
            require(result.status==ContextUniversalCandidatePublication.Status.PUBLISHED && result.audit.verified,"three-variable native join publishes only after family model and audit");
            require(Boolean.TRUE.equals(x.mind.query("?path(1,2);",null,false)),"ordinary native query observes correct shared middle variable");
            require(!Boolean.TRUE.equals(x.mind.query("?path(2,1);",null,false)),"join does not reverse endpoints");
        }
        try(Fixture x=new Fixture(dir,"ThreeVariableJoin")) {
            require(Boolean.TRUE.equals(x.mind.query("?path(1,2);",null,false)),"three-variable rule persists after reopen");
            try(ContextProductionSnapshotSession lease=ContextProductionSnapshotSession.open(x.db,x.mind,1000)) {require(lease.inventory().x.primary.size()==3,"join consequence is not promoted to an extra primary");}
        }
    }
    private static int count(String text,String needle) {return text.split(java.util.regex.Pattern.quote(needle),-1).length-1;}
    public static void main(String[] args) {
        try {
            Path dir=Files.createTempDirectory("universal-publication-console-");System.setProperty("user.home",dir.toString());
            String safe="ctx candidate write \"!@x left(x)&&right(x)->q(x);\"";
            String clash="ctx candidate write \"!@x left(x)&&right(x)->p(x);\"";
            require(PARSER.parse(safe).getIntent()==CommandIntent.CTX_CANDIDATE_WRITE,"explicit write intent distinct from diagnostic");
            require(PARSER.parse("ctx candidate \"!write(1);\"").getIntent()==CommandIntent.CTX_CANDIDATE,"predicate spelling cannot select write mode");
            require(PARSER.parse("!@x p(x)->q(x);").isCoreLanguage(),"ordinary assertion path unchanged");
            for(String c:new String[]{safe,safe+" rounds=8 local=9 depth=7 instances=3000 models=100000 preparation=123456 events=50000 work=200000 audit=300000 rows=999",safe.replace(" write "," WRITE ")}) {
                String echo=FORMATTER.format(PARSER.parse(c));
                require(echo.equals(FORMATTER.format(PARSER.parse(echo))),"write canonical echo stable");
                require(PARSER.parse(c).getArgument("request").equals(PARSER.parse(echo).getArgument("request")),"all explicit publication bounds round trip");
            }
            for(String bad:new String[]{"ctx candidate write","ctx candidate write ?p(1);","ctx candidate wr \"!p(1);\"",safe+" write=1",safe+" audit=0",safe+" models=1 models=2"}) reject(() -> PARSER.parse(bad),"malformed write request cannot fall through to Core");
            require(new CommandHelpRenderer().render().contains("ctx candidate write <assertion>"),"shared help exposes actual write intent");
            try(Fixture a=new Fixture(dir,"A")) {require(a.mind.compile("!left(1); !p(1);",null,false),"A fixture");}
            try(Fixture b=new Fixture(dir,"B")) {require(b.mind.compile("!right(1); !~p(1);",null,false),"B fixture");}
            try(Fixture x=new Fixture(dir,"X")) {
                require(x.mind.compile("!anchor(1);",null,false),"X fixture");x.db.connectContext("A");x.db.connectContext("B");
                long before=x.db.getRevision();int rows=x.mind.getRules().size(),logs=x.mind.getLog().size();
                ContextUniversalCandidatePublication refused=write(x,clash);
                require(refused.status==ContextUniversalCandidatePublication.Status.COLLISION && refused.audit.verified && refused.audit.oppositions.stream().anyMatch(p -> p.usesCandidate),"explicit command returns real audited masked collision");
                require(refused.before.getRevision()==before && refused.after.getRevision()==before,"declined write reports actual unchanged physical revision");
                require(write(x,safe+" audit=1").status==ContextUniversalCandidatePublication.Status.INCOMPLETE,"inference model cannot bypass final audit stop");
                require(write(x,safe+" work=1").status==ContextUniversalCandidatePublication.Status.INCOMPLETE,"incomplete inference cannot publish");
                require(x.db.getRevision()==before && x.mind.getRules().size()==rows && x.mind.getLog().size()==logs,"declines preserve physical/presentation state");
                String delayed="!@x delayed(x);";
                CanonicalCommandProcessor.Result diagnostic=new CanonicalCommandProcessor().execute(PARSER.parse("ctx candidate \""+delayed+"\""),x.user);
                require(diagnostic.getCandidateAudit().verified,"historical read-only success fixture");
                require(x.mind.compile("!~delayed(1);",null,false),"later opposite actual knowledge");before=x.db.getRevision();
                require(write(x,"ctx candidate write \""+delayed+"\"").status!=ContextUniversalCandidatePublication.Status.PUBLISHED && x.db.getRevision()==before,"old diagnostic is never reused as new publication authority");
                String[] display=console(x.mind,clash+"\n"+safe+" audit=1\n"+safe+"\n"+safe+"\n");
                require(display[0].contains("Candidate write: COLLISION") && display[0].contains("Candidate write: INCOMPLETE") && display[0].contains("Candidate write: PUBLISHED"),"actual Console distinguishes write outcomes");
                require(count(display[0],"Candidate write: PUBLISHED")==1 && display[0].contains("Physical revision:") && display[0].contains("uses candidate=yes"),"Console publishes once and reports real revisions/provenance");
                require(display[1].isEmpty(),"ordinary write outcomes do not become operational errors");
            }
            try(Fixture x=new Fixture(dir,"X")) {
                try(ContextProductionSnapshotSession lease=ContextProductionSnapshotSession.open(x.db,x.mind,1000)) {
                    require(lease.inventory().x.primary.size()==3,"only anchor, explicit opposite fact and one safe candidate are durable primary knowledge");
                    require(lease.inventory().x.primary.stream().anyMatch(r -> r.origin.contains("->q(x)")) && lease.inventory().x.primary.stream().noneMatch(r -> r.origin.contains("->p(x)") || r.origin.equals("!q(1);")),"real candidate durable, declined rule/foreign product absent");
                }
            }
            try(Fixture local=new Fixture(dir,"Local")) {
                require(local.mind.compile("!seed(1);",null,false),"local consequence fixture");
                ContextUniversalCandidatePublication ok=write(local,"ctx candidate write \"!@x seed(x)->ready(x);\"");
                require(ok.status==ContextUniversalCandidatePublication.Status.PUBLISHED && ok.audit.verified && ok.audit.evaluation==ok.trial && ok.after.getRevision()>ok.before.getRevision(),"transport-neutral actual publication report retains fresh exact audit and physical revisions");
                require(Boolean.TRUE.equals(local.mind.query("?ready(1);",null,false)),"ordinary native query observes committed consequence");
            }
            FailOnceUser failShared=new FailOnceUser();long durable;
            try(Fixture x=new Fixture(dir,"FailShared",failShared)) {
                long before=x.db.getRevision();failShared.fail=true;TransactionSettlementException seen=null;
                try {write(x,"ctx candidate write \"!@x committed(x);\"");} catch(TransactionSettlementException expected) {seen=expected;}
                require(seen!=null && seen.getOutcome()==TransactionSettlementException.Outcome.COMMITTED && seen.getCause()==failShared.injected && seen.isReservationConsumed(),"shared dispatch preserves original committed failure outcome/cause");
                require(failShared.failures==1 && x.db.getRevision()>before,"one durable settlement, no hidden dispatch retry");durable=x.db.getRevision();
            }
            try(Fixture x=new Fixture(dir,"FailShared")) {require(x.db.getRevision()==durable && Boolean.TRUE.equals(x.mind.query("?committed(9);",null,false)),"committed shared failure persists for unmentioned values after reopen");}
            FailOnceUser failConsole=new FailOnceUser();
            try(Fixture x=new Fixture(dir,"FailConsole",failConsole)) {
                failConsole.fail=true;String[] display=console(x.mind,"ctx candidate write \"!@x durable(x);\"\n");
                require(display[1].contains("Candidate write: COMMITTED; finalization failed; reservation consumed=true") && display[1].contains("injected post-durable finalization error"),"Console truthfully reports committed finalization failure");
                require(!display[1].contains("Candidate write refused") && !display[0].contains("Candidate write: PUBLISHED") && !display[0].contains("No assertion published"),"committed failure is neither clean success nor a claim of declined write");
                require(failConsole.failures==1,"Console does not retry failed finalization");
            }
            try(Fixture x=new Fixture(dir,"FailConsole")) {
                require(Boolean.TRUE.equals(x.mind.query("?durable(99);",null,false)),"Console committed failure survives reopen");
                try(ContextProductionSnapshotSession lease=ContextProductionSnapshotSession.open(x.db,x.mind,1000)) {require(lease.inventory().x.primary.size()==1,"no duplicate primary after committed Console failure");}
            }
            languageBoundaries(dir);
            threeVariablePublication(dir);
            User user=new User();user.setCurrentMind(new Mind(user));reject(() -> new CanonicalCommandProcessor().execute(PARSER.parse(safe),user),"write without actual provider refuses");
            System.out.println("CONTEXT_UNIVERSAL_PUBLICATION_CONSOLE_PASS checks="+checks);System.exit(0);
        } catch(Throwable failure) {failure.printStackTrace();System.exit(1);}
    }
}
