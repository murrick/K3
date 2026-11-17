/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;import org.kanger.*;
/** Explicit diagnostic, not a default passed contract: first sidecar creation and older disconnected history. */
public final class SymbolicDisconnectedHistoryProbe {
    private static Mind open(DB db,Path dir,String name)throws Exception{User user=new User();user.setDatabaseDir(dir.toString()+java.io.File.separator);db.init(user);Mind root=new Mind(user);user.setCurrentMind(root);Mind mind=(Mind)root.useStorage(name);user.setCurrentMind(mind);return mind;}
    private static void close(Mind mind)throws Exception{((User)mind.getUser()).setCurrentMind(mind.closeStorage());}
    public static void main(String[] args)throws Exception{
        System.setProperty("user.home",Files.createTempDirectory("history-gap-home-").toString());Path dir=Files.createTempDirectory("disconnected-history-gap-");DB target=new DB();Mind t=open(target,dir,"Rules");try{if(!t.compile("!target(1);",null,false))throw new AssertionError("target fixture");}finally{close(t);}
        DB db=new DB();Mind m=open(db,dir,"Source");try{
            if(!m.compile("!marker(9);",null,false))throw new AssertionError("first revision");long first=db.getRevision();
            if(!m.compile("!second(1);",null,false))throw new AssertionError("second revision");
            boolean before=db.exists("Source@"+first);db.connectContext("Rules");m.saveCheckedSymbolicContextConnections();boolean after=db.exists("Source@"+first);String code="NONE";
            try(ContextSnapshot old=ContextSnapshot.open(dir.resolve("Source"),first)){}catch(org.kanger.exception.StorageLifecycleException error){code=error.getErrorCode().name();}
            System.out.println("DISCONNECTED_HISTORY_PROBE source=Source revision="+first+" readable_before="+before+" readable_after="+after+" current="+db.getRevision()+" error="+code);
            if(before&&!after)System.out.println("DISCONNECTED_HISTORY_GAP_REPRODUCED");else System.out.println("DISCONNECTED_HISTORY_GAP_NOT_REPRODUCED");
        }finally{close(m);}
    }
}
