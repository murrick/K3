/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import org.kanger.factory.TValueFactory;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Rule;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;

/** Active binding identity, context separation and overridden emptiness. */
public final class GuardedCurrentLookupRunner {
    private static int checks;
    private static void check(boolean result) {
        if (!result) throw new AssertionError("check " + checks);
        checks++;
    }
    private static final class CustomFactory extends TValueFactory {
        int reads;
        boolean hide;
        boolean fail;
        CustomFactory(Mind mind) throws Exception { super(mind); }
        @Override public boolean isEmpty(TVariable variable) {
            reads++;
            if (fail) throw new IllegalStateException("custom emptiness");
            return hide || super.isEmpty(variable);
        }
    }
    private static final class ObservedVariable extends TVariable {
        int hashes, equalities, indexes;
        int hashFailure, equalityFailure;
        java.util.Map<TVariable, TValue> removeOnSecondHash;
        @Override public int hashCode() {
            hashes++;
            if (hashes == hashFailure) throw new IllegalStateException("hash callback");
            if (hashes == 2 && removeOnSecondHash != null) removeOnSecondHash.clear();
            return super.hashCode();
        }
        @Override public boolean equals(Object other) {
            equalities++;
            if (equalities == equalityFailure) throw new IllegalStateException("equals callback");
            return super.equals(other);
        }
        @Override public int getIndex() {
            indexes++;
            throw new AssertionError("stored-key comparison callback");
        }
        void reset() { hashes = equalities = indexes = 0; }
    }
    private static TVariable key(long id) {
        TVariable variable = new TVariable(); variable.setId(id); return variable;
    }
    private static void customKeys(TValueFactory factory, TValue value) {
        java.util.Map<TVariable, TValue> current = factory.getCurrent();
        current.clear();
        ObservedVariable observed = new ObservedVariable(); observed.setId(1001);
        current.put(observed, value); observed.reset();
        check(factory.get(observed) == value);
        check(observed.hashes == 2 && observed.equalities == 0);
        observed.reset(); observed.hashFailure = 2;
        try { factory.get(observed); throw new AssertionError("second hash callback skipped"); }
        catch (IllegalStateException expected) { check("hash callback".equals(expected.getMessage())); }
        check(observed.hashes == 2); observed.hashFailure = 0;
        // A distinct custom probe must call its equals method on both passes.
        current.clear(); current.put(key(1001), value); observed.reset();
        check(factory.get(observed) == value);
        check(observed.hashes == 2 && observed.equalities == 2);
        observed.reset(); observed.equalityFailure = 2;
        try { factory.get(observed); throw new AssertionError("second equality callback skipped"); }
        catch (IllegalStateException expected) { check("equals callback".equals(expected.getMessage())); }
        check(observed.hashes == 2 && observed.equalities == 2); observed.equalityFailure = 0;
        observed.reset(); observed.removeOnSecondHash = current;
        check(factory.get(observed) == null);
        check(current.isEmpty() && observed.hashes == 2);
        observed.removeOnSecondHash = null; observed.reset();
        current.put(key(1001), null);
        check(factory.get(observed) == null);
        check(observed.hashes == 2 && observed.equalities == 2);
        current.clear(); current.put(key(1002), value); observed.reset();
        check(factory.get(observed) == null);
        check(observed.hashes == 1);
        // Built-in probes of a stored custom key must not invoke stored callbacks.
        current.clear(); current.put(observed, value); observed.reset();
        check(factory.get(key(1001)) == value);
        check(observed.hashes == 0 && observed.equalities == 0 && observed.indexes == 0);
    }
    private static void collidingKeys(TValueFactory factory, TValue value) {
        java.util.Map<TVariable, TValue> current = factory.getCurrent();
        current.clear();
        TVariable[] keys = new TVariable[80];
        ObservedVariable custom = new ObservedVariable();
        for (int i = 0; i < keys.length; i++) {
            long id = ((long) i << 32) | i; // identical folded hash, distinct IDs
            keys[i] = i == 20 ? custom : key(id);
            keys[i].setId(id);
            current.put(keys[i], i == 30 ? null : value);
        }
        boolean tree = false;
        for (java.util.Map.Entry<TVariable, TValue> entry : current.entrySet())
            tree |= entry.getClass().getName().endsWith("$TreeNode");
        check(tree); custom.reset();
        for (int i = 0; i < keys.length; i++) {
            TValue expected = i == 30 ? null : value;
            check(factory.get(key(((long) i << 32) | i)) == expected);
        }
        check(custom.hashes == 0 && custom.equalities == 0 && custom.indexes == 0);
        custom.reset(); custom.hashFailure = 2;
        try { factory.get(custom); throw new AssertionError("tree second hash callback skipped"); }
        catch (IllegalStateException expected) { check("hash callback".equals(expected.getMessage())); }
        check(custom.hashes == 2); custom.hashFailure = 0;
        check(factory.get(key((100L << 32) | 100)) == null);
        current.remove(keys[10]); check(factory.get(key((10L << 32) | 10)) == null);
        keys[11].setId(7777); check(factory.get(keys[11]) == null);
        current.clear(); current.put(keys[11], value); check(factory.get(keys[11]) == value);
        current.clear();
        for (long id : new long[] {-1, Long.MIN_VALUE, Long.MAX_VALUE}) {
            TVariable variable = key(id); current.put(variable, value);
            check(factory.get(key(id)) == value);
            current.remove(variable); check(factory.get(variable) == null);
        }
        current.put(null, null); check(!factory.isEmpty(null)); check(factory.get((TVariable)null) == null);
        current.put(null, value); check(factory.get((TVariable)null) == value);
        current.clear(); check(factory.get((TVariable)null) == null);
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("direct-current-").toString());
        Mind root = new Mind(new User());
        ITerm a = root.getTerms().add("a"), b = root.getTerms().add("b");
        TVariable variable = root.getTVars().createTVar(new Rule(root), a);
        TValueFactory factory = root.getTValues();
        check(factory.get(variable) == null);
        check(factory.isEmpty(variable));
        factory.getCurrent().put(variable, null);
        check(!factory.isEmpty(variable));
        check(factory.get(variable) == null);
        factory.getCurrent().remove(variable);
        check(factory.isEmpty(variable));
        TValue av = factory.add(variable, a), bv = factory.add(variable, b);
        factory.set(variable, av);
        check(factory.get(variable) == av);
        check(!factory.isEmpty(variable));
        factory.set(variable, bv);
        check(factory.get(variable) == bv);
        Mind child = new Mind(root);
        try {
            check(child.getTValues().get(variable) == null);
            child.getTValues().set(variable, av);
            check(child.getTValues().get(variable) == av);
            check(factory.get(variable) == bv);
        } finally { root.release(child); }
        // Root finalization packs these values: neither term has an active rule.
        check(factory.get(variable) == null);
        factory.set(variable, null);
        check(factory.get(variable) == null);
        check(factory.isEmpty(variable));
        factory.set(null, av);
        check(factory.get((TVariable) null) == av);
        factory.set(null, null);
        check(factory.get((TVariable) null) == null);
        CustomFactory custom = new CustomFactory(root);
        custom.set(variable, av);
        check(custom.get(variable) == av);
        check(custom.reads == 1);
        custom.hide = true;
        check(custom.get(variable) == null);
        check(custom.reads == 2);
        custom.fail = true;
        try { custom.get(variable); throw new AssertionError("override bypassed"); }
        catch (IllegalStateException expected) { check("custom emptiness".equals(expected.getMessage())); }
        check(custom.reads == 3);
        customKeys(factory, av);
        collidingKeys(factory, av);
        System.out.println("GUARDED_CURRENT_LOOKUP_OK checks=" + checks);
    }
}
