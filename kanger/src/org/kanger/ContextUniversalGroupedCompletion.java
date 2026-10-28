/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionCapture.Support;
import org.kanger.ContextUniversalFrozenConstraints.Proof;
import org.kanger.ContextUniversalInputResiduals.GroupedApplication;
import org.kanger.ContextUniversalInputResiduals.Residual;

/** Explicit detached completion over checked occurrences; not a kernel/round/writer hook. */
final class ContextUniversalGroupedCompletion {
    static final class Receipt {
        final ContextUniversalCompoundOccurrences.Receipt history;final int conclusion;
        final List<Integer> conclusionSlots;final Map<Integer,Proof> additional,donors;
        final Residual clause;final Map<Integer,Residual> instances;final Support support;
        private Receipt(ContextUniversalCompoundOccurrences.Receipt history,int conclusion,Map<Integer,Proof> additional,Application a){this.history=history;this.conclusion=conclusion;conclusionSlots=Collections.unmodifiableList(new ArrayList<>(history.remaining.get(conclusion)));this.additional=Collections.unmodifiableMap(new LinkedHashMap<>(additional));donors=Collections.unmodifiableMap(new LinkedHashMap<>(a.donors));clause=a.binding.conclusion;instances=Collections.unmodifiableMap(new LinkedHashMap<>(a.binding.instances));support=a.support;}
    }
    static final class Completion {
        final boolean completed;final String reason;final Receipt output;
        private Completion(boolean completed,String reason,Receipt output){this.completed=completed;this.reason=reason;this.output=output;}
    }
    private static final class Application {
        final Map<Integer,Proof> donors;final GroupedApplication binding;final Support support;
        Application(Map<Integer,Proof> donors,GroupedApplication binding,Support support){this.donors=donors;this.binding=binding;this.support=support;}
    }
    private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    private static void sameInstances(List<Integer> group,Map<Integer,Residual> instances,Runnable work){Residual first=null;for(int slot:group){work.run();Residual instance=instances.get(slot);require(instance!=null,"Actual fixed instance for every grouped donor occurrence required");if(first==null)first=instance;else require(ContextUniversalInputResiduals.sameOrdered(first,instance,work),"Merged original donor occurrences require identical actual instances");}}
    private static final class NoInstance extends IllegalStateException {private static final long serialVersionUID=1L;}
    private static Application apply(ContextUniversalCompoundOccurrences.Receipt history,int conclusion,Map<Integer,Proof> additional,int depth,Runnable work) {
        ContextUniversalCompoundOccurrences.check(history,work);return applyChecked(history,conclusion,additional,depth,work);
    }
    private static Application applyChecked(ContextUniversalCompoundOccurrences.Receipt history,int conclusion,Map<Integer,Proof> additional,int depth,Runnable work) {
        require(!history.consumed.isEmpty(),"Actually consumed original donors required");require(conclusion>=0&&conclusion<history.remaining.size()&&additional!=null,"Actual residual conclusion position required");
        Set<Integer> expected=new LinkedHashSet<>();for(int i=0;i<history.remaining.size();++i)if(i!=conclusion)expected.add(i);require(expected.equals(additional.keySet()),"Every nonconclusion residual group required exactly once");
        for(Proof p:additional.values()){work.run();require(p!=null&&history.receiver.proofs.contains(p)&&p.clause!=null&&p.clause.literals.size()==1,"Actual receiver unit proof required");}
        Map<Integer,Proof> expanded=new LinkedHashMap<>(history.consumed);for(Map.Entry<Integer,Proof> e:additional.entrySet())for(int slot:history.remaining.get(e.getKey())){work.run();require(expanded.put(slot,e.getValue())==null,"Original donor occurrence duplicated");}
        Set<Integer> heads=new LinkedHashSet<>(history.remaining.get(conclusion));Map<Integer,Proof> donors=new LinkedHashMap<>();Map<Integer,Residual> clauses=new LinkedHashMap<>();Set<Source> sources=new LinkedHashSet<>(history.producer.support.sources);int grade=history.producer.support.depth;
        for(int slot=0;slot<history.producer.clause.literals.size();++slot)if(!heads.contains(slot)){work.run();Proof donor=expanded.get(slot);require(donor!=null,"Every nonconclusion original occurrence requires an actual donor");donors.put(slot,donor);clauses.put(slot,donor.clause);grade=Math.max(grade,donor.support.depth);for(Source source:donor.support.sources){work.run();sources.add(source);}}
        require(donors.keySet().equals(expanded.keySet()),"Conclusion group cannot be discharged as additional premises");Set<Source> exact=new LinkedHashSet<>(history.support.sources);for(Proof p:additional.values())for(Source source:p.support.sources){work.run();exact.add(source);}require(exact.equals(sources),"Every residual physical Source must survive grouped completion");require(grade<Integer.MAX_VALUE,"Grouped completion grade overflow");Support support=new Support(sources,grade+1);require(support.depth<=depth,"Grouped completion exceeds fact bound");
        GroupedApplication binding=ContextUniversalInputResiduals.applyGroupedUnits(history.producer.clause,heads,clauses,work);if(binding==null)throw new NoInstance();
        for(int i=0;i<history.remaining.size();++i)if(i!=conclusion)sameInstances(history.remaining.get(i),binding.instances,work);for(ContextUniversalCompoundOccurrences.Resolution step:history.resolutions)sameInstances(step.discharged,binding.instances,work);
        return new Application(donors,binding,support);
    }
    /** Shared work budget; only semantic unification/unfixed-instance failures mean no candidate. */
    static Receipt candidate(ContextUniversalCompoundOccurrences.Receipt history,int conclusion,Map<Integer,Proof> additional,Runnable work) {
        ContextUniversalCompoundOccurrences.check(history,work);
        try{Application a=applyChecked(history,conclusion,additional,64,work);return new Receipt(history,conclusion,additional,a);}catch(ContextUniversalInputResiduals.UnificationFailure|NoInstance incompatible){return null;}
    }
    static Completion complete(ContextUniversalCompoundOccurrences.Receipt history,int conclusion,Map<Integer,Proof> additional,int maximumDepth,int maximumWork) {
        if(maximumDepth<0||maximumDepth>64||maximumWork<1)throw new IllegalArgumentException("Bounded grouped completion required");Runnable work=ContextGroundProductionRounds.budget(maximumWork);
        try{Application a=apply(history,conclusion,additional,maximumDepth,work);return new Completion(true,"Actual grouped original application checked; kernel integration and candidate minima remain open",new Receipt(history,conclusion,additional,a));}catch(Exception failure){return new Completion(false,failure.toString(),null);}
    }
    static void check(Receipt receipt,int maximumDepth,Runnable work) {
        require(receipt!=null&&maximumDepth>=0&&maximumDepth<=64,"Actual bounded grouped completion receipt required");Application expected=apply(receipt.history,receipt.conclusion,receipt.additional,maximumDepth,work);
        require(receipt.conclusionSlots.equals(receipt.history.remaining.get(receipt.conclusion))&&receipt.donors.equals(expected.donors)&&receipt.support.equals(expected.support)&&ContextUniversalInputResiduals.sameOrdered(receipt.clause,expected.binding.conclusion,work)&&receipt.instances.keySet().equals(expected.binding.instances.keySet()),"Different actual grouped conclusion/parents/Source/grade");
        for(Map.Entry<Integer,Residual> e:expected.binding.instances.entrySet()){work.run();require(ContextUniversalInputResiduals.sameOrdered(receipt.instances.get(e.getKey()),e.getValue(),work),"Different actual original donor instance");}
    }
}
