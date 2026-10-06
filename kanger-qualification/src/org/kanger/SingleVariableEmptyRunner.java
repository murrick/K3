package org.kanger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.kanger.factory.TValueFactory;
import org.kanger.interfaces.ITerm;
import org.kanger.units.*;

/** Independent old-body oracle, callback traces and native map/context boundaries. */
public final class SingleVariableEmptyRunner {
    private static int checks, scenarios;
    private static final RuntimeException FAILURE = new IllegalStateException("original callback failure");
    private static final Method ACTIVE;
    private static final Field FACTORY;
    static {
        try {
            ACTIVE = TVariable.class.getDeclaredMethod("activeMind"); ACTIVE.setAccessible(true);
            FACTORY = Mind.class.getDeclaredField("tValues"); FACTORY.setAccessible(true);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    private static void check(boolean result) {
        if (!result) throw new AssertionError("check=" + checks + " scenario=" + scenarios);
        ++checks;
    }
    // Independent copy of the original method; private context selection is shared,
    // while the whole short-circuit and callback body remains the old implementation.
    private static boolean reference(TVariable variable) throws Exception {
        Mind active = (Mind) ACTIVE.invoke(variable);
        return active == null || active.getTValues().isEmpty(variable)
                || active.getTValues().get(variable) == null;
    }
    private static final class ObservedMind extends Mind {
        final List<String> trace;
        TValueFactory supplied, second;
        int reads, failure;
        boolean secondNull;
        ObservedMind(List<String> trace) throws Exception { super(new User()); this.trace = trace; }
        @Override public TValueFactory getTValues() {
            if (trace == null || supplied == null) return super.getTValues();
            trace.add("M" + ++reads);
            if (reads == failure) throw FAILURE;
            if (reads == 2 && secondNull) return null;
            return reads == 2 && second != null ? second : supplied;
        }
    }
    private static final class ObservedFactory extends TValueFactory {
        final List<String> trace;
        int empties, gets, hideAt, failureAt;
        boolean directGet;
        TValue answer;
        ObservedFactory(Mind mind, List<String> trace) throws Exception { super(mind); this.trace = trace; }
        @Override public boolean isEmpty(TVariable variable) {
            trace.add("E" + ++empties);
            if (empties == failureAt) throw FAILURE;
            return empties == hideAt || super.isEmpty(variable);
        }
        @Override public TValue get(TVariable variable) {
            trace.add("G" + ++gets);
            return directGet ? answer : super.get(variable);
        }
        @Override public Map<TVariable,TValue> getCurrent() {
            throw new AssertionError("custom public-map getter bypassed factory contract");
        }
        Map<TVariable,TValue> map() { return super.getCurrent(); }
    }
    private static final class ObservedVariable extends TVariable {
        final List<String> trace;
        int hashes, equalities, hashFailure, equalityFailure, clearHash, clearEquality;
        Map<TVariable,TValue> mutation;
        ObservedVariable(Mind mind, List<String> trace) { super(mind); this.trace = trace; }
        @Override public int hashCode() {
            trace.add("H" + ++hashes);
            if (hashes == hashFailure) throw FAILURE;
            if (hashes == clearHash) mutation.clear();
            return super.hashCode();
        }
        @Override public boolean equals(Object other) {
            trace.add("Q" + ++equalities);
            if (equalities == equalityFailure) throw FAILURE;
            if (equalities == clearEquality) mutation.clear();
            return super.equals(other);
        }
        @Override public int getIndex() { throw new AssertionError("stored comparable callback"); }
        void reset() { hashes = equalities = 0; trace.clear(); }
    }
    private static TVariable key(Mind mind, long id) {
        TVariable variable = new TVariable(mind); variable.setId(id); return variable;
    }
    private static final class Fixture {
        final List<String> trace = new ArrayList<>();
        final Mind mind;
        final TVariable variable;
        Fixture(int scenario) throws Exception {
            boolean customMind = scenario >= 12 && scenario <= 18;
            mind = customMind ? new ObservedMind(trace) : new Mind(new User());
            ITerm name = mind.getTerms().add("value");
            variable = scenario <= 11 ? new ObservedVariable(mind, trace) : key(mind, 1001);
            variable.setId(1001);
            TValue value = new TValue(variable, name, mind);
            TValueFactory factory = mind.getTValues();
            Map<TVariable,TValue> map = factory.getCurrent();
            if (scenario <= 11) {
                ObservedVariable observed = (ObservedVariable) variable;
                observed.mutation = map;
                map.put(scenario >= 6 ? key(mind, 1001) : variable, scenario == 11 ? null : value);
                observed.reset();
                if (scenario == 0) map.clear();
                if (scenario == 1) observed.hashFailure = 1;
                if (scenario == 2) observed.hashFailure = 2;
                if (scenario == 3) observed.hashFailure = 3;
                if (scenario == 4) observed.clearHash = 2;
                if (scenario == 5) observed.clearHash = 3;
                if (scenario == 7) observed.equalityFailure = 2;
                if (scenario == 8) observed.equalityFailure = 3;
                if (scenario == 9) observed.clearEquality = 2;
                if (scenario == 10) observed.clearEquality = 3;
            } else {
                ObservedFactory custom = new ObservedFactory(mind, trace);
                custom.map().put(variable, value);
                custom.answer = value;
                if (customMind) {
                    ObservedMind observed = (ObservedMind) mind;
                    observed.supplied = scenario <= 14 ? factory : custom;
                    factory.set(variable, value);
                    if (scenario == 13) observed.failure = 2;
                    if (scenario == 14) observed.secondNull = true;
                    if (scenario == 18) {
                        observed.second = new TValueFactory(mind);
                        observed.second.set(variable, null);
                    }
                } else {
                    // Test the exact-Mind/custom-factory guard via controlled injection.
                    FACTORY.set(mind, custom);
                }
                if (scenario == 16 || scenario == 19) custom.hideAt = 1;
                if (scenario == 17 || scenario == 20) custom.failureAt = 2;
                if (scenario == 21) custom.hideAt = 2;
                if (scenario == 22) { custom.directGet = true; custom.map().put(variable, null); }
                if (scenario == 23) { custom.directGet = true; custom.answer = null; }
                trace.clear();
            }
        }
    }
    private static Object outcome(TVariable variable, boolean old) throws Exception {
        try { return old ? reference(variable) : variable.isEmpty(); }
        catch (RuntimeException e) { return e; }
    }
    private static void callbackOracle() throws Exception {
        for (int scenario = 0; scenario < 24; ++scenario) {
            Fixture old = new Fixture(scenario), actual = new Fixture(scenario);
            Object expected = outcome(old.variable, true), result = outcome(actual.variable, false);
            if (expected instanceof NullPointerException) check(result instanceof NullPointerException);
            else check(expected == result);
            check(old.trace.equals(actual.trace));
            ++scenarios;
        }
    }
    private static void nativeBoundaries() throws Exception {
        check(new TVariable().isEmpty());
        Mind root = new Mind(new User());
        TVariable variable = key(root, 1001);
        ITerm term = root.getTerms().add("bound");
        TValue value = new TValue(variable, term, root);
        TValueFactory factory = root.getTValues();
        Map<TVariable,TValue> map = factory.getCurrent();
        check(variable.isEmpty());
        map.put(variable, null); check(variable.isEmpty()); check(!factory.isEmpty(variable));
        factory.set(variable, value); check(!variable.isEmpty());
        factory.set(variable, null); check(variable.isEmpty());
        // Logical deletion does not change transient emptiness.
        factory.set(variable, value); value.setDeleted(true, root); check(!variable.isEmpty());
        value.setDeleted(false, root);
        variable.setDeleted(true, root); check(!variable.isEmpty());
        variable.setDeleted(false, root);
        variable.setId(1002); check(variable.isEmpty());
        map.clear(); map.put(variable, value); check(!variable.isEmpty());
        variable.setId(1001); check(variable.isEmpty()); map.clear();
        for (long id : new long[] {0, -1, Long.MIN_VALUE, Long.MAX_VALUE}) {
            TVariable one = key(root, id);
            map.put(key(root, id), value); check(!one.isEmpty());
            map.put(key(root, id), null); check(one.isEmpty()); map.clear();
        }
        Mind child = new Mind(root);
        try {
            factory.set(variable, value);
            variable.setMind(child); check(variable.isEmpty());
            child.getTValues().set(variable, value); check(!variable.isEmpty());
            variable.setMind(root); check(!variable.isEmpty());
            factory.set(variable, null); check(variable.isEmpty());
            variable.setMind(child); check(!variable.isEmpty());
        } finally { variable.setMind(root); root.release(child); }
        map.clear();
        ObservedVariable stored = new ObservedVariable(root, new ArrayList<String>());
        for (int i = 0; i < 80; ++i) {
            TVariable one = i == 20 ? stored : key(root, 0);
            one.setId(((long)i << 32) | i);
            map.put(one, i == 30 ? null : value);
        }
        boolean tree = false;
        for (Map.Entry<TVariable,TValue> entry : map.entrySet()) tree |= entry.getClass().getName().endsWith("$TreeNode");
        check(tree); stored.reset();
        for (int i = 0; i < 80; ++i) check(key(root, ((long)i << 32) | i).isEmpty() == (i == 30));
        check(stored.hashes == 0 && stored.equalities == 0);
        check(key(root, (100L << 32) | 100).isEmpty());
        map.clear(); map.put(null, value); check(variable.isEmpty());
        map.put(variable, value); check(!variable.isEmpty()); map.clear(); check(variable.isEmpty());
        // Native Mind with null factory: preserve the historical NPE boundary.
        FACTORY.set(root, null);
        check(outcome(variable, false) instanceof NullPointerException);
    }
    private static void threadContexts() throws Exception {
        final Mind root = new Mind(new User());
        final TVariable variable = key(root, 1001);
        final Mind bound = new Mind(root), empty = new Mind(root);
        bound.getTValues().set(variable, new TValue(variable, root.getTerms().add("thread"), bound));
        final java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(2);
        final java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        Thread[] threads = new Thread[2];
        for (int i = 0; i < 2; ++i) {
            final boolean expectedEmpty = i == 1;
            final Mind context = expectedEmpty ? empty : bound;
            threads[i] = new Thread(new Runnable() {
                public void run() {
                    variable.setMind(context); ready.countDown();
                    try {
                        start.await();
                        for (int n = 0; n < 100; ++n)
                            if (variable.isEmpty() != expectedEmpty) throw new AssertionError("sibling projection");
                    } catch (Throwable error) { failure.compareAndSet(null, error); }
                }
            });
            threads[i].start();
        }
        ready.await(); start.countDown();
        for (Thread thread : threads) thread.join();
        check(failure.get() == null); check(variable.isEmpty());
        variable.setMind(bound); check(!variable.isEmpty());
        variable.setMind(empty); check(variable.isEmpty());
        variable.setMind(root); root.release(bound); root.release(empty);
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("single-variable-empty-").toString());
        callbackOracle(); nativeBoundaries(); threadContexts();
        System.out.println("SINGLE_VARIABLE_EMPTY_OK scenarios=" + scenarios + " checks=" + checks);
    }
}
