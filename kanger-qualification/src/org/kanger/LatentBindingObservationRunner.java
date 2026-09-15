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
    }
}
