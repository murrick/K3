/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.enums.DataType;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.units.Term;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for the zero-arity _set parser collision: [] is a literal SET,
 * not an unevaluated infrastructure function in a predicate argument.
 */
final class EmptySetLiteralTest {

    @Test
    void emptySetIsStoredAndMatchedAsLiteral() throws Exception {
        Mind mind = newMind();

        assertEquals(Boolean.TRUE, mind.query("!family([]);", null, false));
        assertEquals(Boolean.TRUE, mind.query("?family([]);", null, false));
        assertEquals(1, mind.getSolutions().size());

        IRule solution = mind.getSolutions().iterator().next();
        ITerm argument = solution.getArguments().get(0).getValue(mind);
        assertNotNull(argument);
        assertEquals(DataType.SET, argument.getType());
        assertTrue(((Term) argument).semanticMembers().isEmpty());
        assertEquals("!family([]);", solution.toString(mind));
    }

    @Test
    void emptySetBindsToQueryVariable() throws Exception {
        Mind mind = newMind();

        assertEquals(Boolean.TRUE, mind.query("!family([]);", null, false));
        assertEquals(Boolean.TRUE, mind.query("?$e family(e);", null, false));
        assertEquals(1, mind.getSolutions().size());
        assertEquals(1, mind.getValues().size());

        for (Map<String, ITerm> row : mind.getValues()) {
            ITerm value = row.get("e");
            assertNotNull(value, "Expected the query variable e to be bound");
            assertEquals(DataType.SET, value.getType());
            assertTrue(((Term) value).semanticMembers().isEmpty());
        }
    }

    @Test
    void emptyAndNonemptySetLengthsAreComputed() throws Exception {
        Mind mind = newMind();

        assertEquals(Boolean.TRUE, mind.query("?length([]) = 0;", null, false));
        assertEquals(Boolean.TRUE, mind.query("?length([Test]) = 1;", null, false));
        assertEquals(Boolean.TRUE, mind.query("!family([Test]);", null, false));
        assertEquals(Boolean.TRUE, mind.query("?family([Test]);", null, false));
    }

    @Test
    void dynamicSetConstructionStillWorks() throws Exception {
        Mind mind = newMind();

        assertEquals(Boolean.TRUE, mind.query("!@x seed(x) -> singleton([x]);", null, false));
        assertEquals(Boolean.TRUE, mind.query("!seed(Test);", null, false));
        assertEquals(Boolean.TRUE, mind.query("?singleton([Test]);", null, false));
    }

    private static Mind newMind() throws Exception {
        User user = new User();
        new UDF().init(user);
        new DB().init(user);
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        return mind;
    }
}
