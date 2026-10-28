/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextUniversalFrozenConstraints.Proof;
import org.kanger.ContextUniversalFrozenConstraints.Result;
import org.kanger.ContextUniversalCompoundExchange.Exchange;
import org.kanger.ContextUniversalInputResiduals.Literal;

/** Exhaustive bounded candidate scan of one actual immutable receiver pool; no round/writer authority. */
final class ContextUniversalGroupedCandidates {
    static final class Receipt {
        final Result base;final Exchange exchange;
        final List<ContextUniversalCompoundOccurrences.Receipt> histories;
        final List<ContextUniversalGroupedCompletion.Receipt> minima;
        final int maximumCandidates,maximumClauses,maximumAlternatives,maximumDepth,maximumAttempts,attempts,accepted;
        private Receipt(Result base,List<ContextUniversalCompoundOccurrences.Receipt> histories,Scan scan,int candidates,int clauses,int alternatives,int depth,int attempts){this.base=base;exchange=base.compounds;this.histories=Collections.unmodifiableList(new ArrayList<>(histories));minima=Collections.unmodifiableList(new ArrayList<>(scan.minima));maximumCandidates=candidates;maximumClauses=clauses;maximumAlternatives=alternatives;maximumDepth=depth;maximumAttempts=attempts;this.attempts=scan.attempts;accepted=scan.accepted;}
    }
    static final class Enumeration {
        final boolean enumerated;final String reason;final Receipt output;
        private Enumeration(boolean enumerated,String reason,Receipt output){this.enumerated=enumerated;this.reason=reason;this.output=output;}
    }
    private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    private static void limits(int candidates,int clauses,int alternatives,int depth,int attempts){if(candidates<1||candidates>512||clauses<1||clauses>512||alternatives<1||alternatives>256||depth<0||depth>64||attempts<1||attempts>65536)throw new IllegalArgumentException("Bounded grouped candidate scan required");}
    private static final class Scan {
        final List<ContextUniversalGroupedCompletion.Receipt> minima=new ArrayList<>();int attempts,accepted;
    }
    private static void add(Scan scan,ContextUniversalGroupedCompletion.Receipt candidate,int maximumCandidates,int maximumClauses,int maximumAlternatives,Runnable work) {
        int found=-1,alternatives=0;List<ContextUniversalGroupedCompletion.Receipt> clauses=new ArrayList<>();
        for(int i=0;i<scan.minima.size();++i){work.run();ContextUniversalGroupedCompletion.Receipt old=scan.minima.get(i);boolean same=old.clause.equivalent(candidate.clause,work);if(same){++alternatives;if(old.support.sources.equals(candidate.support.sources))found=i;}boolean known=false;for(ContextUniversalGroupedCompletion.Receipt bucket:clauses){work.run();if(bucket.clause.equivalent(old.clause,work)){known=true;break;}}if(!known)clauses.add(old);}
        if(found>=0){if(candidate.support.depth<scan.minima.get(found).support.depth)scan.minima.set(found,candidate);return;}
        if(alternatives==0)require(clauses.size()<maximumClauses,"Grouped conclusion bucket limit");require(alternatives<maximumAlternatives,"Grouped Source alternative limit");require(scan.minima.size()<maximumCandidates,"Grouped selected candidate limit");scan.minima.add(candidate);
    }
    private static void join(ContextUniversalCompoundOccurrences.Receipt history,int conclusion,int position,Map<Integer,Proof> additional,Scan scan,int maximumCandidates,int maximumClauses,int maximumAlternatives,int maximumAttempts,Runnable work) {
        work.run();if(position==history.remaining.size()) {
            require(scan.attempts<maximumAttempts,"Grouped candidate attempt limit");++scan.attempts;
            ContextUniversalGroupedCompletion.Receipt candidate=ContextUniversalGroupedCompletion.candidate(history,conclusion,additional,work);
            if(candidate!=null){++scan.accepted;add(scan,candidate,maximumCandidates,maximumClauses,maximumAlternatives,work);}return;
        }
        if(position==conclusion){join(history,conclusion,position+1,additional,scan,maximumCandidates,maximumClauses,maximumAlternatives,maximumAttempts,work);return;}
        Literal formal=history.residual.clause.literals.get(position);
        for(Proof donor:history.receiver.proofs){work.run();if(donor.clause==null||donor.clause.literals.size()!=1)continue;Literal actual=donor.clause.literals.get(0);if(actual.positive==formal.positive||!actual.predicate.equals(formal.predicate)||actual.arguments.size()!=formal.arguments.size())continue;additional.put(position,donor);join(history,conclusion,position+1,additional,scan,maximumCandidates,maximumClauses,maximumAlternatives,maximumAttempts,work);additional.remove(position);}
    }
    private static Scan scan(List<ContextUniversalCompoundOccurrences.Receipt> histories,int candidates,int clauses,int alternatives,int depth,int attempts,Runnable work) {
        Scan scan=new Scan();for(ContextUniversalCompoundOccurrences.Receipt history:histories){work.run();if(history.consumed.isEmpty())continue;for(int conclusion=0;conclusion<history.remaining.size();++conclusion){work.run();join(history,conclusion,0,new LinkedHashMap<>(),scan,candidates,clauses,alternatives,attempts,work);}}
        for(ContextUniversalGroupedCompletion.Receipt receipt:scan.minima){work.run();require(receipt.support.depth<=depth,"Uncovered grouped candidate depth limit");}return scan;
    }
    private static List<ContextUniversalCompoundOccurrences.Receipt> histories(Result base,Runnable work) {
        require(base!=null&&base.closed&&base.compounds!=null,"Actual closed compound receiver required");ContextUniversalFrozenConstraints.check(base,64,work);List<ContextUniversalCompoundOccurrences.Receipt> out=new ArrayList<>();for(ContextUniversalCompoundExchange.ResidualProof residual:base.compounds.clauses){work.run();out.add(ContextUniversalCompoundOccurrences.checked(base.compounds,residual,base,work));}return out;
    }
    static Enumeration enumerate(Result base,int maximumCandidates,int maximumClauses,int maximumAlternatives,int maximumDepth,int maximumAttempts,int maximumWork) {
        limits(maximumCandidates,maximumClauses,maximumAlternatives,maximumDepth,maximumAttempts);if(maximumWork<1)throw new IllegalArgumentException("Bounded candidate work required");Runnable work=ContextGroundProductionRounds.budget(maximumWork);
        try{List<ContextUniversalCompoundOccurrences.Receipt> histories=histories(base,work);Scan scan=scan(histories,maximumCandidates,maximumClauses,maximumAlternatives,maximumDepth,maximumAttempts,work);return new Enumeration(true,"All actual receiver unit tuples scanned; exact-Source minima checked; further saturation/native/family coverage remain open",new Receipt(base,histories,scan,maximumCandidates,maximumClauses,maximumAlternatives,maximumDepth,maximumAttempts));}catch(Exception failure){return new Enumeration(false,failure.toString(),null);}
    }
    static void check(Receipt receipt,int maximumDepth,Runnable work) {
        require(receipt!=null&&receipt.base!=null&&receipt.exchange==receipt.base.compounds&&maximumDepth>=0&&maximumDepth<=64,"Actual bounded candidate receipt required");limits(receipt.maximumCandidates,receipt.maximumClauses,receipt.maximumAlternatives,receipt.maximumDepth,receipt.maximumAttempts);
        ContextUniversalFrozenConstraints.check(receipt.base,64,work);require(receipt.histories.size()==receipt.exchange.clauses.size(),"Every incoming actual capsule requires a checked history");
        for(int i=0;i<receipt.histories.size();++i){work.run();ContextUniversalCompoundOccurrences.Receipt history=receipt.histories.get(i);require(history.receiver==receipt.base&&history.exchange==receipt.exchange&&history.residual==receipt.exchange.clauses.get(i),"Different actual candidate history inventory");ContextUniversalCompoundOccurrences.check(history,work);}
        Scan expected=scan(receipt.histories,receipt.maximumCandidates,receipt.maximumClauses,receipt.maximumAlternatives,receipt.maximumDepth,receipt.maximumAttempts,work);
        require(receipt.attempts==expected.attempts&&receipt.accepted==expected.accepted&&receipt.minima.size()==expected.minima.size(),"Incomplete grouped tuple/minimum inventory");
        for(int i=0;i<receipt.minima.size();++i){work.run();ContextUniversalGroupedCompletion.Receipt actual=receipt.minima.get(i);require(receipt.histories.contains(actual.history)&&actual.support.depth<=maximumDepth,"Different actual candidate history/depth");ContextUniversalGroupedCompletion.check(actual,maximumDepth,work);for(int j=0;j<i;++j){work.run();ContextUniversalGroupedCompletion.Receipt earlier=receipt.minima.get(j);require(!actual.clause.equivalent(earlier.clause,work)||!actual.support.sources.equals(earlier.support.sources),"Duplicate grouped exact-Source minimum");}boolean found=false;for(ContextUniversalGroupedCompletion.Receipt minimum:expected.minima){work.run();if(actual.clause.equivalent(minimum.clause,work)&&actual.support.equals(minimum.support)){found=true;break;}}require(found,"Nonminimal or missing actual grouped candidate");}
    }
}
