/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.units.Rule;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;

/**
 * Focused regression gate for mark/release symmetry when an Escalera-backed
 * factory is marked while its cache root is empty.
 */
public final class KangerEscaleraMarkSafetyRunner {

    private KangerEscaleraMarkSafetyRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            testSingleEmptyRootRollback();
            System.out.println("ESCALERA_MARK_PASS empty-root");

            testNestedEmptyRootRollback();
            System.out.println("ESCALERA_MARK_PASS nested-empty-root");

            System.out.println("ESCALERA_MARK_OK");
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }

    private static Mind newMind(String name) throws Exception {
        IUser user = UserFactory.createUser(name, name);
        new UDF().init(user);
        new DB().init(user);
        return new Mind(user);
    }

    private static TVariable variable(Mind mind, String name) throws Exception {
        Rule owner = new Rule(mind);
        mind.getRules().register(owner);
        return mind.getTVars().createTVar(owner, mind.getTerms().add(name));
    }

    private static void testSingleEmptyRootRollback() throws Exception {
        Mind mind = newMind("escalera-mark-empty");
        TVariable variable = variable(mind, "empty_mark_variable");
        ITerm term = mind.getTerms().add("empty_mark_value");

        require(mind.getTValues().isEmpty(), "TValue cache must start empty");
        mind.getTValues().mark();
        TValue value = mind.getTValues().add(variable, term);
        require(value != null, "marked add did not create TValue");
        mind.getTValues().release();

        require(mind.getTValues().isEmpty(),
                "release of a mark created on an empty cache must restore emptiness");
        require(mind.getTValues().find(variable, term) == null,
                "released TValue remains canonically visible");
        require(mind.getTValues().size() == 0,
                "released TValue remains physically present in cache");
    }

    private static void testNestedEmptyRootRollback() throws Exception {
        Mind mind = newMind("escalera-mark-nested");
        TVariable variable = variable(mind, "nested_mark_variable");
        ITerm outerTerm = mind.getTerms().add("nested_outer_value");
        ITerm innerTerm = mind.getTerms().add("nested_inner_value");

        require(mind.getTValues().isEmpty(), "TValue cache must start empty");
        mind.getTValues().mark();
        TValue outer = mind.getTValues().add(variable, outerTerm);
        require(outer != null, "outer marked add did not create TValue");

        mind.getTValues().mark();
        TValue inner = mind.getTValues().add(variable, innerTerm);
        require(inner != null, "inner marked add did not create TValue");
        mind.getTValues().release();

        require(mind.getTValues().find(variable, innerTerm) == null,
                "inner release retained the inner TValue");
        require(mind.getTValues().find(variable, outerTerm) == outer,
                "inner release removed the outer TValue");

        mind.getTValues().release();
        require(mind.getTValues().isEmpty(),
                "outer release must restore the original empty cache");
        require(mind.getTValues().find(variable, outerTerm) == null,
                "outer release retained canonical outer TValue visibility");
        require(mind.getTValues().size() == 0,
                "outer release left the outer TValue in the physical cache chain");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
