/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.Clause;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IRule;
import org.kanger.enums.ArgumentType;
import org.kanger.units.Domain;
import org.kanger.units.Rule;
import org.kanger.units.TVariable;

/** Direct ordinary primary unit refutations only; no saturation or DMZ receipt. */
final class NativeUniversalUnitCollision {
    private NativeUniversalUnitCollision() { }
    static final class Pair {
        final Rule left, right;
        private final Clause a, b;
        private Pair(Rule left, Rule right, Clause a, Clause b) {
            this.left=left; this.right=right; this.a=a; this.b=b;
        }
        void verify(Mind mind) throws Exception {
            Runnable work=ContextGroundProductionRounds.budget(1000000);
            Clause actualLeft=shape(left,mind,work), actualRight=shape(right,mind,work);
            if (actualLeft==null || actualRight==null || !a.equivalent(actualLeft,work)
                    || !b.equivalent(actualRight,work)
                    || actualLeft.resolveWithUnits(actualRight,0,0,work)!=null)
                throw new IllegalStateException("Changed symbolic native unit collision roots");
        }
    }
    private static Clause shape(Rule rule, Mind mind, Runnable work) throws Exception {
        work.run();
        if (rule.isDeleted(mind) || rule.isGenerated() || rule.isQuery()
                || rule.getTree().size()!=1 || rule.getTree().get(0).size()!=1) return null;
        Domain domain=rule.getTree().get(0).get(0);
        if (domain.isSystem(mind) || domain.isCalculated(mind)) return null;
        for (IArgument argument:domain.getArguments()) {
            work.run();
            if (argument.getType()==ArgumentType.TVARIABLE) {
                if (((TVariable)argument.getObject(mind)).getRuleId()!=rule.getId()) return null;
            } else if (argument.getType()!=ArgumentType.TERM
                    || !SemanticTermSnapshot.isOrdinaryValue(argument.getValue(mind))) return null;
        }
        return Clause.shapeOf(rule.getTree().get(0),mind);
    }
    static List<Pair> find(Mind mind) throws Exception { return find(mind,1000000); }
    static List<Pair> find(Mind mind,int maximumWork) throws Exception {
        Runnable work=ContextGroundProductionRounds.budget(maximumWork);
        List<Pair> result=new ArrayList<>();
        Map<Long,Clause> shapes=new HashMap<>();
        Map<Long,Rule> owners=new HashMap<>();
        Map<String,List<Long>> positive=new LinkedHashMap<>(),negative=new LinkedHashMap<>();
        for (IRule item:mind.getRules()) {
            work.run(); Rule rule=(Rule)item; Clause clause=shape(rule,mind,work);
            if (clause==null) continue;
            ContextProductionCapture.Atom atom=clause.literals.get(0);
            work.run(); ContextArgumentBudget.positions(atom.arguments,work);
            shapes.put(rule.getId(),clause); owners.put(rule.getId(),rule);
            String signature=atom.predicate+"/"+atom.arguments.size();
            (atom.positive?positive:negative).computeIfAbsent(signature,k->new ArrayList<>()).add(rule.getId());
        }
        for (Map.Entry<String,List<Long>> bucket:positive.entrySet()) {
            List<Long> opposite=negative.get(bucket.getKey()); if (opposite==null) continue;
            for (long left:bucket.getValue()) for (long right:opposite) {
                work.run(); Clause a=shapes.get(left),b=shapes.get(right);
                if (a.variables.isEmpty() && b.variables.isEmpty()) continue;
                try {
                    if (a.resolveWithUnits(b,0,0,work)!=null)
                        throw new IllegalStateException("Nonempty direct unit resolvent");
                    result.add(new Pair(owners.get(left),owners.get(right),a,b));
                } catch (Clause.UnificationFailure incompatible) { /* No collision for this exact pair. */ }
            }
        }
        return result;
    }
}
