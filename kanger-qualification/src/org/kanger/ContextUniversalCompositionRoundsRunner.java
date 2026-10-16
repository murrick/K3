/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextOrdinaryResidualRounds.*;

/** Source-bound universal carriers cross complete immutable barriers, never storage. */
public final class ContextUniversalCompositionRoundsRunner {
    private static int checks,ids;
    private static final int WORK=16000000;
    private static void require(boolean yes,String why) { ++checks; if(!yes) throw new AssertionError(why); }
    private static Snapshot snapshot(boolean x,String text) throws Exception {
        User u=new User(); Mind m=new Mind(u); u.setCurrentMind(m);
        require(m.compile(text,null,false),"native fixture");
        ContextProductionInventory inventory=ContextProductionInventory.inspect(m,new UUID(0,++ids),1,x,Collections.emptyList(),1000);
        ContextProductionNativeBinding.Result bound=ContextProductionNativeBinding.bind(m,inventory,WORK,500000);
        require(bound.bound,"native binding"); return bound.snapshot;
    }
    private static Clause clause(Snapshot s) { return s.primary.get(0).clauses.get(0); }
    private static Receipt inspect(Frontier f,Snapshot cut) {
        return ContextOrdinaryResidualRounds.inspectWithUniversalCompositions(f,cut,16,WORK,500000,256);
    }
    private static List<Receipt> pass(Frontier f) {
        List<Receipt> results=new ArrayList<>();
        for(Snapshot cut:f.cuts) { Receipt r=inspect(f,cut); require(r.verified,"verified universal cut: "+r.reason); results.add(r); }
        return results;
    }
    private static int refute(Snapshot x,List<Snapshot> foreign,Set<Source> expected) {
        Frontier f=ContextOrdinaryResidualRounds.start(x,foreign,WORK);
        for(int round=1;round<=5;round++) {
            Barrier b=ContextOrdinaryResidualRounds.merge(f,pass(f),WORK);
            if(b.status==Status.COLLISION) {
                require(b.collisions.stream().anyMatch(c -> { Set<Source> s=new HashSet<>(c.leftProof.sources); s.addAll(c.rightProof.sources); return s.equals(expected); }),"exact original causes survive schema transport and instantiation");
                return round;
            }
            require(b.status==Status.MERGED && b.changed,"cannot mistake pending universal composition for consistency: "+b.reason); f=b.frontier;
        }
        throw new AssertionError("missing collision");
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("universal-composition-rounds-").toString());
            Snapshot x=snapshot(true,"!@x p(x)||left(x);"),a=snapshot(false,"!@y ~p(y)||right(y);");
            Frontier f=ContextOrdinaryResidualRounds.start(x,Collections.singletonList(a),WORK);
            Clause result=clause(snapshot(false,"!@z left(z)||right(z);"));
            List<Receipt> receipts=pass(f); Receipt foreign=receipts.stream().filter(r -> r.target==a).findFirst().get();
            require(foreign.products.stream().anyMatch(p -> p.clause.equivalent(result) && p.proof.sources.size()==2 && p.proof.depth==1),"generic composed clause exported with whole original causes");
            require(f.products.isEmpty(),"inspection cannot mutate common input");
            Barrier first=ContextOrdinaryResidualRounds.merge(f,receipts,WORK);
            require(first.status==Status.MERGED && first.changed && first.frontier.basis==f.identity,"complete barrier creates separate source-bound frontier");
            require(first.frontier.products.stream().anyMatch(p -> p.clause.equivalent(result)),"schema crosses sealed barrier without sampled values");
            require(ContextOrdinaryResidualRounds.merge(first.frontier,receipts,WORK).status==Status.INCOMPLETE,"stale receipts cannot cross a later barrier");
            require(ContextOrdinaryResidualRounds.merge(f,Collections.singletonList(foreign),WORK).status==Status.INCOMPLETE,"missing self cut blocks publication");
            require(ContextOrdinaryResidualRounds.merge(f,Arrays.asList(foreign,foreign),WORK).status==Status.INCOMPLETE,"duplicate cut blocks publication");
            Receipt replay=inspect(first.frontier,a);
            require(replay.verified && replay.localProofs.stream().anyMatch(p -> p.clause.equivalent(result) && p.proof.depth==1 && p.proof.sources.size()==2),"actual native carrier replay preserves uninstantiated schema and original recipe");
            Snapshot b=snapshot(false,"!~left(9); !~right(9);");
            Set<Source> causes=new HashSet<>(); for(Snapshot s:Arrays.asList(x,a,b)) for(ContextProductionNativeBinding.Entry entry:s.primary) causes.add(entry.source);
            int forward=refute(x,Arrays.asList(a,b),causes),reverse=refute(x,Arrays.asList(b,a),causes);
            require(forward==2 && reverse==forward,"schema causes contradiction at second common barrier independent of sibling order");
            Snapshot self=snapshot(true,"!@x p(x,1)||~p(2,x);"),empty=snapshot(false,"!unrelated(7);");
            Frontier sf=ContextOrdinaryResidualRounds.start(self,Collections.singletonList(empty),WORK);
            Clause selfResult=clause(snapshot(false,"!p(1,1)||~p(2,2);"));
            List<Receipt> sr=pass(sf);
            require(sr.stream().allMatch(r -> r.products.stream().anyMatch(p -> p.clause.equivalent(selfResult) && p.proof.sources.size()==1 && p.proof.depth==1)),"self-resolution is transported from actual single source");
            require(ContextOrdinaryResidualRounds.merge(sf,sr,WORK).status==Status.MERGED,"self-resolution barrier succeeds");
            Snapshot unrelated=snapshot(true,"!anchor(1);"),privateRules=snapshot(false,"!@x a(x)||b(x); !@y ~a(y)||c(y);"),other=snapshot(false,"!~b(1); !~c(1);");
            Frontier pf=ContextOrdinaryResidualRounds.start(unrelated,Arrays.asList(privateRules,other),WORK);
            List<Receipt> pr=pass(pf);
            require(pr.stream().allMatch(r -> r.products.isEmpty()),"foreign-only schema never enters X");
            require(pr.stream().anyMatch(r -> r.localProofs.stream().anyMatch(p -> p.proof.depth==1 && p.clause.variables.size()==1)),"foreign-only universal result remains local proof");
            Barrier pb=ContextOrdinaryResidualRounds.merge(pf,pr,WORK);
            require(pb.status==Status.MERGED && !pb.changed,"independent sibling assertions are not pooled");
            Snapshot ux=snapshot(true,"!@x p(x)||left(x);"),ua=snapshot(false,"!@y ~p(y)||left(y);");
            Frontier uf=ContextOrdinaryResidualRounds.start(ux,Collections.singletonList(ua),WORK);
            Receipt unit=inspect(uf,ua);
            require(!unit.verified && unit.reason.contains("Universal residual unit") && unit.products.isEmpty() && unit.localProofs.isEmpty() && unit.collisions.isEmpty(),"universal unit conclusion refuses whole receipt without sampled facts or earlier prefix");
            require(ContextOrdinaryResidualRounds.merge(uf,Arrays.asList(inspect(uf,ux),unit),WORK).status==Status.INCOMPLETE,"unhandled universal unit cannot cross barrier");
            for(int type=0;type<4;type++) {
                Receipt limited=ContextOrdinaryResidualRounds.inspectWithUniversalCompositions(f,a,type==0?0:16,type==1?1:WORK,type==2?1:500000,type==3?1:256);
                // This fixture needs no isolated unit probes, so probe count is tested below.
                if(type<3) require(!limited.verified && limited.products.isEmpty() && limited.localProofs.isEmpty(),"bound refuses entire cut, no prefix");
            }
            Snapshot dx=snapshot(true,"!left(1)&&right(1)->p(1);"),da=snapshot(false,"!left(1); !right(1);");
            Frontier df=ContextOrdinaryResidualRounds.start(dx,Collections.singletonList(da),WORK);
            Receipt limited=ContextOrdinaryResidualRounds.inspectWithUniversalCompositions(df,da,16,WORK,500000,1);
            require(!limited.verified && limited.products.isEmpty(),"universal composition cannot reset baseline probe allowance");
            Snapshot badX=snapshot(true,"!@x p(x,x)||left(x);"),badA=snapshot(false,"!~p(1,2)||right(1);");
            Frontier bad=ContextOrdinaryResidualRounds.start(badX,Collections.singletonList(badA),WORK);
            require(pass(bad).stream().allMatch(r -> r.products.isEmpty()),"typed unification mismatch skips impossible pivot without suppressing resource failures");
            System.out.println("CONTEXT_UNIVERSAL_COMPOSITION_ROUNDS_PASS checks="+checks); System.exit(0);
        } catch(Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
