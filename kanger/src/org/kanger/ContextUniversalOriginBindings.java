/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.Support;
import org.kanger.ContextUniversalFrozenConstraints.Proof;
import org.kanger.ContextUniversalFrozenConstraints.Result;
import org.kanger.ContextWitnessResultTransport.*;

/** Proof-specific parameter binding evidence; never raw-fact or write authority. */
final class ContextUniversalOriginBindings {
    static final class Root {
        final Result owner;final Proof proof;final Item item;final Support support;final List<Integer> arguments;
        private Root(Result owner,Proof proof,Origin origin,Runnable work){this.owner=owner;this.proof=proof;item=proof.input;support=proof.inputProof;List<Integer> positions=new ArrayList<>();for(int i=0;i<item.atom.arguments.size();++i){work.run();if(origin.equals(item.atom.arguments.get(i).origin))positions.add(i);}arguments=Collections.unmodifiableList(positions);}
    }
    static final class Route {
        final Proof theorem;final Origin origin;final List<Root> roots;
        private Route(Proof theorem,Origin origin,List<Root> roots){this.theorem=theorem;this.origin=origin;this.roots=Collections.unmodifiableList(new ArrayList<>(roots));}
    }
    static final class Receipt {
        final Result coverage;final List<Route> routes;
        private Receipt(Result coverage,List<Route> routes){this.coverage=coverage;this.routes=Collections.unmodifiableList(new ArrayList<>(routes));}
    }
    static final class Capture {
        final boolean captured;final String reason;final Receipt output;
        private Capture(boolean captured,String reason,Receipt output){this.captured=captured;this.reason=reason;this.output=output;}
    }
    private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    private static boolean identity(Collection<?> objects,Object value,Runnable work){for(Object object:objects){work.run();if(object==value)return true;}return false;}
    private static void walk(Proof proof,Result owner,Origin origin,Map<Result,Set<Proof>> seen,List<Root> roots,Runnable work) {
        work.run();require(proof!=null&&owner!=null,"Actual parameter proof/owner required");Set<Proof> visited=seen.get(owner);if(visited==null){visited=Collections.newSetFromMap(new IdentityHashMap<Proof,Boolean>());seen.put(owner,visited);}if(!visited.add(proof))return;
        if(proof.input!=null){require(identity(owner.local.items,proof.input,work)&&identity(proof.input.proofs,proof.inputProof,work)&&proof.support==proof.inputProof,"Actual binding Item/Support membership required");boolean present=false;for(Slot slot:proof.input.atom.arguments){work.run();if(origin.equals(slot.origin))present=true;}if(present){require(proof.support.sources.contains(origin.source),"Parameter binding root must retain its physical Source");roots.add(new Root(owner,proof,origin,work));}return;}
        if(proof.original!=null){require(identity(owner.input.rows,proof.original,work)&&proof.original.parts.contains(proof.part)&&proof.clause.origins.isEmpty(),"Actual original schema root cannot invent a rigid Origin");return;}
        if(proof.exchanged!=null){require(owner.exchange!=null&&identity(owner.exchange.units,proof.exchanged,work)&&proof.clause==proof.exchanged.clause&&proof.support==proof.exchanged.support,"Actual symbolic parameter import required");walk(proof.exchanged.proof,proof.exchanged.owner,origin,seen,roots,work);return;}
        if(proof.compound!=null){require(owner.compounds!=null&&identity(owner.compounds.clauses,proof.compound,work)&&proof.clause==proof.compound.clause&&proof.support==proof.compound.support,"Actual compound parameter import required");walk(proof.compound.proof,proof.compound.owner,origin,seen,roots,work);return;}
        if(proof.grouped!=null||!proof.groupedConclusions.isEmpty()){
            ContextUniversalGroupedCompletion.Receipt recipe=proof.grouped;require(recipe!=null&&identity(owner.groupedInputs,recipe,work)&&owner.groupedBase==recipe.history.receiver,"Actual grouped parameter recipe/base required");ContextUniversalCompoundOccurrences.Receipt history=recipe.history;Result rootOwner=history.imports.isEmpty()?history.residual.owner:history.imports.get(0).imported.owner;walk(proof.producer,rootOwner,origin,seen,roots,work);
            for(Map.Entry<Integer,Proof> e:proof.groupedDonors.entrySet()){work.run();Result donorOwner=history.receiver;if(history.consumed.containsKey(e.getKey())){donorOwner=null;for(ContextUniversalCompoundOccurrences.Resolution step:history.resolutions){work.run();if(step.discharged.contains(e.getKey())){require(step.proof.unit==e.getValue(),"Actual consumed parameter parent required");require(donorOwner==null,"Ambiguous original donor owner");donorOwner=step.owner;}}require(donorOwner!=null,"Missing actual consumed parameter owner");}else require(identity(history.receiver.proofs,e.getValue(),work),"Actual additional parameter parent required");walk(e.getValue(),donorOwner,origin,seen,roots,work);}return;
        }
        if(proof.producer!=null){walk(proof.producer,owner,origin,seen,roots,work);for(Proof donor:proof.donors){work.run();walk(donor,owner,origin,seen,roots,work);}return;}
        require(proof.unit!=null&&proof.horn!=null,"Actual ordinary parameter parents required");walk(proof.unit,owner,origin,seen,roots,work);walk(proof.horn,owner,origin,seen,roots,work);
    }
    private static List<Route> collect(Result coverage,Runnable work) {
        require(coverage!=null&&coverage.closed&&coverage.signedUnits&&!coverage.xRefuted&&!coverage.foreignRefuted,"Actual unrefuted signed coverage required");ContextUniversalFrozenConstraints.check(coverage,64,work);List<Route> routes=new ArrayList<>();
        for(Proof theorem:coverage.proofs){work.run();if(theorem.clause==null||theorem.clause.literals.size()!=1||!theorem.clause.variables.isEmpty()||coverage.pool==null&&theorem.support.sources.stream().noneMatch(s->s.localX))continue;for(Origin origin:theorem.clause.origins){work.run();require(theorem.support.sources.contains(origin.source),"Output Origin Source missing from actual theorem");List<Root> roots=new ArrayList<>();walk(theorem,coverage,origin,new IdentityHashMap<>(),roots,work);require(!roots.isEmpty(),"Every actual product Origin needs a checked binding Item/Support root");routes.add(new Route(theorem,origin,roots));}}
        return routes;
    }
    static Receipt checked(Result coverage,Runnable work){return new Receipt(coverage,collect(coverage,work));}
    static Capture capture(Result coverage,int maximumWork){if(maximumWork<1)throw new IllegalArgumentException("Bounded parameter binding capture required");try{return new Capture(true,"Actual product parameter roots checked; no raw fact or live authority transferred",checked(coverage,ContextGroundProductionRounds.budget(maximumWork)));}catch(Exception failure){return new Capture(false,failure.toString(),null);}}
    static void check(Receipt receipt,Runnable work){require(receipt!=null,"Actual parameter binding receipt required");List<Route> expected=collect(receipt.coverage,work);require(expected.size()==receipt.routes.size(),"Every eligible product Origin requires binding evidence");for(int i=0;i<expected.size();++i){work.run();Route a=receipt.routes.get(i),b=expected.get(i);require(a.theorem==b.theorem&&a.origin.equals(b.origin)&&a.roots.size()==b.roots.size(),"Different actual theorem/Origin binding inventory");for(int j=0;j<b.roots.size();++j){work.run();Root r=a.roots.get(j),s=b.roots.get(j);require(r.owner==s.owner&&r.proof==s.proof&&r.item==s.item&&r.support==s.support&&r.arguments.equals(s.arguments),"Different actual parameter Item/Support/argument binding");}}}
}
