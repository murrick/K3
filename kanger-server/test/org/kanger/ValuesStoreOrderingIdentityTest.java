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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ValuesStoreOrderingIdentityTest {

    @Test
    void orderingMustNotCollapseDistinctRowsWithEqualSortKey() throws Exception {
        String identity = "values-ordering-identity-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        new DB().init(user);
        Mind mind = new Mind(user);

        Rule rule = new Rule(mind);
        TVariable x = mind.getTVars().createTVar(rule, mind.getTerms().add("x"));
        TVariable y = mind.getTVars().createTVar(rule, mind.getTerms().add("y"));

        ITerm shared = mind.getTerms().add("same");
        TValue xSame = mind.getTValues().add(x, shared);
        TValue yLondon = mind.getTValues().add(y, mind.getTerms().add("London"));
        TValue yParis = mind.getTValues().add(y, mind.getTerms().add("Paris"));

        ValuesStore values = mind.getValues();
        values.add(Arrays.asList(xSame, yLondon));
        values.add(Arrays.asList(xSame, yParis));
        assertEquals(2, values.size(), "setup must contain two distinct rows");

        mind.setOrder("x");

        int observed = 0;
        for (Map<String, ITerm> ignored : mind.getValues()) {
            observed++;
        }
        assertEquals(2, observed,
                "presentation ordering must never change Values membership");
    }
}
