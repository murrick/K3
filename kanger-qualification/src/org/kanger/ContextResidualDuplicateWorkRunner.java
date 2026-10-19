/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import java.lang.reflect.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextOrdinaryResidualRounds.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;

/** Actual wide receipt duplicate replay must not repeatedly recount distinct clauses. */
public final class ContextResidualDuplicateWorkRunner {
    static int checks;static Method add;
    static void require(boolean v,String why){++checks;if(!v)throw new AssertionError(why);}
    static boolean put(List<Product> pool,Clause clause,Support proof,Runnable work)throws Exception{
        try{return (Boolean)add.invoke(null,pool,clause,proof,16,work,new LedgerLimits(256,1024));}
        catch(InvocationTargetException e){if(e.getCause() instanceof Exception)throw (Exception)e.getCause();throw e;}
    }
    public static void main(String[] args){try{
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("duplicate-work-").toString());
        Snapshot x=ContextResidualRequestLimitsRunner.snapshot(true,ContextResidualRequestLimitsRunner.theory(7)),a=ContextResidualRequestLimitsRunner.snapshot(false,"!q(1);");
        Frontier input=ContextOrdinaryResidualRounds.start(x,Collections.singletonList(a),64000000,new LedgerLimits(256,1024));
        Receipt receipt=ContextOrdinaryResidualRounds.inspect(input,a,16,64000000,500000);
        require(receipt.verified&&receipt.localProofs.size()==135&&receipt.products.size()==127,"actual complete wide ordinary receipt");
        add=ContextOrdinaryResidualRounds.class.getDeclaredMethod("add",List.class,Clause.class,Support.class,int.class,Runnable.class,LedgerLimits.class);add.setAccessible(true);
        List<Product> pool=new ArrayList<>(receipt.localProofs),before=new ArrayList<>(pool);Product last=pool.get(pool.size()-1);long[] calls={0};boolean unchanged=true;
        for(int i=0;i<100;i++)unchanged&=!put(pool,last.clause,last.proof,()->++calls[0]);
        System.out.println("RESIDUAL_DUPLICATE_WORK rows="+pool.size()+" repeats=100 calls="+calls[0]);
        require(unchanged&&pool.equals(before),"all duplicate replays preserve exact clause/support/depth objects and order");
        require(calls[0]<=50000,"known duplicate replay fits bounded50000 work instead of recounting all clause pairs");
        Runnable bounded=ContextGroundProductionRounds.budget(50000);for(int i=0;i<100;i++)require(!put(pool,last.clause,last.proof,bounded),"real shared budget accepts duplicate replay without mutation");
        Runnable allBudget=ContextGroundProductionRounds.budget(20000);boolean allUnchanged=true;
        for(Product p:before)allUnchanged&=!put(pool,p.clause,p.proof,allBudget);
        require(allUnchanged&&pool.equals(before),"every actual unit/compound duplicate preserves whole Source/depth and ordering within one bounded sweep");
        Support longer=new Support(last.proof.sources,17);require(!put(pool,last.clause,longer,ContextGroundProductionRounds.budget(50000)),"longer duplicate cannot overwrite shortest proof even beyond depth bound");
        Support shorter=new Support(last.proof.sources,0);require(put(pool,last.clause,shorter,ContextGroundProductionRounds.budget(50000))&&pool.size()==135,"same-source shorter proof replaces in place");
        Set<Source> sources=new LinkedHashSet<>(last.proof.sources);sources.add(new Source(new UUID(0,999),1,0,false,"independent"));Support independent=new Support(sources,1);
        require(put(pool,last.clause,independent,ContextGroundProductionRounds.budget(50000))&&pool.size()==136,"different whole-source recipe is retained as a separate proof");
        require(pool.stream().anyMatch(p->p.proof.equals(shorter)&&p.clause.equivalent(last.clause))&&pool.stream().anyMatch(p->p.proof.equals(independent)&&p.clause.equivalent(last.clause)),"shortest original and extended source alternatives coexist");
        System.out.println("CONTEXT_RESIDUAL_DUPLICATE_WORK_PASS checks="+checks);System.exit(0);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
