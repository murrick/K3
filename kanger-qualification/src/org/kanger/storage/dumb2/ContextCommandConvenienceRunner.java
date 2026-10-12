/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.io.File;
import java.nio.file.*;
import org.kanger.*;
import org.kanger.command.*;
import org.kanger.interfaces.internal.IContextFederation;

/** Exact CURRENT pinning, refusal preservation and persisted free-text descriptions. */
public final class ContextCommandConvenienceRunner {
    private static int checks;
    private static Path dir;
    private static final CommandParser parser=new CommandParser();
    private static final CanonicalCommandProcessor processor=new CanonicalCommandProcessor();
    private static final class Session implements AutoCloseable {
        final User user=new User(); final DB db=new DB();
        Session(String name) throws Exception {
            user.setDatabaseDir(dir.toString()+File.separator); db.init(user);
            Mind root=new Mind(user);user.setCurrentMind(root);user.setCurrentMind(root.useStorage(name));
        }
        void command(String text) throws Exception {
            CanonicalCommandProcessor.Result result=processor.execute(parser.parse(text),user);
            require(result.isSuccess(),"command succeeds: "+text);user.setCurrentMind(result.getMind());
        }
        void fact(String text) throws Exception { require(((Mind)user.getCurrentMind()).compile(text,null,false),"native input: "+text); }
        public void close() throws Exception {user.setCurrentMind(user.getCurrentMind().closeStorage());}
    }
    public static void main(String[] args) {
        try {
            dir=Files.createTempDirectory("context-command-convenience-");System.setProperty("user.home",dir.toString());
            try(Session a=new Session("A")) {a.fact("!left(1);");}
            try(Session b=new Session("B")) {b.fact("!safe(1);");}
            try(Session x=new Session("X")) {
                x.command("start");x.fact("!guard(1);");x.command("commit Первая  ревизия");
                description(x,"Первая  ревизия");
                x.command("ctx connect A trust commune");x.command("ctx connect B");
                x.command("ctx publish Подключили A и B");description(x,"Подключили A и B");
                long old=pin(x,"A"),source=x.db.getRevision();
                try(Session a=new Session("A")) {a.fact("!left(2);");a.command("commit новая ревизия A");}
                x.command("ctx sw A cur");
                require(pin(x,"A")>old && x.db.getRevision()==source,"CURRENT updates only working target pin");
                require("commune".equals(connection(x,"A").getTrustGroup()),"TRUST group preserved");
                long selected=pin(x,"A");
                try(OperationSnapshot published=OperationSnapshot.open(dir.resolve("X"))) {
                    require(published.getConnections().getConnections().stream().anyMatch(c->c.getTarget().getRevision()==old),"published topology not rewritten by switch");
                }
                try(Session a=new Session("A")) {a.fact("!left(3);");a.command("commit another A revision");}
                require(pin(x,"A")==selected && connection(x,"A").getCurrentRevision()>selected,"pin stays exact after another publication");
                x.command("ctx switch A "+old);require(pin(x,"A")==old,"numeric selector still works");
                x.command("ctx switch A current");require(pin(x,"A")>selected,"fresh CURRENT request resolves fresh published revision");
                long beforeB=pin(x,"B"),beforeA=pin(x,"A");
                try(Session b=new Session("B")) {b.fact("!~guard(1);");b.command("commit conflicting revision");}
                try {x.command("ctx switch B current");throw new AssertionError("Expected topology refusal");}
                catch(Exception expected) {++checks;}
                require(pin(x,"B")==beforeB && pin(x,"A")==beforeA && x.db.getRevision()==source,"refused CURRENT preserves pins and source revision");
                try {x.command("ctx switch Missing current");throw new AssertionError("Expected missing connection refusal");}
                catch(Exception expected) {++checks;}
                x.command("ctx publish contains \"quoted\" fragment");description(x,"contains \"quoted\" fragment");
                x.command("start");x.fact("!marker(4);");x.command("commit \"Quoted description\"");description(x,"Quoted description");
            }
            try(Session x=new Session("X")) {description(x,"Quoted description");}
            System.out.println("CONTEXT_COMMAND_CONVENIENCE_PASS checks="+checks);System.exit(0);
        } catch(Throwable failure) {failure.printStackTrace();System.exit(1);}
    }
    private static void description(Session s,String wanted) throws Exception {
        require(s.db.versionHistory(null).getRevisions().stream().anyMatch(v->v.getRevision()==s.db.getRevision() && wanted.equals(v.getDescription())),"description persisted: "+wanted);
    }
    private static IContextFederation.Connection connection(Session s,String name) throws Exception {
        for(IContextFederation.Connection c:s.db.federationSnapshot().getConnections())if(name.equals(c.getLocator()))return c;
        throw new AssertionError("Missing connection "+name);
    }
    private static long pin(Session s,String name) throws Exception {return connection(s,name).getPinnedRevision();}
    private static void require(boolean value,String message) {++checks;if(!value)throw new AssertionError(message);}
}
