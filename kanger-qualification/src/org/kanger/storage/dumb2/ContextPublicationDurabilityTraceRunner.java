/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.nio.file.*;
import java.io.File;
import org.kanger.*;
import org.kanger.units.Rule;
import org.kanger.storage.dumb2.adapter.RuleV1Adapter;
import org.kanger.storage.dumb2.descriptor.TypeDefinition;
import org.kanger.storage.dumb2.descriptor.StructuralValueCodec;

/** Real production I/O fixture for a syscall audit, NOT a power-loss acceptance gate. */
public final class ContextPublicationDurabilityTraceRunner {
    private static int checks;
    private static void need(boolean b,String reason){checks++;if(!b)throw new AssertionError(reason);}
    private static Mind open(DB db,Path root,String name)throws Exception {
        User user=new User();user.setDatabaseDir(root+File.separator);db.init(user);
        Mind base=new Mind(user);user.setCurrentMind(base);Mind m=(Mind)base.useStorage(name);user.setCurrentMind(m);return m;
    }
    private static void close(Mind m)throws Exception{((User)m.getUser()).setCurrentMind(m.closeStorage());}
    private static ContextStore store(DB db)throws Exception {
        java.lang.reflect.Field f=DB.class.getDeclaredField("context");f.setAccessible(true);return (ContextStore)f.get(db);
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("A fresh audit output directory is required");
        Path audit=Paths.get(args[0]).toAbsolutePath();Files.createDirectory(audit);
        System.setProperty("user.home",audit.resolve("home").toString());Path data=audit.resolve("db");Files.createDirectory(data);
        DB target=new DB();Mind t=open(target,data,"Rules");try{need(t.compile("!target(1);",null,false),"target fixture");}finally{close(t);}
        DB db=new DB();Mind m=open(db,data,"Source");long published;
        try {
            need(m.compile("!anchor(1);",null,false),"initial publication");
            need(m.compile("!second(2);",null,false),"native append publication");
            long before=db.getRevision();db.connectContext("Rules");
            m=(Mind)db.publishContext(m,"trace topology");((User)m.getUser()).setCurrentMind(m);
            need(db.getRevision()==before+1,"topology changes exact source revision");
            // A valid legacy row makes public reindex perform a real physical migration.
            ContextStore s=store(db);ContextBase b=(ContextBase)db.getBase("rules");Rule rule=null;
            for(org.kanger.interfaces.IRule r:m.getRules())if(!r.isGenerated()){rule=(Rule)r;break;}
            need(rule!=null,"original rule for legacy physical fixture");
            PersistentRecord row=((ContextStep)b.get(rule.getId())).getPersistentRecord();
            TypeDefinition type=s.registerType(RuleV1Adapter.TYPE_NAME,RuleV1Adapter.INSTANCE.getDescriptor());
            b.update(new ContextStep(b,new PersistentRecord(row.getId(),row.getHash(),row.getNextId(),type.getTypeCode(),
                    StructuralValueCodec.encode(type.getDescriptor(),RuleV1Adapter.INSTANCE.project(rule,m)))));
            db.flush();before=db.getRevision();db.reindex(null,m);
            need(db.getRevision()==before+1,"reindex actually publishes");published=db.getRevision();
            org.kanger.interfaces.internal.IContextFederation.Revision fork=m.forkContext("Fork");
            need(fork.getRevision()==1,"positive source forks at one");
            need(db.getRevision()==published,"fork leaves source selected");
        }finally{close(m);}
        DB cold=new DB();Mind reopened=open(cold,data,"Source");
        try {
            need(cold.getRevision()==published,"source cold reopen");
            need(Boolean.TRUE.equals(reopened.query("?anchor(1);")),"own fact retained");
            need(Boolean.TRUE.equals(reopened.query("?target(1);")),"saved connection retained");
        }finally{close(reopened);}
        DB fork=new DB();Mind copy=open(fork,data,"Fork");
        try{need(fork.getRevision()==1,"fork cold reopen");need(Boolean.TRUE.equals(copy.query("?anchor(1);")),"fork fact retained");}finally{close(copy);}
        System.out.println("DURABILITY_TRACE_FIXTURE_COMPLETE checks="+checks);
        System.out.println("DURABILITY_TRACE_SCOPE actual creation/append/topology/reindex/fork; no power-loss test");
        System.out.println("DURABILITY_TRACE_SOURCE "+data.resolve("Source"));
        System.out.println("DURABILITY_TRACE_FILESTORE "+Files.getFileStore(data).type());
    }
}
