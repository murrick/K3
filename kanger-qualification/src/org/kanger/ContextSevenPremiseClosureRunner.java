/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextOrdinaryResidualRounds.*;

/** Complete seven-premise ground closure retains every exact subset recipe and separate cut model. */
public final class ContextSevenPremiseClosureRunner {
    static int checks;
    static void require(boolean v,String why){++checks;if(!v)throw new AssertionError(why);}
    public static void main(String[] args){try{
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("seven-closure-").toString());
        Snapshot x=ContextResidualRequestLimitsRunner.snapshot(true,ContextResidualRequestLimitsRunner.theory(7)),a=ContextResidualRequestLimitsRunner.snapshot(false,"!q(1);"),safe=ContextResidualRequestLimitsRunner.snapshot(false,"!safe(1);");
        Entry rule=x.primary.stream().filter(e->e.clauses.stream().anyMatch(c->c.literals.size()==8)).findFirst().get();Clause original=rule.clauses.stream().filter(c->c.literals.size()==8).findFirst().get();
        List<Entry> donors=new ArrayList<>(x.primary);donors.remove(rule);donors.addAll(a.primary);
        ContextGroundClauseFamily.Evaluation first=null;
        for(boolean reverse:new boolean[]{false,true}){
            LedgerLimits limits=new LedgerLimits(256,1024);ContextGroundClauseFamily.Evaluation e=ContextResidualRequestLimitsRunner.family(x,reverse?Arrays.asList(safe,a):Arrays.asList(a,safe),limits);
            require(e.status==ContextGroundClauseFamily.Status.GROUND_FIXED_POINT&&e.rounds==2,"seven-premise complete family at unchanged64M work budget: "+e.reason);
            require(e.certificate.frontier.ledgerLimits==limits&&e.certificate.frontier.products.size()==127&&e.certificate.models.size()==3,"all127 shared residual subsets and three separate models");
            for(Product p:e.certificate.frontier.products){
                require(p.clause.literals.stream().allMatch(l->original.literals.stream().anyMatch(o->o.fact().equivalent(l.fact()))),"residual contains only original signed ground literals");
                require(p.clause.literals.stream().anyMatch(l->l.positive&&l.predicate.equals("out")),"private premise is never exported as a shared positive fact");
                Set<Source> expected=new LinkedHashSet<>();expected.add(rule.source);
                for(Entry donor:donors){Fact removed=donor.clauses.get(0).literals.get(0).fact().opposite();if(p.clause.literals.stream().noneMatch(l->l.fact().equivalent(removed)))expected.add(donor.source);}
                require(p.proof.depth==1&&p.proof.sources.equals(expected)&&p.proof.sources.size()==9-p.clause.literals.size(),"each subset retains exact whole producer/removed-donor Source and minimal cumulative depth");
            }
            require(e.certificate.frontier.products.stream().filter(p->p.clause.literals.size()==1).count()==1,"only out is a shared unit result");
            for(ContextGroundClauseFamily.Model model:e.certificate.models){
                require(model.positiveAtoms.size()==(model.target==x?7:8),"per-cut complete selected witness has expected positive atoms");
                require(model.positiveAtoms.stream().anyMatch(f->f.predicate.equals("q"))==(model.target==a),"q remains private to A's selected model");
                require(model.positiveAtoms.stream().anyMatch(f->f.predicate.equals("safe"))==(model.target==safe),"safe remains private to Safe's selected model");
            }
            if(first!=null){require(ContextResidualRequestLimitsRunner.sameProducts(first.certificate.frontier.products,e.certificate.frontier.products),"reversed context traversal retains all clauses/Source/depth alternatives");require(ContextResidualRequestLimitsRunner.sameModels(first,e),"reversed context traversal retains per-context models");}first=e;
        }
        System.out.println("CONTEXT_SEVEN_PREMISE_CLOSURE_PASS checks="+checks);System.exit(0);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
