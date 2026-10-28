/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionCapture.Support;
import org.kanger.ContextUniversalCompoundExchange.Exchange;
import org.kanger.ContextUniversalCompoundExchange.ResidualProof;
import org.kanger.ContextUniversalFrozenConstraints.Proof;
import org.kanger.ContextUniversalFrozenConstraints.Result;
import org.kanger.ContextUniversalInputResiduals.SignedResolution;

/** Authenticated literal occurrences through contraction and actual compound imports. */
final class ContextUniversalCompoundOccurrences {
    static final class Hop {
        final Result owner;final Proof proof;final Exchange exchange;final ResidualProof imported;
        private Hop(Result owner,Proof proof){this.owner=owner;this.proof=proof;exchange=owner.compounds;imported=proof.compound;}
    }
    static final class Resolution {
        final Result owner;final Proof proof;final SignedResolution mapping;final List<Integer> discharged;
        private Resolution(Result owner,Proof proof,SignedResolution mapping,List<Integer> discharged){this.owner=owner;this.proof=proof;this.mapping=mapping;this.discharged=Collections.unmodifiableList(new ArrayList<>(discharged));}
    }
    static final class Receipt {
        final Exchange exchange;final ResidualProof residual;final Result receiver;
        final Proof producer;final List<Hop> imports;final List<Resolution> resolutions;
        final Map<Integer,Proof> consumed;final List<List<Integer>> remaining;final Support support;
        private Receipt(Exchange exchange,ResidualProof residual,Result receiver,Plan plan){this.exchange=exchange;this.residual=residual;this.receiver=receiver;producer=plan.root;imports=Collections.unmodifiableList(new ArrayList<>(plan.imports));resolutions=Collections.unmodifiableList(new ArrayList<>(plan.resolutions));consumed=Collections.unmodifiableMap(new LinkedHashMap<>(plan.consumed));remaining=immutable(plan.remaining);support=residual.support;}
    }
    static final class Capture {
        final boolean captured;final String reason;final Receipt output;
        private Capture(boolean captured,String reason,Receipt output){this.captured=captured;this.reason=reason;this.output=output;}
    }
    private static List<List<Integer>> immutable(List<List<Integer>> groups){List<List<Integer>> out=new ArrayList<>();for(List<Integer> group:groups)out.add(Collections.unmodifiableList(new ArrayList<>(group)));return Collections.unmodifiableList(out);}
    private static final class Plan {
        final Proof root;final List<Hop> imports=new ArrayList<>();final List<Resolution> resolutions=new ArrayList<>();final Map<Integer,Proof> consumed=new LinkedHashMap<>();List<List<Integer>> remaining=new ArrayList<>();
        Plan(Proof root){this.root=root;for(int i=0;i<root.clause.literals.size();++i)remaining.add(Collections.singletonList(i));}
    }
    private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    private static Plan trace(Proof p,Result owner,Runnable work) {
        work.run();require(p!=null&&p.clause!=null,"Actual nonempty compound history required");
        if(p.original!=null){require(p.clause.literals.size()>=3,"Original multi-premise typed part required");return new Plan(p);}
        if(p.compound!=null){require(owner.compounds!=null&&owner.compounds.clauses.contains(p.compound)&&p.clause==p.compound.clause&&p.support==p.compound.support,"Actual owning compound import required");Plan out=trace(p.compound.proof,p.compound.owner,work);out.imports.add(new Hop(owner,p));return out;}
        require(p.unit!=null&&p.horn!=null&&p.producer==null&&p.exchanged==null,"Original binary/import history required; application roots remain open");
        Plan out=trace(p.horn,owner,work);require(out.remaining.size()==p.horn.clause.literals.size()&&p.pivot>=0&&p.pivot<out.remaining.size(),"Actual ordered pivot occurrences required");
        SignedResolution mapping=ContextUniversalInputResiduals.resolveSignedUnitOccurrences(p.unit.clause,p.horn.clause,p.pivot,work);
        require(ContextUniversalInputResiduals.sameOrdered(mapping.clause,p.clause,work),"Different ordered resolution clause; permutation is not occurrence evidence");
        List<Integer> discharged=out.remaining.get(p.pivot);for(int slot:discharged){work.run();require(out.consumed.put(slot,p.unit)==null,"Original occurrence already discharged");}
        List<List<Integer>> next=new ArrayList<>();for(List<Integer> group:mapping.occurrences){List<Integer> merged=new ArrayList<>();for(int position:group){work.run();require(position!=p.pivot&&position>=0&&position<out.remaining.size(),"Different remaining occurrence index");merged.addAll(out.remaining.get(position));}next.add(merged);}
        out.remaining=next;out.resolutions.add(new Resolution(owner,p,mapping,discharged));return out;
    }
    private static Plan frame(Exchange exchange,ResidualProof residual,Result receiver,Runnable work) {
        require(exchange!=null&&residual!=null&&receiver!=null&&receiver.closed&&receiver.compounds==exchange,"Actual checked compound receiver required");ContextUniversalFrozenConstraints.check(receiver,64,work);
        require(exchange.clauses.contains(residual)&&exchange.checks.containsValue(residual.owner)&&residual.owner.proofs.contains(residual.proof),"Actual owned residual required");Plan plan=trace(residual.proof,residual.owner,work);
        require(plan.remaining.size()==residual.clause.literals.size(),"Every ordered residual literal requires original occurrences");Set<Integer> slots=new LinkedHashSet<>(plan.consumed.keySet());for(List<Integer> group:plan.remaining){require(!group.isEmpty(),"Empty occurrence group");for(int slot:group){work.run();require(slots.add(slot),"Original occurrence duplicated in history");}}require(slots.size()==plan.root.clause.literals.size(),"Original occurrence omitted from history");
        Set<Source> sources=new LinkedHashSet<>(plan.root.support.sources);for(Proof donor:plan.consumed.values())for(Source source:donor.support.sources){work.run();sources.add(source);}require(sources.equals(residual.support.sources),"Every actual historical Source must survive occurrence tracing");return plan;
    }
    static Capture capture(Exchange exchange,ResidualProof residual,Result receiver,int maximumWork) {
        if(maximumWork<1)throw new IllegalArgumentException("Bounded occurrence capture required");Runnable work=ContextGroundProductionRounds.budget(maximumWork);
        try{Plan plan=frame(exchange,residual,receiver,work);return new Capture(true,"Actual original occurrence partition checked; completion and closure integration remain separate",new Receipt(exchange,residual,receiver,plan));}catch(Exception failure){return new Capture(false,failure.toString(),null);}
    }
    static void check(Receipt receipt,Runnable work) {
        require(receipt!=null,"Actual occurrence receipt required");Plan p=frame(receipt.exchange,receipt.residual,receipt.receiver,work);
        require(receipt.producer==p.root&&receipt.support==receipt.residual.support&&receipt.consumed.equals(p.consumed)&&receipt.remaining.equals(p.remaining)&&receipt.imports.size()==p.imports.size()&&receipt.resolutions.size()==p.resolutions.size(),"Different actual original occurrence partition");
        for(int i=0;i<p.imports.size();++i){work.run();Hop a=receipt.imports.get(i),b=p.imports.get(i);require(a.owner==b.owner&&a.proof==b.proof&&a.exchange==b.exchange&&a.imported==b.imported,"Different actual import history");}
        for(int i=0;i<p.resolutions.size();++i){work.run();Resolution a=receipt.resolutions.get(i),b=p.resolutions.get(i);require(a.owner==b.owner&&a.proof==b.proof&&a.discharged.equals(b.discharged)&&a.mapping.occurrences.equals(b.mapping.occurrences)&&ContextUniversalInputResiduals.sameOrdered(a.mapping.clause,b.mapping.clause,work),"Different actual pivot/contraction history");}
    }
}
