/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextUniversalUnitLedger.Row;

/** Checked chosen models for flat ordinary universal clauses over ALL admissible values. No admission. */
public final class ContextUniversalQuotientModel {
    public enum Scope { ORDINARY_FLAT_UNIVERSAL_QUOTIENT_MODEL }
    public static final class Profile {
        public final String predicate;
        public final List<Integer> arguments;
        private Profile(String predicate,List<Integer> arguments) {
            this.predicate=predicate; this.arguments=Collections.unmodifiableList(new ArrayList<>(arguments));
        }
        @Override public boolean equals(Object other) {
            if(!(other instanceof Profile)) return false;
            Profile p=(Profile)other; return predicate.equals(p.predicate) && arguments.equals(p.arguments);
        }
        @Override public int hashCode() { return Objects.hash(predicate,arguments); }
    }
    /** Constants are singleton classes; otherIndex denotes ALL values unequal to those constants. */
    public static final class Model {
        public final Scope scope=Scope.ORDINARY_FLAT_UNIVERSAL_QUOTIENT_MODEL;
        public final ContextUniversalUnitLedger ledger;
        public final List<SemanticTermSnapshot> constants;
        public final int otherIndex;
        public final List<Profile> positiveProfiles;
        private Model(ContextUniversalUnitLedger ledger,List<SemanticTermSnapshot> constants,List<Profile> positives) {
            this.ledger=ledger; this.constants=Collections.unmodifiableList(new ArrayList<>(constants));
            otherIndex=constants.size(); positiveProfiles=Collections.unmodifiableList(new ArrayList<>(positives));
        }
        /** Truth in this CHOSEN interpretation only; absent profiles are not negative knowledge. */
        public boolean holds(String predicate,List<SemanticTermSnapshot> arguments) {
            Objects.requireNonNull(predicate); List<Integer> projected=new ArrayList<>();
            for(SemanticTermSnapshot argument:arguments) {
                Objects.requireNonNull(argument); int at=-1;
                for(int i=0;i<constants.size();i++) if(constants.get(i).semanticallyEquals(argument)) { at=i; break; }
                projected.add(at<0?otherIndex:at);
            }
            return positiveProfiles.contains(new Profile(predicate,projected));
        }
    }
    public static final class Result {
        public final boolean modeled;
        public final String reason;
        public final Model model;
        private Result(boolean modeled,String reason,Model model) { this.modeled=modeled; this.reason=reason; this.model=model; }
    }
    public static final class Check {
        public final boolean valid;
        public final String reason;
        private Check(boolean valid,String reason) { this.valid=valid; this.reason=reason; }
    }
    private static void instance(int[] remaining,Runnable work) {
        work.run(); if(remaining[0]--<=0) throw new IllegalStateException("Universal quotient instance limit");
    }
    private static int constant(List<SemanticTermSnapshot> constants,SemanticTermSnapshot value,Runnable work) {
        for(int i=0;i<constants.size();i++) { work.run(); if(constants.get(i).semanticallyEquals(value)) return i; }
        return -1;
    }
    private static List<SemanticTermSnapshot> constants(ContextUniversalUnitLedger ledger,Runnable work) {
        List<SemanticTermSnapshot> result=new ArrayList<>();
        for(Row row:ledger.rows) for(Atom atom:row.clause.literals) for(Slot slot:atom.arguments) {
            work.run(); if(slot.variable<0 && constant(result,slot.value,work)<0) {
                if(result.size()>=64) throw new IllegalStateException("Universal quotient constant limit");
                result.add(slot.value);
            }
        }
        return result;
    }
    private static void encode(Clause clause,List<Integer> variables,int at,Map<Integer,Integer> assignment,List<SemanticTermSnapshot> constants,
            List<Profile> atoms,List<int[]> clauses,int[] remaining,Runnable work) {
        work.run();
        if(at<variables.size()) {
            for(int value=0;value<=constants.size();value++) { assignment.put(variables.get(at),value); encode(clause,variables,at+1,assignment,constants,atoms,clauses,remaining,work); }
            assignment.remove(variables.get(at)); return;
        }
        instance(remaining,work); int[] encoded=new int[clause.literals.size()]; int pos=0;
        for(Atom atom:clause.literals) {
            List<Integer> arguments=new ArrayList<>();
            for(Slot slot:atom.arguments) {
                work.run(); int value=slot.variable>=0?assignment.get(slot.variable):constant(constants,slot.value,work);
                if(value<0) throw new IllegalStateException("Ground constant absent from quotient manifest"); arguments.add(value);
            }
            Profile profile=new Profile(atom.predicate,arguments); int index=-1;
            for(int i=0;i<atoms.size();i++) { work.run(); if(atoms.get(i).equals(profile)) { index=i; break; } }
            if(index<0) {
                if(atoms.size()>=256) throw new IllegalStateException("Universal quotient profile limit");
                index=atoms.size(); atoms.add(profile);
            }
            encoded[pos++]=atom.positive?index+1:-(index+1);
        }
        clauses.add(encoded);
    }
    private static boolean search(List<int[]> clauses,boolean[] values,int at,int[] remaining,Runnable work) {
        work.run();
        if(at<values.length) {
            values[at]=false; if(search(clauses,values,at+1,remaining,work)) return true;
            values[at]=true; return search(clauses,values,at+1,remaining,work);
        }
        if(remaining[0]--<=0) throw new IllegalStateException("Universal quotient model assignment limit");
        for(int[] clause:clauses) {
            boolean trueClause=false;
            for(int literal:clause) { work.run(); if(values[Math.abs(literal)-1]==(literal>0)) { trueClause=true; break; } }
            if(!trueClause) return false;
        }
        return true;
    }
    // Independent witness evaluation: no encoded CNF indices or search assignment array.
    private static void verifyInstances(Clause clause,List<Integer> variables,int[] valuation,int domain,Model model,int[] remaining,Runnable work) {
        instance(remaining,work);
        boolean trueClause=false;
        for(Atom atom:clause.literals) {
            List<Integer> arguments=new ArrayList<>();
            for(Slot slot:atom.arguments) {
                work.run(); int value;
                if(slot.variable<0) value=constant(model.constants,slot.value,work);
                else { int index=variables.indexOf(slot.variable); if(index<0) throw new IllegalStateException("Unbound model-check variable"); value=valuation[index]; }
                if(value<0 || value>=domain) throw new IllegalStateException("Model-check argument outside exact quotient");
                arguments.add(value);
            }
            boolean positive=false; Profile profile=new Profile(atom.predicate,arguments);
            for(Profile p:model.positiveProfiles) { work.run(); if(p.equals(profile)) { positive=true; break; } }
            trueClause |= positive==atom.positive;
        }
        if(!trueClause) throw new IllegalStateException("Quotient witness falsifies an actual universal clause instance");
    }
    private static void verify(ContextUniversalUnitLedger ledger,Model model,int[] remaining,Runnable work) {
        if(ledger==null || model==null || model.ledger!=ledger || model.otherIndex!=model.constants.size()) throw new IllegalStateException("Exact historical quotient model required");
        for(int i=0;i<model.constants.size();i++) {
            for(int j=0;j<i;j++) { work.run(); if(model.constants.get(i).semanticallyEquals(model.constants.get(j))) throw new IllegalStateException("Duplicate semantic quotient constants"); }
            boolean actual=false;
            for(Row row:ledger.rows) for(Atom atom:row.clause.literals) for(Slot slot:atom.arguments) {
                work.run(); if(slot.variable<0 && slot.value.semanticallyEquals(model.constants.get(i))) actual=true;
            }
            if(!actual) throw new IllegalStateException("Invented constant in quotient manifest");
        }
        for(Row row:ledger.rows) for(Atom atom:row.clause.literals) for(Slot slot:atom.arguments) {
            work.run(); if(slot.variable<0 && constant(model.constants,slot.value,work)<0) throw new IllegalStateException("Actual constant missing from quotient manifest");
        }
        for(int i=0;i<model.positiveProfiles.size();i++) {
            Profile profile=model.positiveProfiles.get(i); boolean declared=false;
            for(Row row:ledger.rows) for(Atom atom:row.clause.literals) { work.run(); if(atom.predicate.equals(profile.predicate) && atom.arguments.size()==profile.arguments.size()) declared=true; }
            if(!declared) throw new IllegalStateException("Model profile outside actual predicate signature");
            for(int value:profile.arguments) { work.run(); if(value<0 || value>model.otherIndex) throw new IllegalStateException("Invalid quotient profile argument"); }
            for(int j=0;j<i;j++) { work.run(); if(profile.equals(model.positiveProfiles.get(j))) throw new IllegalStateException("Duplicate positive model profile"); }
        }
        int domain=model.constants.size()+1;
        for(Row row:ledger.rows) {
            if(row.clause.variables.size()>16 || row.clause.literals.size()>256) throw new IllegalStateException("Universal quotient clause/rank limit");
            List<Integer> variables=new ArrayList<>(row.clause.variables); int[] valuation=new int[variables.size()]; boolean more=true;
            while(more) {
                verifyInstances(row.clause,variables,valuation,domain,model,remaining,work);
                int index=valuation.length-1;
                while(index>=0 && ++valuation[index]==domain) { valuation[index]=0; index--; }
                more=index>=0;
            }
        }
    }
    public static Result find(ContextUniversalUnitLedger ledger,int maximumInstances,int maximumModels,int maximumWork) {
        if(maximumInstances<1 || maximumModels<1 || maximumWork<1) throw new IllegalArgumentException("Bounded universal model required");
        Runnable work=ContextGroundProductionRounds.budget(maximumWork); int[] remaining={Math.min(maximumInstances,65536)};
        try {
            if(ledger==null) throw new IllegalStateException("Sealed flat universal ledger required");
            List<SemanticTermSnapshot> constants=constants(ledger,work); List<Profile> atoms=new ArrayList<>(); List<int[]> clauses=new ArrayList<>();
            for(Row row:ledger.rows) {
                work.run(); if(row.clause.variables.size()>16 || row.clause.literals.size()>256) throw new IllegalStateException("Universal quotient clause/rank limit");
                encode(row.clause,new ArrayList<>(row.clause.variables),0,new HashMap<>(),constants,atoms,clauses,remaining,work);
            }
            boolean[] values=new boolean[atoms.size()];
            if(!search(clauses,values,0,new int[]{maximumModels},work)) throw new IllegalStateException("No quotient model found; no sourced collision proof supplied");
            List<Profile> positives=new ArrayList<>(); for(int i=0;i<values.length;i++) { work.run(); if(values[i]) positives.add(atoms.get(i)); }
            Model model=new Model(ledger,constants,positives); verify(ledger,model,remaining,work);
            return new Result(true,"Checked full-admissible-value quotient interpretation; family closure/admission unknown",model);
        } catch(Exception failure) { return new Result(false,failure.getMessage(),null); }
    }
    public static Check check(ContextUniversalUnitLedger ledger,Model model,int maximumInstances,int maximumWork) {
        if(maximumInstances<1 || maximumWork<1) throw new IllegalArgumentException("Bounded model check required");
        return check(ledger,model,new int[]{Math.min(maximumInstances,65536)},ContextGroundProductionRounds.budget(maximumWork));
    }
    static Check check(ContextUniversalUnitLedger ledger,Model model,int[] remaining,Runnable work) {
        try { verify(ledger,model,remaining,work); return new Check(true,"All representative valuations checked; projection extends to all admissible values"); }
        catch(Exception failure) { return new Check(false,failure.getMessage()); }
    }
}
