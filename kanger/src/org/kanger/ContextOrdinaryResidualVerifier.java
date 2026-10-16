/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;

/** Whole-clause instance check for an observed reduction; supplies no inference result. */
final class ContextOrdinaryResidualVerifier {
    private static boolean same(Slot a,Slot b) {
        return a.variable >= 0 || b.variable >= 0 ? a.variable == b.variable : a.value.semanticallyEquals(b.value);
    }
    private static boolean instance(List<Atom> expected,List<Atom> actual,int index,boolean[] used,Map<Integer,Slot> bindings,Runnable work) {
        work.run(); if (index == expected.size()) return true;
        Atom atom = expected.get(index);
        for (int i=0;i<actual.size();++i) if (!used[i]) {
            work.run(); Atom value = actual.get(i);
            if (!atom.predicate.equals(value.predicate) || atom.positive != value.positive || atom.arguments.size() != value.arguments.size()) continue;
            Map<Integer,Slot> next = new HashMap<>(bindings); boolean matches = true;
            for (int a=0;a<atom.arguments.size();++a) {
                work.run(); Slot left = atom.arguments.get(a),right = value.arguments.get(a);
                if (left.variable < 0) { if (right.variable >= 0 || !same(left,right)) { matches = false; break; } }
                else { Slot old = next.putIfAbsent(left.variable,right); if (old != null && !same(old,right)) { matches = false; break; } }
            }
            if (matches) {
                used[i] = true;
                boolean found = instance(expected,actual,index+1,used,next,work);
                used[i] = false; if (found) return true;
            }
        }
        return false;
    }
    static boolean instance(Clause owner,Residual event,Runnable work) {
        work.run();
        if (event.steps != 1 || event.premises.isEmpty() || event.premises.size() > 2 || event.conclusion.literals.isEmpty()) return false;
        List<Atom> actual = new ArrayList<>(event.conclusion.literals);
        for (Fact premise : event.premises) actual.addAll(new Clause(Collections.singletonList(premise.opposite())).literals);
        return actual.size() == owner.literals.size() && instance(owner.literals,actual,0,new boolean[actual.size()],new HashMap<>(),work);
    }
}
