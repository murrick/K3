/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.*;
import org.kanger.units.*;

/** Boundary observations must discard rolled-back additions and retain tuple dependencies. */
public final class LatentBindingObservationRunner {
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("latent-bindings-").toString());
        Mind mind = new Mind(new User());
        Rule a = (Rule) mind.compileLine("!@x p(x);", false, null);
        Rule b = (Rule) mind.compileLine("!@y q(y);", false, null);
        TVariable x = a.getTVariables().get(0), y = b.getTVariables().get(0);
        Linker linker = new Linker(mind);
        Method consumers = Linker.class.getDeclaredMethod("observeConsumers");
        Method bindings = Linker.class.getDeclaredMethod("observeBindings", Map.class);
        Method changes = Linker.class.getDeclaredMethod("recordBindingChanges", Map.class);
        consumers.setAccessible(true); bindings.setAccessible(true); changes.setAccessible(true);
        Object before = bindings.invoke(linker, consumers.invoke(linker));
        mind.getTValues().mark();
        mind.getTValues().add(x, mind.getTerms().add(10));
        mind.getTValues().mark();
        mind.getTValues().add(y, mind.getTerms().add(20));
        mind.getTValues().commit();
        mind.getTValues().release();
        changes.invoke(linker, before);
        if (!linker.snapshotStatistics().getBindingTrace().isEmpty())
            throw new AssertionError("Rolled-back values observed");
        mind.getTValues().mark();
        TValue vx = mind.getTValues().add(x, mind.getTerms().add(30));
        TValue vy = mind.getTValues().add(y, mind.getTerms().add(40));
        mind.getTValues().commit();
        mind.addTSolve(Arrays.asList(vx, vy));
        changes.invoke(linker, before);
        List<String> rows = linker.snapshotStatistics().getBindingTrace();
        if (rows.size() != 2) throw new AssertionError("Committed values missing: " + rows);
        if (!rows.get(0).contains("tuple-extra=[" + b.getId() + "]")
                || !rows.get(1).contains("tuple-extra=[" + a.getId() + "]"))
            throw new AssertionError("Cross-rule tuple dependency missing: " + rows);
        System.out.println("LATENT_BINDING_OBSERVATION_PASS");
        tupleOnlyChange();
    }

    private static void tupleOnlyChange() throws Exception {
        Mind mind = new Mind(new User());
        Rule rule = (Rule) mind.compileLine("!@x @y pair(x,y);", false, null);
        TVariable x = rule.getTVariables().get(0), y = rule.getTVariables().get(1);
        TValue vx = mind.getTValues().add(x, mind.getTerms().add(10));
        TValue y1 = mind.getTValues().add(y, mind.getTerms().add(20));
        TValue y2 = mind.getTValues().add(y, mind.getTerms().add(30));
        x.setCurrent(vx); y.setCurrent(y2);
        SortedSet<TVariable> variables = new TreeSet<>(Arrays.asList(x, y));
        Linker linker = new Linker(mind);
        Method valid = Linker.class.getDeclaredMethod("isValidFor", SortedSet.class);
        Method tuples = Linker.class.getDeclaredMethod("observeTuples");
        Method consumers = Linker.class.getDeclaredMethod("observeConsumers");
        Method bindings = Linker.class.getDeclaredMethod("observeBindings", Map.class);
        Method changes = Linker.class.getDeclaredMethod("recordTupleChanges", Map.class, Map.class);
        for (Method method : Arrays.asList(valid, tuples, consumers, bindings, changes)) method.setAccessible(true);
        if (!Boolean.TRUE.equals(valid.invoke(linker, variables))) throw new AssertionError("Unconstrained tuple");
        mind.addTSolve(Arrays.asList(vx, y1));
        if (!Boolean.FALSE.equals(valid.invoke(linker, variables))) throw new AssertionError("Missing tuple must exclude assignment");
        Object before = tuples.invoke(linker);
        Object valuesBefore = bindings.invoke(linker, consumers.invoke(linker));
        mind.addTSolve(Arrays.asList(vx, y2));
        mind.addTSolve(Arrays.asList(vx, y2)); // Duplicate is not a relation change.
        if (!Boolean.TRUE.equals(valid.invoke(linker, variables))) throw new AssertionError("New tuple must admit assignment");
        if (!valuesBefore.equals(bindings.invoke(linker, consumers.invoke(linker))))
            throw new AssertionError("Fixture changed values");
        changes.invoke(linker, before, valuesBefore);
        List<String> rows = linker.snapshotStatistics().getTupleTrace();
        if (rows.size() != 1 || !rows.get(0).contains("all-values-preexisting=true")
                || !rows.get(0).contains("consumers=[" + rule.getId() + "]"))
            throw new AssertionError("Tuple-only dependency missing: " + rows);
        System.out.println("LATENT_TUPLE_ONLY_ACTIVATION_PASS");
    }
}
