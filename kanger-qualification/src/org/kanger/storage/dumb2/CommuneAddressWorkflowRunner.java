/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.command.*;
import org.kanger.interfaces.IContextResults;
import org.kanger.interfaces.internal.IContextFederation;

/** Public canonical routing to the existing qualified commune, including binary native inference. */
public final class CommuneAddressWorkflowRunner {
    private static int checks;
    private static Path dir;
    private static final CommandParser parser=new CommandParser();
    private static final CanonicalCommandProcessor processor=new CanonicalCommandProcessor();
    private static final class Session implements AutoCloseable {
        final User user=new User(); final DB db=new DB();
        Session(String name) throws Exception {
            user.setDatabaseDir(dir.toString()+File.separator);db.init(user);
            Mind root=new Mind(user);user.setCurrentMind(root);user.setCurrentMind(root.useStorage(name));
        }
        Mind mind() { return (Mind)user.getCurrentMind(); }
        CanonicalCommandProcessor.Result command(String text) throws Exception {
            CanonicalCommandProcessor.Result result=processor.execute(parser.parse(text),user);
            require(result.isSuccess(),"canonical command: "+text);user.setCurrentMind(result.getMind());return result;
        }
        void fact(String text) throws Exception {require(mind().compile(text,null,false),"native input: "+text);}
        public void close() throws Exception {user.setCurrentMind(mind().closeStorage());}
    }
    interface Action {void run() throws Exception;}
    private static void reject(Action action,String message) throws Exception {
        try {action.run();throw new AssertionError("Expected refusal: "+message);}
        catch(org.kanger.exception.CommandErrorException expected) {require(expected.toString().contains(message),"specific refusal: "+message);}
    }
    private static void truth(Session s,String locator,String query,IContextResults.FrontierTruth wanted) throws Exception {
        IContextFederation.QueryResult result=s.command("ctx ask "+locator+" "+query).getFederationQueryResult();
        require(result.getResultTruth()==wanted,"truth for "+locator+" "+query);
    }
    public static void main(String[] args) {
        try {
            dir=Files.createTempDirectory("commune-address-");System.setProperty("user.home",dir.toString());
            try(Session s=new Session("Natives")) {s.fact("!@x @y father(x,y) -> male(x);");}
            try(Session s=new Session("Facts")) {s.fact("!father(John,Tom);");}
            try(Session s=new Session("Other")) {s.fact("!outside(1);");}
            try(Session s=new Session("own")) {s.fact("!physical(1);");}
            try(Session s=new Session("X")) {
                s.fact("!local(1);");s.command("ctx connect Natives trust own");s.command("ctx connect Facts trust own");
                s.command("ctx connect Other trust another");s.command("ctx publish commune fixture");
                String before=DmzSourceMaterializationQualification.fingerprint(s.mind());long revision=s.db.getRevision();Mind current=s.mind();
                truth(s,"own","?male(John);",IContextResults.FrontierTruth.TRUE);
                truth(s,"own","?father(John,Tom);",IContextResults.FrontierTruth.TRUE);
                truth(s,"Facts","?male(John);",IContextResults.FrontierTruth.UNKNOWN);
                truth(s,"own","?local(1);",IContextResults.FrontierTruth.UNKNOWN);
                truth(s,"own","?outside(1);",IContextResults.FrontierTruth.UNKNOWN);
                truth(s,"another","?outside(1);",IContextResults.FrontierTruth.TRUE);
                IContextFederation.RuleBlock all=s.command("ctx ask own rules all").getContextRules().get(0);
                require("own".equals(all.locator) && "own".equals(all.revision.getCommune()) && all.revision.getCommuneMembers().size()==2,"commune projection carries exact member pins");
                require(all.rules.stream().anyMatch(r->r.statement.contains("father(John,Tom)")) && all.rules.stream().anyMatch(r->r.statement.contains("@x")),"rules include both member inputs");
                IContextFederation.RuleRow generated=s.command("ctx ask own rules produced").getContextRules().get(0).rules.stream().filter(r->r.statement.contains("male(John)")).findFirst().get();
                String tree=s.command("ctx ask own rules tree "+generated.id).getContextRules().get(0).rules.get(0).tree.toString();
                require(tree.contains("Cause:") && tree.contains("father"),"generated inference tree belongs to commune view");
                require(!s.command("ctx ask own rules structure "+generated.id).getContextRules().get(0).rules.get(0).tree.isEmpty(),"structure inspection scoped to commune ID");
                s.command("ctx ask own rules comment "+generated.id);
                reject(()->s.command("ctx ask own !leak(1);"),"read-only");
                require(before.equals(DmzSourceMaterializationQualification.fingerprint(s.mind())) && revision==s.db.getRevision() && current==s.mind(),"commune query and refusal preserve X");
                String detached=all.rules.toString();
                s.command("ctx ask Facts !father(Mary,Sarah);");
                truth(s,"own","?male(Mary);",IContextResults.FrontierTruth.TRUE);
                require(s.command("ctx ask own rules").getContextRules().get(0).configured,"member initialization is visible as configured commune");
                require(detached.equals(all.rules.toString()) && all.revision.getCommuneMembers().size()==2,"detached old projection survives re-preparation");
                s.command("ctx disconnect Facts");
                truth(s,"own","?male(John);",IContextResults.FrontierTruth.UNKNOWN);
                s.command("ctx connect Facts trust own");truth(s,"own","?male(John);",IContextResults.FrontierTruth.TRUE);
                truth(s,"own","?male(Mary);",IContextResults.FrontierTruth.UNKNOWN);
                try(Session facts=new Session("Facts")) {facts.fact("!father(Mary,Sarah);");}
                truth(s,"own","?male(Mary);",IContextResults.FrontierTruth.UNKNOWN);
                s.command("ctx switch Facts current");
                truth(s,"own","?male(Mary);",IContextResults.FrontierTruth.TRUE);
                require(s.command("ctx ask own rules all").getContextRules().get(0).revision.getCommuneMembers().stream().anyMatch(p->p.getRevision()>all.revision.getCommuneMembers().stream().filter(old->old.getContextId().equals(p.getContextId())).findFirst().get().getRevision()),"fresh commune projection reports deliberately repinned member");
                s.command("ctx connect own");
                reject(()->s.command("ctx ask own ?physical(1);"),"Ambiguous");
                reject(()->s.command("ctx ask own rules"),"Ambiguous");
                truth(s,"./own","?physical(1);",IContextResults.FrontierTruth.TRUE);
                require(s.command("ctx ask ./own rules").getContextRules().get(0).rules.stream().anyMatch(r->r.statement.contains("physical")),"explicit physical path disambiguates rules");
                s.command("ctx disconnect own");s.command("ctx publish save addressed commune");
            }
            try(Session s=new Session("X")) {truth(s,"own","?male(John);",IContextResults.FrontierTruth.TRUE);require(s.command("ctx ask own rules all").getContextRules().get(0).revision.getCommuneMembers().size()==2,"commune routing survives reopen");}
            System.out.println("COMMUNE_ADDRESS_WORKFLOW_PASS checks="+checks);System.exit(0);
        } catch(Throwable failure) {failure.printStackTrace();System.exit(1);}
    }
    private static void require(boolean value,String message) {++checks;if(!value)throw new AssertionError(message);}
}
