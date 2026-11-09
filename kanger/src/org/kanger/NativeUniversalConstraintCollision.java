/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.*;
import org.kanger.units.*;

/** Refutation-only native unit/headless resolution; no family closure or admission receipt. */
final class NativeUniversalConstraintCollision {
    private NativeUniversalConstraintCollision() { }
    static final class Root {
        final long nativeRule;
        final String origin;
        final List<Clause> clauses;
        private Root(Rule rule,List<Clause> clauses) throws Exception {
            nativeRule=rule.getId(); origin=rule.getOrigin();
            this.clauses=Collections.unmodifiableList(new ArrayList<>(clauses));
        }
    }
    static final class Application {
        final int unit,pivot;
        final Clause residual;
        private Application(int unit,int pivot,Clause residual) { this.unit=unit; this.pivot=pivot; this.residual=residual; }
    }
    /** Detached exact whole roots and every resolution application; not a DMZ certificate. */
    static final class Evidence {
        final List<Root> roots;
        final int branch;
        final List<Application> applications;
        private Evidence(List<Root> roots,int branch,List<Application> applications) {
            this.roots=Collections.unmodifiableList(new ArrayList<>(roots)); this.branch=branch;
            this.applications=Collections.unmodifiableList(new ArrayList<>(applications));
        }
        void check(int maximumWork) {
            Runnable work=ContextGroundProductionRounds.budget(maximumWork);
            if (roots.isEmpty() || branch<0 || branch>=roots.get(0).clauses.size() || applications.isEmpty())
                throw new IllegalStateException("Complete symbolic constraint evidence required");
            Clause current=roots.get(0).clauses.get(branch);
            if (current.literals.size()<2 || !headless(current))
                throw new IllegalStateException("Whole compound headless root required");
            Set<Integer> used=new HashSet<>(); used.add(0);
            for (Application application:applications) {
                work.run();
                if (current==null || application.unit<1 || application.unit>=roots.size())
                    throw new IllegalStateException("Different symbolic constraint application root");
                Root root=roots.get(application.unit);
                if (root.clauses.size()!=1 || !positiveUnit(root.clauses.get(0)))
                    throw new IllegalStateException("Whole positive unit evidence required");
                Clause expected=root.clauses.get(0).resolveWithUnits(current,0,application.pivot,work);
                if (!same(expected,application.residual,work))
                    throw new IllegalStateException("Different symbolic constraint residual");
                used.add(application.unit); current=expected;
            }
            if (current!=null || used.size()!=roots.size())
                throw new IllegalStateException("Empty proof with exactly its actual roots required");
        }
    }
    private static final class Unit {
        final Rule rule; final Clause clause;
        Unit(Rule rule,Clause clause) { this.rule=rule; this.clause=clause; }
    }
    private static final class Step {
        final Step previous; final Unit unit; final int pivot; final Clause residual;
        Step(Step previous,Unit unit,int pivot,Clause residual) { this.previous=previous; this.unit=unit; this.pivot=pivot; this.residual=residual; }
    }
    static final class Proof {
        final Rule constraint;
        private final Mind owner;
        private final Evidence evidence;
        private final List<Rule> originals;
        private Proof(Rule constraint,int branch,Step last,Mind mind,Runnable work) throws Exception {
            this.constraint=constraint; this.owner=mind;
            List<Step> chain=chain(last,work); List<Rule> originals=new ArrayList<>(); originals.add(constraint);
            Map<Long,Integer> ids=new LinkedHashMap<>(); ids.put(constraint.getId(),0);
            List<Application> applications=new ArrayList<>();
            for (Step step:chain) {
                work.run(); Integer index=ids.get(step.unit.rule.getId());
                if (index==null) { index=originals.size(); originals.add(step.unit.rule); ids.put(step.unit.rule.getId(),index); }
                applications.add(new Application(index,step.pivot,step.residual));
            }
            List<Root> roots=new ArrayList<>();
            for (Rule rule:originals) { List<Clause> clauses=shapes(rule,mind,work); if (clauses==null) throw new IllegalStateException("Changed native constraint root"); roots.add(new Root(rule,clauses)); }
            evidence=new Evidence(roots,branch,applications); this.originals=Collections.unmodifiableList(originals);
            evidence.check(1000000);
        }
        List<Rule> originals() { return originals; }
        Evidence evidence() { return evidence; }
        void verify(Mind mind) throws Exception {
            if (mind!=owner) throw new IllegalStateException("Different native symbolic constraint owner");
            Runnable work=ContextGroundProductionRounds.budget(1000000);
            for (int i=0;i<originals.size();i++) {
                work.run(); Rule rule=originals.get(i); Root root=evidence.roots.get(i);
                List<Clause> actual=shapes(rule,mind,work);
                if (mind.getRules().get(rule.getId())!=rule || actual==null || root.nativeRule!=rule.getId() || !Objects.equals(root.origin,rule.getOrigin())
                        || actual.size()!=root.clauses.size()) throw new IllegalStateException("Changed whole symbolic constraint root");
                for (int j=0;j<actual.size();j++) if (!actual.get(j).equivalent(root.clauses.get(j),work))
                    throw new IllegalStateException("Changed whole symbolic constraint clause");
            }
            evidence.check(1000000);
        }
    }
    private static List<Step> chain(Step last,Runnable work) {
        List<Step> out=new ArrayList<>(); for (Step step=last;step!=null;step=step.previous) { work.run(); out.add(step); }
        Collections.reverse(out); return out;
    }
    private static boolean same(Clause a,Clause b,Runnable work) { return a==null?b==null:b!=null&&a.equivalent(b,work); }
    private static boolean positiveUnit(Clause clause) { return clause.literals.size()==1&&clause.literals.get(0).positive; }
    private static boolean headless(Clause clause) { for (Atom atom:clause.literals) if (atom.positive) return false; return true; }
    private static List<Clause> shapes(Rule rule,Mind mind,Runnable work) throws Exception {
        work.run(); if (rule.isDeleted(mind)||rule.isGenerated()||rule.isQuery()||rule.getTree().isEmpty()) return null;
        List<Clause> clauses=new ArrayList<>();
        for (List<Domain> branch:rule.getTree()) {
            work.run(); if (branch.isEmpty()) return null;
            for (Domain domain:branch) {
                work.run(); if (domain.isSystem(mind)||domain.isCalculated(mind)) return null;
                for (IArgument argument:domain.getArguments()) {
                    work.run();
                    if (argument.getType()==ArgumentType.TVARIABLE) {
                        if (((TVariable)argument.getObject(mind)).getRuleId()!=rule.getId()) return null;
                    } else if (argument.getType()!=ArgumentType.TERM || !SemanticTermSnapshot.isOrdinaryValue(argument.getValue(mind))) return null;
                }
            }
            Clause clause=Clause.shapeOf(branch,mind); if (clause==null) return null; clauses.add(clause);
        }
        return clauses;
    }
    static Proof find(Mind mind) throws Exception { return find(mind,1000000,512); }
    static Proof find(Mind mind,int maximumWork,int maximumStates) throws Exception {
        if (maximumStates<1) throw new IllegalArgumentException("Positive constraint state budget required");
        Runnable work=ContextGroundProductionRounds.budget(maximumWork);
        Map<Rule,List<Clause>> roots=new LinkedHashMap<>(); List<Unit> units=new ArrayList<>();
        for (IRule item:mind.getRules()) {
            work.run(); Rule rule=(Rule)item; List<Clause> clauses=shapes(rule,mind,work);
            if (clauses==null) continue; roots.put(rule,clauses);
            if (clauses.size()==1&&positiveUnit(clauses.get(0))) units.add(new Unit(rule,clauses.get(0)));
        }
        if (units.isEmpty()) return null;
        int states=0;
        for (Map.Entry<Rule,List<Clause>> root:roots.entrySet()) for (int branch=0;branch<root.getValue().size();branch++) {
            Clause constraint=root.getValue().get(branch); if (constraint.literals.size()<2||!headless(constraint)) continue;
            List<Step> pending=new ArrayList<>(); pending.add(null);
            for (int at=0;at<pending.size();at++) {
                work.run(); if (++states>maximumStates) throw new IllegalStateException("Native symbolic constraint state limit");
                Step previous=pending.get(at); Clause current=previous==null?constraint:previous.residual;
                // Discharge the first remaining premise, enumerating EVERY unit choice.
                // Principal substitution retains all equations in the entire residual.
                for (Unit unit:units) {
                    int pivot=0;
                    work.run(); Atom a=unit.clause.literals.get(0),b=current.literals.get(pivot);
                    if (!a.predicate.equals(b.predicate)||a.arguments.size()!=b.arguments.size()) continue;
                    try {
                        Clause residual=unit.clause.resolveWithUnits(current,0,pivot,work);
                        Step step=new Step(previous,unit,pivot,residual);
                        if (residual==null) {
                            if (constraint.literals.size()==2 && previous!=null && previous.previous==null
                                    && DmzTypedNativeResume.deniesSymbolic(mind,root.getKey(),branch,
                                            previous.unit.rule,previous.unit.clause.literals.get(0),
                                            unit.rule,a,work)) continue;
                            return new Proof(root.getKey(),branch,step,mind,work);
                        }
                        if (pending.size()>=maximumStates) throw new IllegalStateException("Native symbolic constraint state limit");
                        pending.add(step);
                    } catch (Clause.UnificationFailure incompatible) { /* This exact tuple does not discharge this premise. */ }
                }
            }
        }
        return null; // No additional refutation, never a closure/consistency certificate.
    }
}
