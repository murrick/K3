/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.compiler.Leaf;
import org.kanger.compiler.Parser;
import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.IArgument;
import org.kanger.units.*;

/** Detached leading-prefix metadata, checked against a positive ordinary native unit.
 * Not a model, proof, dependency implementation or admission certificate. */
final class NativePrenexUnitScope {
    static final class Binder {
        final boolean universal;
        final List<Integer> precedingUniversals;
        final long nativeId;
        final boolean domini;
        private Binder(boolean universal,List<Integer> dependencies,long nativeId,boolean domini,Runnable work) {
            this.universal=universal; List<Integer> copy=new ArrayList<>();
            for (int dependency:dependencies) { work.run(); copy.add(dependency); }
            precedingUniversals=Collections.unmodifiableList(copy);
            this.nativeId=nativeId; this.domini=domini;
        }
    }
    static final class Slot {
        final int binder;
        final SemanticTermSnapshot value;
        private Slot(int binder,SemanticTermSnapshot value) { this.binder=binder; this.value=value; }
    }
    final UUID observation=UUID.randomUUID();
    final long nativeRule;
    final String predicate;
    final List<Binder> binders;
    final List<Slot> slots;
    private NativePrenexUnitScope(Rule rule,String predicate,List<Binder> binders,List<Slot> slots) {
        nativeRule=rule.getId(); this.predicate=predicate;
        this.binders=Collections.unmodifiableList(new ArrayList<>(binders));
        this.slots=Collections.unmodifiableList(new ArrayList<>(slots));
    }
    static NativePrenexUnitScope observe(Rule rule,Mind mind,Runnable work) throws Exception {
        return observe(rule,mind,work,true);
    }
    /** Fetch internally: storage may rehydrate a new Java object for the same actual factory row. */
    static NativePrenexUnitScope observeFactoryRow(long id,Mind mind,Runnable work) throws Exception {
        work.run();Rule rule=(Rule)mind.getRules().get(id);
        if(rule==null||rule.getId()!=id)throw new IllegalStateException("Missing actual prenex factory row");
        return observe(rule,mind,work,false);
    }
    private static NativePrenexUnitScope observe(Rule rule,Mind mind,Runnable work,boolean identity) throws Exception {
        work.run();
        if (identity&&mind.getRules().get(rule.getId())!=rule) throw new IllegalStateException("Different native prenex observation owner");
        if (rule.isQuery()||rule.isGenerated()||rule.isDeleted(mind)||rule.getTree().size()!=1||rule.getTree().get(0).size()!=1)
            throw new UnsupportedOperationException("Positive primary prenex unit required");
        String origin=rule.getOrigin(); if (origin==null) throw new UnsupportedOperationException("Whole origin required");
        for (int i=0;i<origin.length();i++) work.run();
        Leaf tree=Parser.parse(origin);
        if (!"!".equals(tree.getValue())||tree.getLeft()!=null) throw new UnsupportedOperationException("Positive leading prefix required");
        tree=tree.getRight();
        List<Boolean> kinds=new ArrayList<>(); Map<String,Integer> names=new LinkedHashMap<>();
        while (tree!=null&&("@".equals(tree.getValue())||"$".equals(tree.getValue()))) {
            work.run();
            Leaf name=tree.getLeft();
            if (name==null||name.getRange()!=0||name.getLeft()!=null||name.getRight()!=null||names.containsKey(name.getValue()))
                throw new UnsupportedOperationException("Distinct flat prefix binders required");
            names.put(name.getValue(),kinds.size()); kinds.add("@".equals(tree.getValue())); tree=tree.getRight();
        }
        Domain domain=rule.getTree().get(0).get(0);
        if (tree==null||tree.isSystem()||!domain.isAntc()||domain.isSystem(mind)||domain.isCalculated(mind)
                ||!tree.getValue().equals(domain.getPredicate(mind).getName(mind)))
            throw new UnsupportedOperationException("One positive ordinary atom after prefix required");
        List<Leaf> leaves=new ArrayList<>(); Deque<Leaf> pending=new ArrayDeque<>();
        if (tree.getRight()!=null) pending.push(tree.getRight()); if (tree.getLeft()!=null) pending.push(tree.getLeft());
        while (!pending.isEmpty()) {
            work.run(); Leaf leaf=pending.pop();
            if (",".equals(leaf.getValue())&&leaf.getLeft()!=null&&leaf.getRight()!=null) {
                pending.push(leaf.getRight()); pending.push(leaf.getLeft());
            } else {
                if (leaf.getRange()!=0||leaf.getLeft()!=null||leaf.getRight()!=null)
                    throw new UnsupportedOperationException("Flat argument leaves required");
                leaves.add(leaf);
            }
        }
        if (leaves.size()!=domain.getRange()||tree.getRange()!=domain.getRange())
            throw new IllegalStateException("Prefix/native argument count differs");
        Long[] nativeIds=new Long[kinds.size()]; boolean[] domini=new boolean[kinds.size()];
        List<Slot> slots=new ArrayList<>();
        for (int position=0;position<leaves.size();position++) {
            work.run(); Integer index=names.get(leaves.get(position).getValue()); IArgument argument=domain.get(position);
            if (index==null) {
                if (argument.getType()!=ArgumentType.TERM||!SemanticTermSnapshot.isOrdinaryValue(argument.getValue(mind)))
                    throw new IllegalStateException("Fixed source leaf is not a fixed native ordinary value");
                slots.add(new Slot(-1,SemanticTermSnapshot.capture(argument.getValue(mind)))); continue;
            }
            long id; int nativeIndex;
            if (kinds.get(index)) {
                if (argument.getType()!=ArgumentType.TVARIABLE) throw new IllegalStateException("Universal prefix/native slot differs");
                TVariable variable=(TVariable)argument.getObject(mind);
                if (variable.getRuleId()!=rule.getId()) throw new IllegalStateException("Different native universal owner");
                id=variable.getId(); nativeIndex=variable.getIndex();
            } else {
                if (argument.getType()!=ArgumentType.TERM||!(argument.getValue(mind) instanceof Term))
                    throw new IllegalStateException("Existential prefix/native slot differs");
                Term term=(Term)argument.getValue(mind);
                if (!term.isCVariable()||term.getRuleId()!=rule.getId()||term.getParent(mind)!=null)
                    throw new IllegalStateException("Local primary existential root required");
                boolean laterUniversal=false;
                for (int j=index+1;j<kinds.size();j++) { work.run(); laterUniversal|=kinds.get(j); }
                if (term.isDomini()!=laterUniversal) throw new IllegalStateException("Different native prefix domini flag");
                id=term.getId(); nativeIndex=term.getIndex(); domini[index]=term.isDomini();
            }
            if ((long)nativeIndex!=(long)rule.getVarIndex()+index+1)
                throw new IllegalStateException("Different native declaration position");
            if (nativeIds[index]!=null&&nativeIds[index].longValue()!=id)
                throw new IllegalStateException("One source binder split across native identities");
            nativeIds[index]=id; slots.add(new Slot(index,null));
        }
        List<Binder> binders=new ArrayList<>(); List<Integer> universals=new ArrayList<>();
        for (int i=0;i<kinds.size();i++) {
            work.run(); if (nativeIds[i]==null) throw new UnsupportedOperationException("Unused prefix binders outside this observation scope");
            binders.add(new Binder(kinds.get(i),kinds.get(i)?Collections.emptyList():universals,nativeIds[i],domini[i],work));
            if (kinds.get(i)) universals.add(i);
        }
        return new NativePrenexUnitScope(rule,tree.getValue(),binders,slots);
    }
    static boolean same(NativePrenexUnitScope a,NativePrenexUnitScope b,Runnable work) {
        work.run(); if (!a.predicate.equals(b.predicate)||a.binders.size()!=b.binders.size()||a.slots.size()!=b.slots.size()) return false;
        for (int i=0;i<a.binders.size();i++) {
            work.run(); Binder x=a.binders.get(i),y=b.binders.get(i);
            if (x.universal!=y.universal||x.domini!=y.domini||x.precedingUniversals.size()!=y.precedingUniversals.size()) return false;
            for (int j=0;j<x.precedingUniversals.size();j++) {
                work.run(); if (!x.precedingUniversals.get(j).equals(y.precedingUniversals.get(j))) return false;
            }
        }
        for (int i=0;i<a.slots.size();i++) {
            work.run(); Slot x=a.slots.get(i),y=b.slots.get(i);
            if (x.binder!=y.binder||(x.binder<0&&!x.value.semanticallyEquals(y.value))) return false;
        }
        return true; // Operational IDs intentionally belong only to their observation UUID.
    }
}
