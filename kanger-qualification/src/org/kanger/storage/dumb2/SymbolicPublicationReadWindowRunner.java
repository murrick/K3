/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;import java.util.*;import java.io.IOException;
import org.kanger.*;

/** Saving must not reread connection metadata after the irreversible marker advance. */
public final class SymbolicPublicationReadWindowRunner {
    private static int checks;
    private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static Mind open(DB db,Path d,String name)throws Exception{User u=new User();u.setDatabaseDir(d.toString()+java.io.File.separator);db.init(u);Mind r=new Mind(u);u.setCurrentMind(r);Mind m=(Mind)r.useStorage(name);u.setCurrentMind(m);return m;}
    private static void close(Mind m)throws Exception{((User)m.getUser()).setCurrentMind(m.closeStorage());}
    private static void seed(Path d,String name,String text)throws Exception{DB db=new DB();Mind m=open(db,d,name);try{need(m.compile(text,null,false),"metadata-window fixture");}finally{close(m);}}
    public static void main(String[] args)throws Exception{
        System.setProperty("user.home",Files.createTempDirectory("metadata-window-home-").toString());Path d=Files.createTempDirectory("metadata-window-");seed(d,"Source","!marker(9);");seed(d,"Rules","!@u rel(1,u,1)->middle(u); !@u middle(u)->tail(u);");DB db=new DB();Mind m=open(db,d,"Source");
        try{db.connectContext("Rules");db.applyConnectionCommand(m,"Rules","!$u rel(1,u,1);");SymbolicCacheFamilyInspection before=db.inspectSymbolicCacheFamily(m);Path location=before.local.location;long revision=db.getRevision(),target=db.versionHistory("Rules").getCurrentRevision();
            try(SymbolicPublicationFaults fault=new SymbolicPublicationFaults(db,location,SymbolicPublicationFaults.Point.METADATA_READ)){
                boolean readFailure=false;long published=-1;try{published=m.saveCheckedSymbolicContextConnections();}catch(IOException expected){readFailure=true;}
                need(!readFailure&&fault.hits==0,"already-published topology must not depend on another fallible metadata read");need(published==revision+1&&db.getRevision()==published,"one complete revision published");
            }
            for(ContextConnection c:db.dmzTopology())need(c.getCertificate().matches(new RevisionRef(db.getContextId(),db.getRevision()),c.getTarget(),c.getCertificate().getSemanticVersion()),"activated certificate binds current source");
            db.inspectSymbolicCacheFamily(m).requireCurrentModelled(db,m);need(true,"fresh family qualifies after save");need(m.saveCheckedSymbolicContextConnections()==revision+1,"no-op save consumes the current qualified vector without another revision");need(m.writeCheckedSymbolicContextAssertion("!safe(8);",false),"next actual assertion can publish");need(db.versionHistory("Rules").getCurrentRevision()==target,"private root never alters physical target");
        }finally{close(m);}
        db=new DB();m=open(db,d,"Source");try{db.inspectSymbolicCacheFamily(m).requireCurrentModelled(db,m);need(db.dmzTopology().size()==1&&db.dmzTopology().get(0).getInitialization().equals(Collections.singletonList("!$u rel(1,u,1);")),"complete saved recipe survives cold reopen");need(ContextProductionInventory.inspect(m,db.getContextId(),db.getRevision(),true,Collections.emptyList(),1000).primary.size()==2,"both source originals survive cold reopen");}finally{close(m);}
        System.out.println("SYMBOLIC_PUBLICATION_READ_WINDOW_PASS checks="+checks);
    }
}
