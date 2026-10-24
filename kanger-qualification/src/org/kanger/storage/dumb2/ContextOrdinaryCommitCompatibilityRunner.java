/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.*;
import org.kanger.command.*;
import org.kanger.CanonicalCommandProcessor;
import org.kanger.exception.TransactionSettlementException;

/** A write-policy handoff must preserve observational commits and committed publication reload. */
public final class ContextOrdinaryCommitCompatibilityRunner {
    static class Fixture extends ContextSevenPremiseNativeWriteRunner.Fixture {Fixture(Path dir,String name)throws Exception{super(dir,name);}}
    static String fingerprint(Fixture f)throws Exception{return org.kanger.ContextProductionInventory.inspect(f.root,f.db.getContextId(),f.db.getRevision(),true,Collections.emptyList(),1000).fingerprint;}
    static int checks;static final List<String> failures=new ArrayList<>();
    static void require(boolean b,String why){++checks;if(!b)failures.add(why);}
    static void command(Fixture f,String text)throws Exception{new CanonicalCommandProcessor().execute(new CommandParser().parse(text),f.user);}
    public static void main(String[] args){try{
        Path dir=Files.createTempDirectory("ordinary-commit-compatibility-");System.setProperty("user.home",dir.toString());
        ContextSevenPremiseNativeWriteRunner.create(dir,"Rules","!@x p(x)->q(x);");ContextSevenPremiseNativeWriteRunner.create(dir,"Facts","!p(1);");
        for(boolean publish:new boolean[]{false,true}){
            String name=publish?"Publication":"Observation";long settled=0;boolean operationFailed=false,publicationSucceeded=false;
            try(Fixture f=new Fixture(dir,name)){
                require(f.root.compile("!anchor(1);",null,false),"standalone source");command(f,"ctx connect Rules trust own");command(f,"ctx connect Facts trust own");long revision=f.db.getRevision();String before=fingerprint(f);
                try{
                    if(publish){command(f,"ctx publish ordinary-compatibility");publicationSucceeded=true;require(f.db.getRevision()>revision,"configuration publication advances revision");}
                    else require(Boolean.TRUE.equals(f.root.query("?q(1);",null,false)),"trusted group read proves native joint result");
                }catch(Exception error){operationFailed=true;System.out.println(name+" FAILURE "+error+" cause="+error.getCause());if(error instanceof TransactionSettlementException)System.out.println("POST_SETTLEMENT "+error);require(false,name+" must remain usable: "+error);}
                settled=f.db.getRevision();require(f.db.federationSnapshot().getConnections().size()==2,"topology retained");if(!publish)require(settled==revision&&fingerprint(f).equals(before),"observation does not mutate physical source");
            }catch(Exception error){System.out.println(name+" CLOSE_FAILURE "+error+" cause="+error.getCause());if(!operationFailed)require(false,name+" close/checkpoint must remain usable: "+error);}
            if(publish)try(ContextSnapshot snapshot=ContextSnapshot.open(dir.resolve(name))){require(snapshot.getRevision()>0&&(!publicationSucceeded||snapshot.getRevision()==settled),"publication physical state remains accessible");}
        }
        if(!failures.isEmpty())throw new AssertionError(failures.toString());System.out.println("CONTEXT_ORDINARY_COMMIT_COMPATIBILITY_PASS checks="+(checks+ContextSevenPremiseNativeWriteRunner.checks));System.exit(0);
    }catch(Throwable e){System.out.println("CONTEXT_ORDINARY_COMMIT_COMPATIBILITY_FAIL checks="+checks+" failures="+failures.size());e.printStackTrace();System.exit(1);}}
}
