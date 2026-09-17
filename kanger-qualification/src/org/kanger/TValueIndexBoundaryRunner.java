/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.*;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.internal.IStep;
import org.kanger.storage.*;
import org.kanger.udf.UDF;
import org.kanger.units.*;

/** Raw Escalera protocol qualification, not a physical-deletion rollback promise. */
public final class TValueIndexBoundaryRunner {
    private static Object field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name);
        f.setAccessible(true); return f.get(object);
    }
    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
    private static void unchanged(Escalera cache) throws Exception {
        cache.size(); cache.mark(); cache.release();
        require((Boolean) field(cache, "indexValid") ==
                Boolean.getBoolean("kanger.experiment.preserveTValueIndex"), "no-change guard");
    }
    private static void check(Escalera cache, String label, TValue... values) throws Exception {
        List<Long> actual = new ArrayList<>(), expected = new ArrayList<>();
        for (IStep step = cache.getRoot(); step != null; step = step.getNext()) actual.add(step.getId());
        for (TValue value : values) {
            expected.add(value.getId());
            TValue found = (TValue) cache.get(value.getId());
            require(found != null && found.equalsTo(value), label + " value");
            require(cache.find(value.getHash()).contains(value.getId()), label + " hash");
        }
        require(actual.equals(expected) && cache.size() == values.length, label + " chain/order");
        System.out.println("BOUNDARY " + label + " ids=" + actual);
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("tvalue-boundary-").toString());
        User user = (User) UserFactory.createUser("boundary", "boundary");
        new UDF().init(user); new DB().init(user);
        Mind mind = new Mind(user); user.setCurrentMind(mind);
        mind = (Mind) mind.useStorage("boundary"); user.setCurrentMind(mind);
        try {
            require(mind.compile("!@x p(x) -> q(x);"), "compile variable owner");
            TVariable variable = null;
            for (IRule rule : mind.getRules())
                for (List<Domain> branch : ((Rule) rule).getTree())
                    for (Domain domain : branch)
                        for (int i = 0; i < domain.getRange(); i++)
                            if (domain.get(i).getType() == org.kanger.enums.ArgumentType.TVARIABLE)
                                variable = mind.getTVars().get(domain.get(i).getId());
            require(variable != null, "variable fixture");
            TValue a = mind.getTValues().add(variable, mind.getTerms().add(101));
            TValue b = mind.getTValues().add(variable, mind.getTerms().add(102));
            TValue c = mind.getTValues().add(variable, mind.getTerms().add(103));
            mind.getTerms().update();
            Escalera cache = (Escalera) field(mind.getTValues(), "cache");
            check(cache, "memory", c, b, a);
            cache.mark();
            require(cache.update(), "materialization");
            require(cache.getRoot() instanceof Sapato, "persistent root");
            require(((Map<?, ?>) field(cache, "memoryById")).isEmpty(), "memory entries drained");
            require(((Set<?>) field(cache, "persistentIds")).size() == 3, "persistent IDs");
            boolean rejected = false;
            try { cache.release(); } catch (IllegalStateException expected) { rejected = true; }
            require(rejected, "materialization consumes checkpoints");
            unchanged(cache); check(cache, "materialized", c, b, a);
            Escalera loaded = new Escalera(mind, "tvalues", null);
            unchanged(loaded); check(loaded, "loaded", c, b, a);

            // From here use raw caches only: do not consult factory auxiliary indexes
            // after deliberately deleting canonical entries below factory level.
            IStep root = loaded.getRoot(); loaded.mark(); loaded.delete(b.getId());
            require(loaded.getRoot() == root, "interior deletion keeps root identity");
            loaded.release();
            require(!(Boolean) field(loaded, "indexValid"), "interior mutation fallback");
            require(loaded.get(b.getId()) == null && loaded.find(b.getHash()).isEmpty(), "deleted absent");
            check(loaded, "interior-delete", c, a);
            unchanged(loaded);
            check(new Escalera(mind, "tvalues", null), "deletion-persisted", c, a);
            loaded.mark(); loaded.deleteAll(Arrays.asList(a.getId(), a.getId(), -1L)); loaded.release();
            require(!(Boolean) field(loaded, "indexValid"), "batch fallback");
            check(loaded, "batch-delete", c);
            unchanged(loaded);
        } finally {
            user.setCurrentMind(user.getCurrentMind().closeStorage());
        }

        Mind parent = new Mind(new User());
        TVariable v = parent.getTVars().createTVar(new Rule(parent), parent.getTerms().add("x"));
        TValue first = parent.getTValues().add(v, parent.getTerms().add(1));
        Escalera pc = (Escalera) field(parent.getTValues(), "cache");
        Mind child = new Mind(parent);
        TValue second = child.getTValues().add(v, child.getTerms().add(2));
        Escalera cc = (Escalera) field(child.getTValues(), "cache");
        unchanged(pc); unchanged(cc);
        check(pc, "parent-isolated", first); check(cc, "child-overlay", second, first);
        pc.mark();
        parent.getTValues().commit(child.getTValues());
        check(pc, "factory-transfer", second, first);
        pc.release();
        require(!(Boolean) field(pc, "indexValid"), "transfer fallback");
        check(pc, "raw-root-restored", first);
        unchanged(pc);
        System.out.println("TVALUE_INDEX_BOUNDARY_PASS");
    }
}
