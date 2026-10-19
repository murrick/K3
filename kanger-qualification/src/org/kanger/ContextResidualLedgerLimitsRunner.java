/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import java.lang.reflect.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextOrdinaryResidualRounds.*;

/** Independent clause/proof caps preserve all Source alternatives and shortest depths. */
public final class ContextResidualLedgerLimitsRunner {
    static int checks;static Method add;
    static void require(boolean v,String why){++checks;if(!v)throw new AssertionError(why);}
    static Source source(int n){return new Source(new UUID(0,0),1,n,true,"base");}
    static Support proof(int depth,int... ids){Set<Source>s=new LinkedHashSet<>();for(int id:ids)s.add(source(id));return new Support(s,depth);}
    static boolean put(List<Product> pool,Clause clause,Support proof)throws Exception{
        try{return (Boolean)add.invoke(null,pool,clause,proof,16,ContextGroundProductionRounds.budget(8000000),LedgerLimits.DEFAULT);}
        catch(InvocationTargetException failure){Throwable cause=failure.getCause();if(cause instanceof IllegalStateException)throw (IllegalStateException)cause;throw failure;}
    }
    static void limit(List<Product> pool,Clause clause,Support proof,String reason)throws Exception{
        List<Product> before=new ArrayList<>(pool);String seen=null;try{put(pool,clause,proof);}catch(IllegalStateException expected){seen=expected.getMessage();}
        require(reason.equals(seen),"precise bound and counts: "+seen);require(pool.equals(before),"refused insertion cannot expose a changed ledger prefix");
    }
    static List<Clause> clauses()throws Exception{
        User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);try(ContextProductionCapture capture=ContextProductionCapture.begin(m,10000)){
            for(int i=0;i<65;i++)capture.replay(m,source(i),"!atom"+i+"(1);");List<Clause> result=new ArrayList<>();for(Input input:capture.snapshot().inputs)result.add(input.clauses.get(0));return result;
        }
    }
    static ContextProductionNativeBinding.Snapshot snapshot(int id,boolean local,String text)throws Exception{
        User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);require(m.compile(text,null,false),"actual receipt fixture");
        ContextProductionNativeBinding.Result r=ContextProductionNativeBinding.bind(m,ContextProductionInventory.inspect(m,new UUID(0,id),1,local,Collections.emptyList(),1000),64000000,100000);require(r.bound,"complete native receipt source");return r.snapshot;
    }
    static void incomplete(Barrier b,String reason){require(b.status==Status.INCOMPLETE&&b.frontier==null&&!b.changed&&b.collisions.isEmpty()&&b.reason.equals(reason),"complete refusal cause without pool/evidence prefix: "+b.reason);}
    static void actual()throws Exception{
        StringBuilder text=new StringBuilder();for(int i=1;i<7;i++)text.append("!p").append(i).append("(1);");text.append("!q(1)");for(int i=1;i<7;i++)text.append("&&p").append(i).append("(1)");text.append("->out(1);");
        ContextProductionNativeBinding.Snapshot x=snapshot(10,true,text.toString()),a=snapshot(11,false,"!q(1);"),safe=snapshot(12,false,"!safe(1);");
        Frontier f=ContextOrdinaryResidualRounds.start(x,Arrays.asList(a,safe),64000000);List<Receipt> outputs=new ArrayList<>();
        for(ContextProductionNativeBinding.Snapshot cut:f.cuts){Receipt r=ContextOrdinaryResidualRounds.inspectWithLocalCompositions(f,cut,16,64000000,500000,100000,16);require(!r.verified&&r.products.isEmpty()&&r.collisions.isEmpty()&&r.reason.equals("Residual clause limit: clauses=64, proofs=64"),"actual seven-premise exhaustion is distinct-clause growth: "+r.reason);outputs.add(r);}
        incomplete(ContextOrdinaryResidualRounds.merge(f,outputs,64000000),"Residual receipt incomplete: Residual clause limit: clauses=64, proofs=64");
        Frontier other=ContextOrdinaryResidualRounds.start(x,Arrays.asList(a,safe),64000000);
        incomplete(ContextOrdinaryResidualRounds.merge(other,outputs,64000000),"Residual receipt scope/input/evidence mismatch");
        List<Receipt> duplicate=new ArrayList<>(outputs);duplicate.set(2,outputs.get(0));incomplete(ContextOrdinaryResidualRounds.merge(f,duplicate,64000000),"Residual receipt scope/input/evidence mismatch");
        incomplete(ContextOrdinaryResidualRounds.merge(f,outputs.subList(0,2),64000000),"Incomplete residual family barrier");
        incomplete(ContextOrdinaryResidualRounds.merge(f,outputs,1),"Ground transport work limit");
    }
    public static void main(String[] args){try{
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("residual-ledger-").toString());
        add=ContextOrdinaryResidualRounds.class.getDeclaredMethod("add",List.class,Clause.class,Support.class,int.class,Runnable.class,LedgerLimits.class);add.setAccessible(true);List<Clause> clauses=clauses();
        List<Product> distinct=new ArrayList<>();for(int i=0;i<64;i++)require(put(distinct,clauses.get(i),proof(5,0)),"distinct logical row inserted");
        limit(distinct,clauses.get(64),proof(5,0),"Residual clause limit: clauses=64, proofs=64");
        require(put(distinct,clauses.get(0),proof(4,1))&&distinct.size()==65,"new Source alternative for an existing clause is allowed at clause cap");
        require(put(distinct,clauses.get(0),proof(2,0))&&distinct.size()==65,"shorter existing support replaces depth without requiring another slot");
        List<Product> alternatives=new ArrayList<>();for(int i=0;i<256;i++)require(put(alternatives,clauses.get(0),proof(5,i)),"separate Source alternative inserted");
        limit(alternatives,clauses.get(0),proof(5,256),"Residual proof limit: proofs=256, clauses=1");
        require(put(alternatives,clauses.get(0),proof(3,100))&&alternatives.size()==256,"shorter same-support proof replaces at proof cap");
        require(!put(alternatives,clauses.get(0),proof(6,100))&&alternatives.size()==256&&alternatives.stream().anyMatch(p->p.proof.sources.equals(proof(3,100).sources)&&p.proof.depth==3),"longer duplicate cannot overwrite minimal depth");
        List<Product> whole=new ArrayList<>();put(whole,clauses.get(0),proof(1,0));put(whole,clauses.get(0),proof(1,1));put(whole,clauses.get(0),proof(1,0,1));
        require(whole.size()==3&&whole.stream().filter(p->p.proof.sources.size()==1).count()==2&&whole.stream().filter(p->p.proof.sources.size()==2).count()==1,"independent alternatives and conjunctive dependency remain three distinct proofs");
        actual();
        System.out.println("CONTEXT_RESIDUAL_LEDGER_LIMITS_PASS checks="+checks);System.exit(0);
    }catch(Throwable failure){failure.printStackTrace();System.exit(1);}}
}
