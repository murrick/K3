/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextUniversalFrozenClosure.Proof;
import org.kanger.ContextUniversalFrozenEvents.Conclusion;
import org.kanger.ContextUniversalInputResiduals.Residual;
import org.kanger.ContextWitnessResultTransport.*;

/** Exact positive projection/product correspondence; never complete instantiation or family authority. */
final class ContextUniversalFrozenProducts {
    static final class Product {
        final Residual fact;
        final Support expected,localProof,sharedProof;
        final Item input,local,shared;
        final Proof theorem;
        final Conclusion event;
        final ContextUniversalFrozenEvents.Donor instance;
        private Product(Demand demand,Match local,Match shared) {
            fact=demand.fact;expected=demand.support;input=demand.input;theorem=demand.theorem;event=demand.event;instance=demand.instance;
            this.local=local.item;localProof=local.support;this.shared=shared==null?null:shared.item;sharedProof=shared==null?null:shared.support;
        }
    }
    static final class Result {
        final boolean bound;
        final String reason;
        final ContextUniversalFrozenCoverage.Result coverage;
        final TypedTransfer transfer;
        final int symbolic,foreignOnly;
        final List<Product> products;
        private Result(boolean bound,String reason,ContextUniversalFrozenCoverage.Result coverage,TypedTransfer transfer,int symbolic,int foreignOnly,List<Product> products) {
            this.bound=bound;this.reason=reason;this.coverage=coverage;this.transfer=transfer;this.symbolic=symbolic;this.foreignOnly=foreignOnly;
            this.products=Collections.unmodifiableList(new ArrayList<>(products));
        }
    }
    private static final class Demand {
        final Residual fact;final Support support;final Item input;final Proof theorem;final Conclusion event;final ContextUniversalFrozenEvents.Donor instance;
        Demand(Residual fact,Support support,Item input,Proof theorem,Conclusion event){this(fact,support,input,theorem,event,null);}
        Demand(Residual fact,Support support,Item input,Proof theorem,Conclusion event,ContextUniversalFrozenEvents.Donor instance){this.fact=fact;this.support=support;this.input=input;this.theorem=theorem;this.event=event;this.instance=instance;}
    }
    private static final class Match {
        final Item item;final Support support;
        Match(Item item,Support support){this.item=item;this.support=support;}
    }
    private static boolean usesX(Support support){return support.sources.stream().anyMatch(s->s.localX);}
    private static int minimum(List<Demand> demands,Residual fact,Support support,Runnable work) {
        int minimum=Integer.MAX_VALUE;
        for(Demand demand:demands){work.run();if(demand.support.sources.equals(support.sources)&&demand.fact.equivalent(fact,work))minimum=Math.min(minimum,demand.support.depth);}
        if(minimum==Integer.MAX_VALUE)throw new IllegalStateException("Packet proof has no checked product obligation");return minimum;
    }
    private static Match match(Packet packet,Residual fact,Support support,int depth,Runnable work) {
        Match found=null;
        for(Item item:packet.items) {
            work.run();if(!fact.equivalent(ContextUniversalInputResiduals.fact(item,work),work))continue;
            for(Support actual:item.proofs) {
                work.run();if(!actual.sources.equals(support.sources))continue;
                if(actual.depth!=depth||found!=null)throw new IllegalStateException("Different or duplicate minimum product proof");found=new Match(item,actual);
            }
        }
        if(found==null)throw new IllegalStateException("Missing checked product/whole Source alternative in Packet");return found;
    }
    private static void reverse(Packet packet,List<Demand> demands,boolean shared,Runnable work) {
        for(Item item:packet.items)for(Support support:item.proofs) {
            work.run();if(shared&&!usesX(support))throw new IllegalStateException("Private-only proof in shared product Packet");
            int depth=minimum(demands,ContextUniversalInputResiduals.fact(item,work),support,work);
            if(support.depth!=depth)throw new IllegalStateException("Packet grade differs from minimum checked product");
        }
    }
    private static void unmarked(Packet packet) {
        if(packet.basis!=null||!packet.producers.isEmpty()||packet.observedStep||packet.emptyCut!=null||packet.coveredCut!=null||packet.roundCut!=null||packet.privateTarget!=null||packet.emptyTarget!=null)
            throw new IllegalStateException("Product correspondence cannot inherit coverage/STEP stamps");
    }
    private static void add(List<Demand> demands,Demand demand,int maximumProducts,Runnable work) {
        work.run();if(demands.size()==maximumProducts)throw new IllegalStateException("Product obligation limit");demands.add(demand);
    }
    static Result bind(ContextUniversalFrozenCoverage.Result coverage,TypedTransfer transfer,int maximumProducts,int maximumWork) {
        if(maximumProducts<1||maximumProducts>65536||maximumWork<1)throw new IllegalArgumentException("Bounded product correspondence required");
        Runnable work=ContextGroundProductionRounds.budget(maximumWork);
        try {
            work.run();if(coverage==null||!coverage.covered||transfer==null||!transfer.transferred||transfer.proof==null)
                throw new IllegalStateException("Successful sealed projection audit and transfer required");
            ContextUniversalInputResiduals.Frame frame=coverage.input.frame;
            if(transfer.proof.input.input.theory!=coverage.input||transfer.before!=frame.common||transfer.privateBefore!=frame.pool
                    ||!transfer.parent.equals(coverage.input.parent)||!transfer.target.equals(coverage.input.target))
                throw new IllegalStateException("Product audit and transfer do not share the exact owning ledger/frame");
            boolean privateInput=frame.pool!=null;
            if(privateInput?transfer.privateAfter==null||transfer.privateAfter.local!=transfer.local||transfer.privateAfter.common!=frame.common
                    :transfer.privateAfter!=null||transfer.local!=transfer.output)throw new IllegalStateException("Different retained product state");
            unmarked(transfer.local);unmarked(transfer.output);List<Demand> demands=new ArrayList<>();int symbolic=0,foreignOnly=0;
            for(Item item:frame.local.items)for(Support support:item.proofs) {
                work.run();add(demands,new Demand(ContextUniversalInputResiduals.fact(item,work),support,item,null,null),maximumProducts,work);
            }
            for(Proof proof:coverage.input.proofs) {
                work.run();if(proof.clause.literals.size()!=1||!proof.clause.variables.isEmpty()){++symbolic;continue;}
                if(!privateInput&&!usesX(proof.support)){++foreignOnly;continue;}
                add(demands,new Demand(proof.clause,proof.support,null,proof,null),maximumProducts,work);
            }
            for(Conclusion event:transfer.proof.conclusions) {
                work.run();if(!privateInput&&!usesX(event.support)){++foreignOnly;continue;}
                add(demands,new Demand(event.fact,event.support,null,null,event),maximumProducts,work);
            }
            if(transfer.instances!=null) {
                if(!transfer.instances.proved||transfer.instances.input!=transfer.proof.input)throw new IllegalStateException("Different exact instance product frame");
                for(ContextUniversalFrozenInstances.Instance instance:transfer.instances.instances)for(ContextUniversalFrozenEvents.Donor donor:instance.proofs) {
                    work.run();if(!privateInput&&!usesX(donor.support)){++foreignOnly;continue;}
                    add(demands,new Demand(instance.fact,donor.support,null,null,null,donor),maximumProducts,work);
                }
            }
            List<Product> products=new ArrayList<>();
            for(Demand demand:demands) {
                work.run();int depth=minimum(demands,demand.fact,demand.support,work);
                Match local=match(transfer.local,demand.fact,demand.support,depth,work);
                Match shared=usesX(demand.support)?match(transfer.output,demand.fact,demand.support,depth,work):null;
                products.add(new Product(demand,local,shared));
            }
            reverse(transfer.local,demands,false,work);reverse(transfer.output,demands,true,work);
            return new Result(true,"Exact input/fixed/event product minima bound to owning positive projection; complete instantiation and family qualification remain open",coverage,transfer,symbolic,foreignOnly,products);
        }catch(Exception failure){return new Result(false,failure.toString(),coverage,transfer,0,0,Collections.emptyList());}
    }
}
