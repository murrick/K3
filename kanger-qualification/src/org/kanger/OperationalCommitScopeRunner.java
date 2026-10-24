/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.kanger.storage.dumb2.DB;
import org.kanger.interfaces.internal.IData;

/** Operation ownership expires at provider return; ordinary unchanged children carry no privilege. */
public final class OperationalCommitScopeRunner {
    static int checks,calls,refreshes;static boolean reject,mutate;static Mind lastOperational;
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    public static void main(String[] args){try{
        require(java.lang.reflect.Modifier.isFinal(Mind.class.getMethod("isOperationalCommit").getModifiers()),"provider observation cannot be overridden by a caller's Mind subtype");
        Path dir=Files.createTempDirectory("operational-commit-scope-");System.setProperty("user.home",dir.toString());User u=new User();u.setDatabaseDir(dir.toString()+java.io.File.separator);DB db=new DB();db.init(u);Mind initial=new Mind(u);u.setCurrentMind(initial);Mind root=(Mind)initial.useStorage("X");u.setCurrentMind(root);
        Object proxy=java.lang.reflect.Proxy.newProxyInstance(DB.class.getClassLoader(),DB.class.getInterfaces(),(p,m,a)->{
            if(m.getName().equals("validateCommit")){
                ++calls;Mind child=(Mind)a[0];if(child.isOperationalCommit()){
                    ++refreshes;lastOperational=child;AtomicBoolean other=new AtomicBoolean(true);Thread thread=new Thread(()->other.set(child.isOperationalCommit()));thread.start();thread.join();require(!other.get(),"operational frame not visible as permission to another thread");
                    if(mutate)child.compile("!injected(1);",null,false);
                    if(reject)throw new IllegalStateException("injected operational validation refusal");
                }
            }
            try{return m.invoke(db,a);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
        });u.setData((IData)proxy);
        require(root.compile("!base(1);",null,false),"ordinary primary compile");require(calls==1&&refreshes==0,"ordinary authoring never acquires operational frame");
        Mind unchanged=new Mind(root);require(root.commit(unchanged),"ordinary unchanged child commits");require(refreshes==0&&!unchanged.isOperationalCommit(),"unchanged content alone does not create operational permission");
        require(root.qualifyCurrentContext(false).isValid(),"owning native refresh");require(refreshes==1&&!lastOperational.isOperationalCommit(),"native refresh supplies owning frame that expires on success");require(root.checkpointStorageState(),"owning empty checkpoint");require(refreshes==2,"checkpoint supplies owning frame");
        int before=calls;Mind changed;
        try(TechnicalMindTransaction tx=TechnicalMindTransaction.begin(root)){
            changed=tx.mind();require(changed.compile("!newFact(1);",null,false),"prepare primary mutation in technical child");boolean refused=false;try{tx.commitRefresh();}catch(IllegalStateException expected){refused=true;}require(refused,"refresh cannot admit new primary");
        }
        require(calls==before&&!changed.isOperationalCommit()&&root.pendingTransactionCount()==0,"changed-primary refusal precedes provider and releases exact child");require(root.query("?newFact(1);",null,false)==null,"rejected primary did not leak");
        before=calls;
        try(TechnicalMindTransaction tx=TechnicalMindTransaction.begin(root)){
            require(Boolean.TRUE.equals(tx.mind().query("-base(1);",null,false)),"prepare primary deletion");boolean refusedDelete=false;try{tx.commitRefresh();}catch(IllegalStateException expected){refusedDelete=true;}require(refusedDelete,"refresh cannot remove an original primary");
        }
        require(calls==before&&root.pendingTransactionCount()==0&&Boolean.TRUE.equals(root.query("?base(1);",null,false)),"deletion refused before provider, original primary survives");
        mutate=true;
        try(TechnicalMindTransaction tx=TechnicalMindTransaction.begin(root)){boolean rejectedMutation=false;try{tx.commitRefresh();}catch(IllegalStateException expected){rejectedMutation=expected.getMessage().contains("validation changed");}require(rejectedMutation,"provider cannot mutate primary after the entry guard");}
        mutate=false;require(!lastOperational.isOperationalCommit()&&root.pendingTransactionCount()==0&&root.query("?injected(1);",null,false)==null,"post-validation mutation rejected without leaked primary or frame");
        reject=true;Mind refused;
        try(TechnicalMindTransaction tx=TechnicalMindTransaction.begin(root)){refused=tx.mind();boolean seen=false;try{tx.commitRefresh();}catch(IllegalStateException expected){seen=expected.getMessage().contains("injected");}require(seen,"provider refusal propagates");}
        reject=false;require(!refused.isOperationalCommit()&&root.pendingTransactionCount()==0,"refused operational frame expires and reservation is released");
        require(root.compile("!after(1);",null,false),"ordinary authoring remains usable after refusal");require(Boolean.TRUE.equals(root.query("?base(1);",null,false)),"original source truth retained");u.setData(db);u.setCurrentMind(root.closeStorage());
        System.out.println("OPERATIONAL_COMMIT_SCOPE_PASS checks="+checks);System.exit(0);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
