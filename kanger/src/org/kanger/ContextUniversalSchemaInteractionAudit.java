/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextWitnessResultTransport.Replay;

/** Symbolic obligations for observed unit/Horn schema encounters; never knowledge or full search coverage. */
final class ContextUniversalSchemaInteractionAudit {
    static final class Obligation {
        final SchemaInteraction observation;
        final boolean unifiable;
        final Clause resolvent;
        final Set<Set<Source>> sourceAlternatives;
        private Obligation(SchemaInteraction observation,boolean unifiable,Clause resolvent,Set<Set<Source>> alternatives) {
            this.observation=observation;this.unifiable=unifiable;this.resolvent=resolvent;
            Set<Set<Source>> copy=new LinkedHashSet<>();for(Set<Source> alternative:alternatives)copy.add(Collections.unmodifiableSet(new LinkedHashSet<>(alternative)));
            sourceAlternatives=Collections.unmodifiableSet(copy);
        }
    }
    static final class Result {
        final UUID replay;
        final boolean observedComplete;
        final String reason;
        final List<Obligation> obligations;
        private Result(UUID replay,boolean complete,String reason,List<Obligation> obligations) {
            this.replay=replay;observedComplete=complete;this.reason=reason;this.obligations=Collections.unmodifiableList(new ArrayList<>(obligations));
        }
    }
    private static Set<Source> binding(Snapshot snapshot,long rule,Clause clause,List<Support> labels,Map<Source,Entry> originals,Runnable work) {
        Set<Source> expected=new LinkedHashSet<>(),actual=new LinkedHashSet<>();
        for(Input input:snapshot.inputs) {
            work.run();if(input.nativeRule!=rule)continue;
            Entry original=originals.get(input.source);if(original==null)throw new IllegalStateException("Missing whole schema owner");
            ContextUniversalWitnessInstanceVerifier.whole(original,input,work);
            boolean found=false;for(Clause shape:input.shapes){work.run();if(shape.equivalent(clause))found=true;}
            if(!found)throw new IllegalStateException("Observed schema branch differs from whole owner");expected.add(input.source);
        }
        for(Support label:labels){work.run();if(label.depth!=0||label.sources.size()!=1)throw new IllegalStateException("Schema producer is not primary");actual.addAll(label.sources);}
        if(expected.isEmpty()||!expected.equals(actual))throw new IllegalStateException("Incomplete schema producer binding");
        return expected;
    }
    private static boolean unit(Clause clause,Runnable work) {
        work.run();return clause.literals.size()==1&&clause.literals.get(0).positive&&!clause.variables.isEmpty();
    }
    private static boolean horn(Clause clause,int pivot,Runnable work) {
        if(clause.literals.size()<2||clause.literals.size()>8||pivot<0||pivot>=clause.literals.size()||clause.literals.get(pivot).positive)return false;
        Atom head=null;Set<Integer> body=new HashSet<>();
        for(Atom literal:clause.literals) {
            work.run();if(literal.positive){if(head!=null)return false;head=literal;}
            else for(Slot slot:literal.arguments){work.run();if(slot.variable>=0)body.add(slot.variable);}
        }
        if(head==null)return false;
        for(Slot slot:head.arguments){work.run();if(slot.variable>=0&&!body.contains(slot.variable))return false;}
        return true;
    }
    static Result inspect(Replay replay,ContextProductionCapture capture,List<Entry> originals,int maximumWork) {
        if(maximumWork<1)throw new IllegalArgumentException("Bounded schema audit required");
        return inspect(replay,capture,originals,ContextGroundProductionRounds.budget(maximumWork));
    }
    static Result inspect(Replay replay,ContextProductionCapture capture,List<Entry> originals,Runnable work) {
        try {
            if(replay==null||capture==null||originals==null||originals.isEmpty()||originals.size()>64)throw new IllegalStateException("Bounded schema originals required");
            Snapshot snapshot=capture.snapshot(replay.mind);work.run();
            if(!snapshot.schemaInteractionsObserved)throw new IllegalStateException("Schema interaction observer not enabled");
            if(snapshot.schemaInteractionsMissed)throw new IllegalStateException("Schema encounters predate observer; journal is incomplete");
            Map<Source,Entry> declared=new LinkedHashMap<>();
            for(Entry original:originals){work.run();if(original==null||declared.put(original.source,original)!=null)throw new IllegalStateException("Repeated schema owner");}
            List<Obligation> obligations=new ArrayList<>();Set<Long> seen=new HashSet<>();
            for(SchemaInteraction encounter:snapshot.schemaInteractions) {
                work.run();seen.add(encounter.partnerRule);
                Set<Source> left=binding(snapshot,encounter.ownerRule,encounter.owner,encounter.ownerSupports,declared,work),
                    right=binding(snapshot,encounter.partnerRule,encounter.partner,encounter.partnerSupports,declared,work);
                boolean supported=unit(encounter.owner,work)&&encounter.ownerPivot==0&&horn(encounter.partner,encounter.partnerPivot,work)
                    ||unit(encounter.partner,work)&&encounter.partnerPivot==0&&horn(encounter.owner,encounter.ownerPivot,work);
                if(!supported)throw new IllegalStateException("Outside positive universal unit/Horn schema audit");
                Set<Set<Source>> alternatives=new LinkedHashSet<>();
                for(Source a:left)for(Source b:right){work.run();Set<Source> pair=new LinkedHashSet<>();pair.add(a);pair.add(b);alternatives.add(pair);}
                Clause resolvent;boolean unifiable=true;
                try {resolvent=encounter.owner.resolveWithUnits(encounter.partner,encounter.ownerPivot,encounter.partnerPivot,work);}
                catch(Clause.UnificationFailure incompatible){resolvent=null;unifiable=false;}
                if(unifiable&&resolvent==null)throw new IllegalStateException("Unexpected empty unit/Horn resolvent");
                obligations.add(new Obligation(encounter,unifiable,resolvent,alternatives));
            }
            for(String gap:snapshot.gaps) {
                work.run();if(gap.equals("observation-budget-exhausted")||gap.startsWith("unobserved-schema-interaction:"))throw new IllegalStateException("Incomplete schema encounter capture");
                if(gap.startsWith("unbound-waiter-composition:")&&!seen.contains(Long.parseLong(gap.substring("unbound-waiter-composition:".length()))))
                    throw new IllegalStateException("Schema gap has no observed encounter");
            }
            return new Result(replay.namespace,true,"All observed positive unit/Horn encounters checked; resolvents remain symbolic obligations, not search coverage",obligations);
        }catch(Exception failure){return new Result(replay==null?null:replay.namespace,false,failure.getMessage(),Collections.emptyList());}
    }
}
