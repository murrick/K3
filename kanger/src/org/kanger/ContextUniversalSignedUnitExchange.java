/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.Support;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalFrozenConstraints.Proof;
import org.kanger.ContextUniversalFrozenConstraints.Result;
import org.kanger.ContextUniversalInputResiduals.Residual;
import org.kanger.ContextWitnessResultTransport.*;

/** Supplied-cut symbolic unit barrier; no live family, native completeness or writer authority. */
final class ContextUniversalSignedUnitExchange {
    static final class Unit {
        final Residual clause;final Support support;final Result owner;final Proof proof;
        private Unit(Result owner,Proof proof){this.owner=owner;this.proof=proof;clause=proof.clause;support=proof.support;}
    }
    static final class Exchange {
        final Snapshot x;final Packet common;
        final Map<Cut,Result> checks;final List<Unit> units;
        private Exchange(Snapshot x,Packet common,Map<Cut,Result> checks,List<Unit> units){this.x=x;this.common=common;this.checks=Collections.unmodifiableMap(new LinkedHashMap<>(checks));this.units=Collections.unmodifiableList(new ArrayList<>(units));}
    }
    static final class Collection {
        final boolean collected;final String reason;final Exchange output;
        private Collection(boolean collected,String reason,Exchange output){this.collected=collected;this.reason=reason;this.output=output;}
    }
    private static boolean eligible(Proof proof){return proof.clause!=null&&proof.clause.literals.size()==1&&!proof.clause.variables.isEmpty()&&proof.support.sources.stream().anyMatch(s->s.localX);}
    private static void require(boolean valid,String reason){if(!valid)throw new IllegalStateException(reason);}
    private static void frame(Snapshot x,Packet common,List<Cut> expected,Map<Cut,Result> checks,int maximumWork,Runnable work) {
        require(x!=null&&common!=null&&expected!=null&&checks!=null,"Actual symbolic exchange frame required");
        Set<Cut> cuts=new LinkedHashSet<>(expected);require(cuts.size()==expected.size()&&cuts.contains(Cut.of(x))&&cuts.equals(checks.keySet()),"Exact supplied symbolic cuts including self-X required");
        ContextWitnessResultTransport.family(common,expected,maximumWork);
        ContextUniversalSignedUnitExchange.Exchange input=null;boolean first=true;
        for(Map.Entry<Cut,Result> e:checks.entrySet()) {
            work.run();Result r=e.getValue();require(r!=null&&r.closed&&r.signedUnits&&!r.xRefuted&&!r.foreignRefuted,"All symbolic donor cuts must be complete and unrefuted");
            require(r.input.x==x&&r.common==common&&r.input.target.equals(e.getKey()),"Different actual symbolic donor native/common/cut frame");
            if(first){input=r.exchange;first=false;}else require(r.exchange==input,"Different actual symbolic donor barrier");
            ContextUniversalFrozenConstraints.check(r,64,work);
        }
    }
    private static List<Unit> select(Map<Cut,Result> checks,int maximumUnits,int maximumAlternatives,Runnable work) {
        List<Unit> selected=new ArrayList<>();
        for(Result result:checks.values())for(Proof proof:result.proofs) {
            work.run();if(!eligible(proof))continue;
            int same=-1,alternatives=0;boolean bucket=false;
            for(int i=0;i<selected.size();++i){work.run();Unit prior=selected.get(i);if(!prior.clause.equivalent(proof.clause,work))continue;bucket=true;++alternatives;if(prior.support.sources.equals(proof.support.sources))same=i;}
            if(same>=0){if(proof.support.depth<selected.get(same).support.depth)selected.set(same,new Unit(result,proof));continue;}
            if(!bucket){int buckets=0;List<Residual> clauses=new ArrayList<>();for(Unit u:selected){work.run();boolean found=false;for(Residual c:clauses){work.run();if(c.equivalent(u.clause,work)){found=true;break;}}if(!found){clauses.add(u.clause);++buckets;}}require(buckets<maximumUnits,"Symbolic unit bucket limit");}
            require(alternatives<maximumAlternatives,"Symbolic Source alternative limit");selected.add(new Unit(result,proof));
        }
        return selected;
    }
    static Collection collect(Snapshot x,Packet common,List<Cut> expected,Map<Cut,Result> checks,int maximumUnits,int maximumAlternatives,int maximumDepth,int maximumWork) {
        if(maximumUnits<1||maximumUnits>512||maximumAlternatives<1||maximumAlternatives>256||maximumDepth<0||maximumDepth>64||maximumWork<1)throw new IllegalArgumentException("Bounded symbolic unit exchange required");
        Runnable work=ContextGroundProductionRounds.budget(maximumWork);
        try {
            frame(x,common,expected,checks,maximumWork,work);List<Unit> units=select(checks,maximumUnits,maximumAlternatives,work);
            // Apply the fact bound AFTER combining exact-Source minima across all supplied donors.
            for(Unit unit:units){work.run();require(unit.support.depth<=maximumDepth,"Uncovered symbolic unit depth limit");}
            return new Collection(true,"Exact supplied symbolic unit minima collected; next-input qualification and native/family coverage remain open",new Exchange(x,common,checks,units));
        }catch(Exception failure){return new Collection(false,failure.toString(),null);}
    }
    static void check(Exchange exchange,Snapshot x,Packet common,int maximumDepth,Runnable work) {
        require(exchange!=null&&exchange.x==x&&exchange.common==common,"Different exact symbolic exchange input");
        frame(x,common,new ArrayList<>(exchange.checks.keySet()),exchange.checks,Integer.MAX_VALUE,work);
        for(Unit unit:exchange.units) {
            work.run();require(eligible(unit.proof)&&exchange.checks.containsValue(unit.owner)&&unit.owner.proofs.contains(unit.proof)&&unit.clause==unit.proof.clause&&unit.support==unit.proof.support&&unit.support.depth<=maximumDepth,"Different actual symbolic proof/grade");
        }
    }
}
