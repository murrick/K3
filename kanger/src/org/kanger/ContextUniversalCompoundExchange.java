/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.Support;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalFrozenConstraints.Proof;
import org.kanger.ContextUniversalFrozenConstraints.Result;
import org.kanger.ContextUniversalInputResiduals.Residual;
import org.kanger.ContextWitnessResultTransport.*;

/** Sealed supplied-cut compound proofs; no consumer, native-minimum or admission authority. */
final class ContextUniversalCompoundExchange {
    static final class ResidualProof {
        final Result owner;final Proof proof;final Residual clause;final Support support;
        private ResidualProof(Result owner,Proof proof){this.owner=owner;this.proof=proof;clause=proof.clause;support=proof.support;}
    }
    static final class Exchange {
        final UUID identity=UUID.randomUUID();final Snapshot x;final Packet common;
        final Map<Cut,Result> checks;final List<ResidualProof> clauses;
        final int maximumClauses,maximumAlternatives,maximumDepth;
        final Exchange prior;final ConstraintBarrier barrier;
        private Exchange(Snapshot x,Packet common,Map<Cut,Result> checks,List<ResidualProof> clauses,int maximumClauses,int maximumAlternatives,int maximumDepth){this.x=x;this.common=common;this.checks=Collections.unmodifiableMap(new LinkedHashMap<>(checks));this.clauses=Collections.unmodifiableList(new ArrayList<>(clauses));this.maximumClauses=maximumClauses;this.maximumAlternatives=maximumAlternatives;this.maximumDepth=maximumDepth;prior=null;barrier=null;}
        private Exchange(Exchange prior,ConstraintBarrier barrier){this.prior=prior;this.barrier=barrier;x=prior.x;common=barrier.packet;checks=prior.checks;clauses=prior.clauses;maximumClauses=prior.maximumClauses;maximumAlternatives=prior.maximumAlternatives;maximumDepth=prior.maximumDepth;}
    }
    static final class Collection {
        final boolean collected;final String reason;final Exchange output;
        private Collection(boolean collected,String reason,Exchange output){this.collected=collected;this.reason=reason;this.output=output;}
    }
    private static void require(boolean valid,String reason){if(!valid)throw new IllegalStateException(reason);}
    private static boolean eligible(Proof proof){return proof.clause!=null&&proof.clause.literals.size()>1&&proof.support.sources.stream().anyMatch(s->s.localX);}
    private static void shape(Residual clause,Runnable work){
        require(clause.literals.size()>=2&&clause.literals.size()<=8&&clause.variables.size()<=2&&clause.origins.size()<=8,"Outside bounded compound clause shape");int positive=0;
        for(ContextUniversalInputResiduals.Literal literal:clause.literals){work.run();require(literal.arguments.size()<=8,"Compound arity limit");if(literal.positive)++positive;}require(positive<=1,"Typed Horn/constraint compound required");
    }
    private static void limits(int clauses,int alternatives,int depth){if(clauses<1||clauses>512||alternatives<1||alternatives>256||depth<0||depth>64)throw new IllegalArgumentException("Bounded compound limits required");}
    private static List<ResidualProof> select(Map<Cut,Result> checks,int maximumClauses,int maximumAlternatives,Runnable work){
        List<ResidualProof> selected=new ArrayList<>();List<Residual> buckets=new ArrayList<>();
        for(Result owner:checks.values())for(Proof proof:owner.proofs){
            work.run();if(!eligible(proof))continue;shape(proof.clause,work);int same=-1,alternatives=0;
            for(int i=0;i<selected.size();++i){work.run();ResidualProof old=selected.get(i);if(!old.clause.equivalent(proof.clause,work))continue;++alternatives;if(old.support.sources.equals(proof.support.sources))same=i;}
            if(same>=0){if(proof.support.depth<selected.get(same).support.depth)selected.set(same,new ResidualProof(owner,proof));continue;}
            boolean bucket=false;for(Residual clause:buckets){work.run();if(clause.equivalent(proof.clause,work)){bucket=true;break;}}
            if(!bucket){require(buckets.size()<maximumClauses,"Compound clause bucket limit");buckets.add(proof.clause);}
            require(alternatives<maximumAlternatives,"Compound Source alternative limit");selected.add(new ResidualProof(owner,proof));
        }
        return selected;
    }
    static Collection collect(Snapshot x,Packet common,List<Cut> expected,Map<Cut,Result> checks,int maximumClauses,int maximumAlternatives,int maximumDepth,int maximumWork){
        limits(maximumClauses,maximumAlternatives,maximumDepth);if(maximumWork<1)throw new IllegalArgumentException("Bounded compound work required");
        return collect(x,common,expected,checks,maximumClauses,maximumAlternatives,maximumDepth,maximumWork,ContextGroundProductionRounds.budget(maximumWork));
    }
    static final class SessionCollection {
        final Collection collection;final ContextUniversalValidationSession.Metrics metrics;
        private SessionCollection(Collection collection,ContextUniversalValidationSession.Metrics metrics){this.collection=collection;this.metrics=metrics;}
    }
    /** One original collection budget; selection and sealed policy are independently checked in full. */
    static SessionCollection collectWithValidationReuse(Snapshot x,Packet common,List<Cut> expected,Map<Cut,Result> checks,int maximumClauses,int maximumAlternatives,int maximumDepth,int maximumWork,int capacity){
        limits(maximumClauses,maximumAlternatives,maximumDepth);if(maximumWork<1)throw new IllegalArgumentException("Bounded compound work required");
        ContextUniversalValidationSession.Scope scope=ContextUniversalValidationSession.open(maximumWork,capacity);Collection collection;
        try{collection=collect(x,common,expected,checks,maximumClauses,maximumAlternatives,maximumDepth,maximumWork,scope.work);}finally{scope.close();}
        return new SessionCollection(collection,scope.metrics());
    }
    private static Collection collect(Snapshot x,Packet common,List<Cut> expected,Map<Cut,Result> checks,int maximumClauses,int maximumAlternatives,int maximumDepth,int maximumWork,Runnable work){
        try {
            // Validate EVERY complete original/private/symbolic owner frame before selecting anything.
            ContextUniversalSignedUnitExchange.frame(x,common,expected,checks,maximumWork,work);List<ResidualProof> selected=select(checks,maximumClauses,maximumAlternatives,work);
            // Minima for the SAME exact Source set are combined across ALL cuts before the fact bound.
            for(ResidualProof proof:selected){work.run();require(proof.support.depth<=maximumDepth,"Uncovered compound depth limit");}
            return new Collection(true,"Actual compound Source minima sealed; consumer/completion/native/family qualification remains open",new Exchange(x,common,checks,selected,maximumClauses,maximumAlternatives,maximumDepth));
        }catch(Exception failure){return new Collection(false,failure.toString(),null);}
    }
    private static void barrier(Exchange prior,ConstraintBarrier barrier,Runnable work){
        require(barrier!=null&&barrier.merged&&barrier.packet!=null&&barrier.branches.keySet().equals(prior.checks.keySet()),"Actual matching compound/fixed barrier required");
        for(Map.Entry<Cut,Result> e:prior.checks.entrySet()){work.run();ConstraintTransfer t=barrier.branches.get(e.getKey());require(t!=null&&t.proof==e.getValue()&&t.before==prior.common,"Different actual compound/fixed branch ledger");}
    }
    static Exchange rebase(Exchange prior,ConstraintBarrier barrier,int maximumWork){
        if(maximumWork<1)throw new IllegalArgumentException("Bounded compound rebase required");Runnable work=ContextGroundProductionRounds.budget(maximumWork);require(prior!=null,"Actual prior compound exchange required");check(prior,prior.x,prior.common,64,work);barrier(prior,barrier,work);return new Exchange(prior,barrier);
    }
    static void check(Exchange exchange,Snapshot x,Packet common,int maximumDepth,Runnable work){
        require(exchange!=null&&exchange.x==x&&exchange.common==common,"Different exact compound exchange input");limits(exchange.maximumClauses,exchange.maximumAlternatives,exchange.maximumDepth);require(maximumDepth>=0&&maximumDepth<=64,"Bounded compound verification depth required");
        if(exchange.prior!=null){
            check(exchange.prior,x,exchange.prior.common,maximumDepth,work);barrier(exchange.prior,exchange.barrier,work);
            require(exchange.barrier.packet==common&&exchange.checks==exchange.prior.checks&&exchange.clauses==exchange.prior.clauses&&exchange.maximumClauses==exchange.prior.maximumClauses&&exchange.maximumAlternatives==exchange.prior.maximumAlternatives&&exchange.maximumDepth==exchange.prior.maximumDepth,"Different actual compound rebase receipt");return;
        }
        require(exchange.barrier==null,"Unexpected compound barrier");ContextUniversalSignedUnitExchange.frame(x,common,new ArrayList<>(exchange.checks.keySet()),exchange.checks,Integer.MAX_VALUE,work);
        List<ResidualProof> expected=select(exchange.checks,exchange.maximumClauses,exchange.maximumAlternatives,work);require(expected.size()==exchange.clauses.size(),"Incomplete compound Source inventory");
        List<ResidualProof> seen=new ArrayList<>();for(ResidualProof actual:exchange.clauses){
            work.run();require(actual!=null&&actual.owner!=null&&actual.proof!=null&&eligible(actual.proof)&&exchange.checks.containsValue(actual.owner)&&actual.owner.proofs.contains(actual.proof)&&actual.clause==actual.proof.clause&&actual.support==actual.proof.support,"Different actual compound proof ownership");shape(actual.clause,work);
            require(actual.support.depth<=maximumDepth&&actual.support.depth<=exchange.maximumDepth,"Compound proof exceeds checked depth");
            for(ResidualProof prior:seen){work.run();require(!prior.support.sources.equals(actual.support.sources)||!prior.clause.equivalent(actual.clause,work),"Repeated compound exact-Source proof");}
            boolean found=false;for(ResidualProof proof:expected){work.run();if(proof.support.equals(actual.support)&&proof.clause.equivalent(actual.clause,work)){found=true;break;}}require(found,"Different compound exact-Source minimum");seen.add(actual);
        }
    }
    static boolean sameContents(Exchange a,Exchange b,int maximumWork){
        if(maximumWork<1)throw new IllegalArgumentException("Bounded compound comparison required");Runnable work=ContextGroundProductionRounds.budget(maximumWork);List<ResidualProof> left=a==null?Collections.emptyList():a.clauses,right=b==null?Collections.emptyList():b.clauses;if(left.size()!=right.size())return false;
        for(ResidualProof proof:left){work.run();boolean found=false;for(ResidualProof other:right){work.run();if(proof.support.equals(other.support)&&proof.clause.equivalent(other.clause,work)){found=true;break;}}if(!found)return false;}return true;
    }
}
