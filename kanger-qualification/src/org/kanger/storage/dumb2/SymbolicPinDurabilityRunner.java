/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.nio.file.*;
import org.kanger.*;
import org.kanger.exception.TransactionSettlementException;
import org.kanger.storage.dumb2.adapter.RuleV1Adapter;
import org.kanger.storage.dumb2.descriptor.*;
import org.kanger.units.Rule;

/** A target barrier failure must precede the consumer marker and preserve explicit retry. */
public final class SymbolicPinDurabilityRunner {
    private static int checks;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static Mind open(DB db,Path directory,String name)throws Exception{
        User u=new User();u.setDatabaseDir(directory.toString()+java.io.File.separator);db.init(u);Mind root=new Mind(u);u.setCurrentMind(root);
        Mind m=(Mind)root.useStorage(name);u.setCurrentMind(m);return m;
    }
    private static void close(Mind m)throws Exception{((User)m.getUser()).setCurrentMind(m.closeStorage());}
    private static ContextStore store(DB db)throws Exception{java.lang.reflect.Field f=DB.class.getDeclaredField("context");f.setAccessible(true);return (ContextStore)f.get(db);}
    private static void historicalLayout(DB db,Mind m)throws Exception{
        ContextStore s=store(db);ContextBase base=(ContextBase)db.getBase("rules");Rule rule=null;
        for(org.kanger.interfaces.IRule r:m.getRules())if(!r.isGenerated())rule=(Rule)r;
        need(rule!=null,"source original for actual reindex");PersistentRecord row=((ContextStep)base.get(rule.getId())).getPersistentRecord();
        TypeDefinition type=s.registerType(RuleV1Adapter.TYPE_NAME,RuleV1Adapter.INSTANCE.getDescriptor());
        base.update(new ContextStep(base,new PersistentRecord(row.getId(),row.getHash(),row.getNextId(),type.getTypeCode(),
                StructuralValueCodec.encode(type.getDescriptor(),RuleV1Adapter.INSTANCE.project(rule,m)))));db.flush();
    }
    private static boolean barrierCause(Throwable error){
        for(Throwable t=error;t!=null;t=t.getCause())if("Injected directory persistence barrier failure".equals(t.getMessage()))return true;
        return false;
    }
    private static void run(String operation)throws Exception{
        Path root=Files.createTempDirectory("pin-barrier-"),producer=root.resolve("producer-db/Rules"),consumer=root.resolve("consumer-db/Source"),fork=root.resolve("fork-db/Fork");
        DB target=new DB();Mind tm=open(target,producer.getParent(),"Rules");
        try{need(tm.compile("!@u rel(1,u,1)->middle(u); !@u middle(u)->tail(u);",null,false),"independent producer");}finally{close(tm);}
        DB db=new DB();Mind m=open(db,consumer.getParent(),"Source");long expected;
        try{
            need(m.compile("!marker(9);",null,false),"consumer original");db.flush();
            if("reindex".equals(operation))historicalLayout(db,m);
            db.connectContext(producer.toString());db.applyConnectionCommand(m,producer.toString(),"!$u rel(1,u,1);");
            if(!"save".equals(operation))m.saveCheckedSymbolicContextConnections();
            long before=db.getRevision(),targetBefore=RevisionStore.read(ContextStore.revisionPath(producer));
            byte[] marker=Files.readAllBytes(ContextStore.revisionPath(consumer));
            Throwable refused=null;
            try(SymbolicMarkerFaults fault=new SymbolicMarkerFaults(ContextStore.revisionPath(producer),SymbolicMarkerFaults.Point.PIN_BARRIER_FAILURE)){
                try{
                    if("native".equals(operation))m.writeCheckedSymbolicContextAssertion("!safe(8);",false);
                    else if("save".equals(operation))m.saveCheckedSymbolicContextConnections();
                    else if("reindex".equals(operation))db.reindex(null,m);
                    else try(ContextSnapshot source=ContextSnapshot.open(consumer);ContextStore ignored=ContextStore.fork(source,fork)){}
                }catch(Exception failure){refused=failure;}
                need(refused!=null&&barrierCause(refused),"operation reaches exact producer directory barrier and refuses");
                if("native".equals(operation))need(refused instanceof TransactionSettlementException&&((TransactionSettlementException)refused).isReservationConsumed(),"accepted native delta is not resubmitted");
                need(db.getRevision()==before&&java.util.Arrays.equals(marker,Files.readAllBytes(ContextStore.revisionPath(consumer))),"consumer marker stays unchanged");
                need(!store(db).hasUnconfirmedPublication(),"pre-marker target failure is not an uncertain consumer publication");
                need(RevisionStore.read(ContextStore.revisionPath(producer))==targetBefore,"stabilization never advances producer revision");
                if("save".equals(operation))need(db.federationSnapshot().hasWorkingChanges(),"unsaved connection intent retained");
                if("fork".equals(operation))need(!Files.exists(ContextStore.revisionPath(fork))&&!Files.exists(ContextStore.contextPath(fork)),"failed fork cannot acknowledge a broken exported pin");
            }
            DmzCandidateSession.requireSettledPhysicalQ(m);need(true,"no leaked child reservation");
            if("native".equals(operation))((User)m.getUser()).checkpoint(m);
            else if("save".equals(operation))m.saveCheckedSymbolicContextConnections();
            else if("reindex".equals(operation))db.reindex(null,m);
            else try(ContextSnapshot source=ContextSnapshot.open(consumer);ContextStore result=ContextStore.fork(source,fork)){
                need(result.getRevision()==1,"fork retry succeeds once");
                need(ConnectionStore.read(fork,new RevisionRef(result.getContextId(),1)).size()==1,"fork retains its exact direct pin");
            }
            expected=before+("fork".equals(operation)?0:1);need(db.getRevision()==expected,"retry publishes or preserves exactly one consumer revision");
            need(m.saveCheckedSymbolicContextConnections()==expected,"subsequent no-op does not duplicate publication");
            need(Boolean.TRUE.equals(m.query("?marker(9);")),"original knowledge retained");
            if("native".equals(operation))need(Boolean.TRUE.equals(m.query("?safe(8);")),"accepted native delta survives retry");
            need(RevisionStore.read(ContextStore.revisionPath(producer))==targetBefore,"producer remains independent");
        }finally{close(m);}
        db=new DB();m=open(db,consumer.getParent(),"Source");try{
            need(db.getRevision()==expected,"cold reopen selects confirmed revision");
            need(db.dmzTopology().size()==1,"cold exact direct topology retained");
        }finally{close(m);}
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("user.home",Files.createTempDirectory("pin-barrier-home-").toString());
        for(String operation:new String[]{"save","native","reindex","fork"})run(operation);
        System.out.println("SYMBOLIC_PIN_DURABILITY_PASS checks="+checks);
    }
}
