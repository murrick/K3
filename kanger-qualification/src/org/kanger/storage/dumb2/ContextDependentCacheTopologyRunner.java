/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import org.kanger.*;

import java.nio.file.*;
import java.util.*;
import org.kanger.storage.dumb2.DB;
import org.kanger.interfaces.internal.IContextFederation.Snapshot;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.enums.StorageLifecycleErrorCode;

/** Required public cached topology operations, not expected unsupported-refusal controls. */
public final class ContextDependentCacheTopologyRunner {
    private static int checks;
    private static void require(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static Mind open(DB db,Path directory,String name)throws Exception{User u=new User();u.setDatabaseDir(directory.toString()+java.io.File.separator);db.init(u);Mind root=new Mind(u);u.setCurrentMind(root);Mind m=(Mind)root.useStorage(name);u.setCurrentMind(m);return m;}
    private static void close(Mind m)throws Exception{((User)m.getUser()).setCurrentMind(m.closeStorage());}
    private static void seed(Path directory,String name,String text)throws Exception{Mind m=open(new DB(),directory,name);try{require(m.compile(text,null,false),"fixture "+name);}finally{close(m);}}
    public static void main(String[] args)throws Exception{
        Path directory=Files.createTempDirectory("dependent-cache-topology-");System.setProperty("user.home",directory.toString());
        seed(directory,"Cached","!@a $u @b rel(a,u,b); !seed(1); !@x seed(x)->middle(x); !@x middle(x)->tail(x);");
        seed(directory,"Compatible","!marker(1);");seed(directory,"Conflict","!~tail(1);");
        for(boolean reverse:new boolean[]{false,true}){
            String source=reverse?"Compatible":"Cached",target=reverse?"Cached":"Compatible";
            DB db=new DB();Mind m=open(db,directory,source);long published;
            try{
                if(!reverse)require(ContextProductionInventory.inspect(m,db.getContextId(),db.getRevision(),true,Collections.emptyList(),1000).generated.size()>=2,"physical source really contains persisted native cache rows");
                long targetRevision=db.versionHistory(target).getCurrentRevision();
                db.connectContext(target);
                if(reverse){ContextConnection c=db.dmzTopology().get(0);require(ContextProductionInventory.inspect(c.layer(),c.getTarget().getContextId(),c.getTarget().getRevision(),false,c.getInitialization(),1000).generated.size()>=2,"physical foreign pin really contains native cache rows");}
                published=db.saveConnections(m);Snapshot view=db.federationSnapshot();
                require(!view.hasWorkingChanges()&&view.getConnections().size()==1&&view.getConnections().get(0).getPinnedRevision()==targetRevision,"cached compatible connection publishes at exact pin in both directions");
                require(db.versionHistory(target).getCurrentRevision()==targetRevision,"qualification never publishes the target");
            }finally{close(m);}
            db=new DB();m=open(db,directory,source);
            try{Snapshot view=db.federationSnapshot();require(db.getRevision()==published&&view.getConnections().size()==1&&!view.hasWorkingChanges(),"cached topology survives fresh reopen");}finally{close(m);}
        }
        DB db=new DB();Mind m=open(db,directory,"Cached");
        try{
            Snapshot before=db.federationSnapshot();String state=ContextProductionInventory.inspect(m,db.getContextId(),db.getRevision(),true,Collections.emptyList(),1000).fingerprint;long revision=db.getRevision();boolean refused=false;
            try{db.connectContext("Conflict");}catch(StorageLifecycleException failure){refused=failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT&&!failure.getCollisions().isEmpty();}
            require(refused,"derived tail conflict is rejected with actual evidence");
            Snapshot after=db.federationSnapshot();boolean pins=after.getConnections().size()==before.getConnections().size();
            for(int i=0;pins&&i<before.getConnections().size();i++){
                org.kanger.interfaces.internal.IContextFederation.Connection a=before.getConnections().get(i),b=after.getConnections().get(i);
                pins=a.getTargetContextId().equals(b.getTargetContextId())&&a.getPinnedRevision()==b.getPinnedRevision()&&a.getLocator().equals(b.getLocator())
                        &&a.getInitialization().equals(b.getInitialization())&&Objects.equals(a.getTrustGroup(),b.getTrustGroup());
            }
            DmzCandidateSession.requireSettledPhysicalQ(m);
            require(db.getRevision()==revision&&pins&&state.equals(ContextProductionInventory.inspect(m,db.getContextId(),db.getRevision(),true,Collections.emptyList(),1000).fingerprint)&&m.getUser().getCurrentMind()==m,"failed cached topology leaves revision, pins and state unchanged");
        }finally{close(m);}
        System.out.println("CONTEXT_DEPENDENT_CACHE_TOPOLOGY_PASS checks="+checks);
    }
}
