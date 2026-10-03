/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.interfaces.*;
import org.kanger.primitives.*;
import org.kanger.units.*;

/** Focused oracle boundaries; run in separate OFF/ON JVMs. */
public final class ResidentBaseComparisonRunner {
    private static int checks;
    private static ArgumentsList args(ITerm... terms) {
        ArgumentsList list = new ArgumentsList();
        for (ITerm t : terms) list.add(t == null ? new Argument() : new Argument(t));
        return list;
    }
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
    }
    private static final class CountingArgument extends Argument {
        int reads;
        CountingArgument(ITerm term) { super(term); }
        @Override public ITerm getValue(IMind mind) throws Exception { reads++; return super.getValue(mind); }
    }
    private static final class CountingValue extends TValue {
        int reads;
        CountingValue(TVariable variable, ITerm term) { super(variable, term); }
        @Override public Term peekBuiltinTerm() { throw new AssertionError("custom resident probe called"); }
        @Override public ITerm getValue(Mind mind) throws Exception {
            reads++; return super.getValue(mind);
        }
    }
    private static final class CountingTerm extends Term {
        int ids;
        @Override public long getId() { ids++; return super.getId(); }
    }
    private static final class CountingList extends ArgumentsList {
        int gets;
        @Override public IArgument get(int index) { gets++; return super.get(index); }
    }
    private static final class ObservedMind extends Mind {
        int terms,values;
        ObservedMind() throws Exception { super(new User()); }
        @Override public org.kanger.factory.DictionaryFactory getTerms() { terms++; return super.getTerms(); }
        @Override public org.kanger.factory.TValueFactory getTValues() { values++; return super.getTValues(); }
    }
    private static final class CountingFactory extends org.kanger.factory.TValueFactory {
        int gets;
        CountingFactory(Mind mind) throws Exception { super(mind); }
        @Override public TValue get(TVariable variable) { gets++; return super.get(variable); }
    }
    private static final class ParentKey extends Term {
        int ids;
        TVariable variable;
        TValue next;
        boolean fail;
        @Override public long getId() {
            ids++;
            if (fail) throw new IllegalStateException("parent key callback");
            variable.setCurrent(next);
            return super.getId();
        }
    }
    private static ArgumentsList objects(IUnit... units) {
        ArgumentsList list=new ArgumentsList();
        for (IUnit unit:units) list.add(new Argument(unit));
        return list;
    }
    private static void extra(Mind m, ITerm a, ITerm b) throws Exception {
        CountingTerm custom=new CountingTerm();custom.setId(7001);
        Term equal=new Term();equal.setId(7001);
        ArgumentsList customTerms=args(custom);custom.ids=0;
        check(customTerms.equalsBase(m,args(equal)),"custom term result");
        check(custom.ids==1,"custom term getId count");
        TVariable v=new TVariable(m);v.setId(8001);
        ArgumentsList customDonor=objects(new TValue(v,custom));custom.ids=0;
        check(customDonor.equalsBase(m,args(equal)),"custom donor result");
        check(custom.ids==1,"custom donor getId count");
        CountingList left=new CountingList(),right=new CountingList();
        left.add(new Argument(a));right.add(new Argument(a));left.gets=right.gets=0;
        check(left.equalsBase(m,right),"custom lists");
        check(left.gets==3 && right.gets==2,"custom list access counts");
        ObservedMind observed=new ObservedMind();ITerm x=observed.getTerms().add("x");
        TValue lazy=new TValue();lazy.setPersistentReferences(x.getId(),v.getId());
        observed.terms=0;
        check(objects(lazy).equalsBase(observed,args(x)),"lazy TValue hydration");
        check(observed.terms==1,"hydrate exactly once");
        check(objects(lazy).equalsBase(observed,args(x)) && observed.terms==1,"resident later TValue");
        TVariable cv=new TVariable(observed);cv.setId(8002);
        observed.getTValues().set(cv,new TValue(cv,x));observed.values=0;
        check(objects(cv).equalsBase(observed,args(x)),"custom Mind");
        check(observed.values==6,"custom Mind value factory callback count");
        java.lang.reflect.Field factoryField=Mind.class.getDeclaredField("tValues");factoryField.setAccessible(true);
        org.kanger.factory.TValueFactory original=m.getTValues();
        CountingFactory factory=new CountingFactory(m);factory.set(v,new TValue(v,a));
        factoryField.set(m,factory);
        try {
            factory.gets=0;
            check(objects(v).equalsBase(m,args(a)),"custom factory");
            check(factory.gets==6,"custom factory callback count");
        } finally { factoryField.set(m,original); }
        original.set(v,new TValue(v,a));Mind child=new Mind(m);
        try {
            child.getTValues().set(v,new TValue(v,b));v.setMind(child);
            check(objects(v).equalsBase(m,args(b)),"caller and active Mind differ");
            java.lang.reflect.Field view=TVariable.class.getDeclaredField("runtimeMind");view.setAccessible(true);
            ((java.lang.ref.WeakReference<?>)((ThreadLocal<?>)view.get(v)).get()).clear();
            check(objects(v).equalsBase(m,args(a)),"cleared weak view falls back to owner");
        } finally { v.setMind(m);m.release(child); }
        Term t=new Term(),r=new Term();t.setId(901);r.setId(902);
        ArgumentsList mutable=objects(new TValue(v,t));
        check(!mutable.equalsBase(m,args(r)),"different mutable donor IDs");
        t.setId(902);check(mutable.equalsBase(m,args(r)),"read current ID despite stored valueId");
        check(args(a).equalsBase(null,args(a)),"resident terms with null Mind");
        try { args().equalsBase(m,new java.util.ArrayList<IArgument>());throw new AssertionError("legacy cast changed"); }
        catch(ClassCastException expected) { check(true,"legacy non-ArgumentsList cast"); }
        Rule one=new Rule(m);one.setId(9901);Rule two=new Rule(m);two.setId(9902);
        Term initial=(Term)m.getTerms().createCVar(one,a,null);
        Term replacement=(Term)m.getTerms().createCVar(two,b,null);
        TVariable changing=new TVariable(m);changing.setId(8003);
        TValue before=new TValue(changing,initial),after=new TValue(changing,replacement);
        changing.setCurrent(before);
        m.getCvarParents().remove(initial);
        ParentKey key=new ParentKey();key.setId(initial.getId());key.variable=changing;key.next=after;
        m.getCvarParents().put(key,a);key.ids=0;
        ArgumentsList changingArgs=objects(changing);
        check(changingArgs.equalsBase(m,args(replacement)),"parent callback changes final ID comparison");
        check(key.ids==2 && changing.getValue()==replacement,"parent callback counts and binding");
        changing.setCurrent(before);key.ids=0;key.fail=true;
        java.io.PrintStream saved=System.err;java.io.ByteArrayOutputStream errors=new java.io.ByteArrayOutputStream();
        boolean result;
        try {System.setErr(new java.io.PrintStream(errors));result=changingArgs.equalsBase(m,args(replacement));}
        finally {System.setErr(saved);}
        check(!result,"parent callback failure result");
        check(key.ids==1 && errors.toString("UTF-8").contains("parent key callback"),"parent callback failure preserved");
        key.fail=false;m.getCvarParents().remove(key);
    }
    public static void main(String[] ignored) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("resident-base-").toString());
        Mind m = new Mind(new User());
        ITerm a = m.getTerms().add("a"), b = m.getTerms().add("b");
        check(args().equalsBase(m,args()), "empty lists");
        check(!args(a).equalsBase(m,args()), "different size");
        check(args(a,b).equalsBase(m,args(a,b)), "equal terms");
        check(!args(a,b).equalsBase(m,args(b,a)), "positional mismatch");
        check(!args((ITerm)null).equalsBase(m,args(a)), "empty left");
        check(!args(a).equalsBase(m,args((ITerm)null)), "empty right");
        Rule rule = new Rule(m);
        rule.setId(42);
        ITerm parent = m.getTerms().createCVar(rule,a,null);
        ITerm child = m.getTerms().createCVar(rule,b,parent);
        Rule siblingRule = new Rule(m); siblingRule.setId(43);
        ITerm sibling = m.getTerms().createCVar(siblingRule,a,parent);
        check(args(parent).equalsBase(m,args(child)), "parent child");
        check(args(child).equalsBase(m,args(parent)), "child parent");
        check(!args(child).equalsBase(m,args(sibling)), "siblings are not base equal");
        m.getCvarParents().remove(child);
        check(!args(child).equalsBase(m,args(parent)), "parent relation removed");
        Argument lazy = new Argument(a);
        java.lang.reflect.Field object = Argument.class.getDeclaredField("o");
        object.setAccessible(true); object.set(lazy,null);
        ArgumentsList loaded = new ArgumentsList(); loaded.add(lazy);
        check(loaded.equalsBase(m,args(a)), "lazy first access");
        check(loaded.equalsBase(m,args(a)), "resident later access");
        TVariable v = m.getTVars().createTVar(rule,a);
        ArgumentsList dynamic = new ArgumentsList(); dynamic.add(new Argument(v));
        v.setCurrent(m.getTValues().add(v,a));
        check(dynamic.equalsBase(m,args(a)), "binding a");
        v.setCurrent(m.getTValues().add(v,b));
        check(!dynamic.equalsBase(m,args(a)), "binding changed");
        v.setCurrent(null);
        check(!dynamic.equalsBase(m,args(a)), "binding cleared");
        CountingArgument custom = new CountingArgument(a);
        ArgumentsList customList = new ArgumentsList(); customList.add(custom);
        check(customList.equalsBase(m,args(a)), "custom argument");
        check(custom.reads == 3, "custom resolution count: " + custom.reads);
        v.setCurrent(m.getTValues().add(v,a));
        ArgumentsList fixedValue = new ArgumentsList(); fixedValue.add(new Argument(v.getCurrent()));
        check(fixedValue.equalsBase(m,args(a)), "resident TValue");
        check(!fixedValue.equalsBase(m,args(b)), "resident TValue mismatch");
        CountingValue customValue = new CountingValue(v,a);
        v.setCurrent(customValue);
        check(dynamic.equalsBase(m,args(a)), "custom bound TValue");
        check(customValue.reads == 3, "custom TValue reads: " + customValue.reads);
        ArgumentsList unbound = new ArgumentsList();
        TVariable absent = m.getTVars().createTVar(siblingRule,b);
        unbound.add(new Argument(absent));
        check(!dynamic.equalsBase(m,unbound), "unbound right");
        check(customValue.reads == 4, "right emptiness short circuit");
        v.setCurrent(m.getTValues().add(v,a));
        Mind childMind = new Mind(m);
        try {
            v.setMind(childMind);
            v.setCurrent(childMind.getTValues().add(v,b));
            check(dynamic.equalsBase(childMind,args(b)), "active child binding");
            v.setMind(m);
            check(dynamic.equalsBase(m,args(a)), "active parent binding");
        } finally { v.setMind(m); m.release(childMind); }
        extra(m,a,b);
        System.out.println("RESIDENT_BASE_COMPARISON_OK checks=" + checks + " custom_reads=" + custom.reads);
    }
}
