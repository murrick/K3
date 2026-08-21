/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.stores.ValuesStore;
import org.kanger.udf.UDF;
import org.kanger.units.Rule;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValuesStoreOrderingIdentityTest {

    @Test
    void orderingMustNotCollapseDistinctRowsWithEqualSortKey() throws Exception {
        Fixture fixture = fixture();

        assertEquals(2, fixture.values.size(), "setup must contain two distinct rows");
        fixture.mind.setOrder("x");

        int observed = 0;
        for (Map<String, ITerm> ignored : fixture.mind.getValues()) {
            observed++;
        }
        assertEquals(2, observed,
                "presentation ordering must never change Values membership");
    }

    @Test
    void orderedProjectionIsStableMultiKeyAndInvocationLocal() throws Exception {
        Fixture fixture = fixture();

        List<Map<String, ITerm>> ordered = fixture.mind.getValues(
                ValuesOrder.asc("x"), ValuesOrder.desc("y"));

        assertEquals(2, ordered.size());
        assertEquals(fixture.paris, ordered.get(0).get("y"));
        assertEquals(fixture.london, ordered.get(1).get("y"));

        assertEquals(fixture.london, fixture.values.get(0).get("y"),
                "ordered projection must not mutate raw Values order");
        assertEquals(fixture.paris, fixture.values.get(1).get("y"),
                "ordered projection must not persist into the raw factory");
    }

    @Test
    void orderedProjectionRejectsInvalidKeysWithoutTouchingValues() throws Exception {
        Fixture fixture = fixture();

        assertThrows(IllegalArgumentException.class,
                () -> fixture.mind.getValues((ValuesOrder[]) null));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.mind.getValues(new ValuesOrder[]{null}));
        assertThrows(IllegalArgumentException.class,
                () -> ValuesOrder.asc(""));

        assertEquals(2, fixture.values.size());
        assertEquals(fixture.london, fixture.values.get(0).get("y"));
    }

    private Fixture fixture() throws Exception {
        String identity = "values-ordering-identity-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        new DB().init(user);
        Mind mind = new Mind(user);

        Rule rule = new Rule(mind);
        TVariable x = mind.getTVars().createTVar(rule, mind.getTerms().add("x"));
        TVariable y = mind.getTVars().createTVar(rule, mind.getTerms().add("y"));

        ITerm shared = mind.getTerms().add("same");
        ITerm london = mind.getTerms().add("London");
        ITerm paris = mind.getTerms().add("Paris");
        TValue xSame = mind.getTValues().add(x, shared);
        TValue yLondon = mind.getTValues().add(y, london);
        TValue yParis = mind.getTValues().add(y, paris);

        ValuesStore values = mind.getValues();
        values.add(Arrays.asList(xSame, yLondon));
        values.add(Arrays.asList(xSame, yParis));
        return new Fixture(mind, values, london, paris);
    }

    private static final class Fixture {
        private final Mind mind;
        private final ValuesStore values;
        private final ITerm london;
        private final ITerm paris;

        private Fixture(Mind mind, ValuesStore values, ITerm london, ITerm paris) {
            this.mind = mind;
            this.values = values;
            this.london = london;
            this.paris = paris;
        }
    }
}
