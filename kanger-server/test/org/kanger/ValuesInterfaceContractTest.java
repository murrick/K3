/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.interfaces.IMind;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValuesInterfaceContractTest {

    @Test
    void orderedValuesMustBeContractOnlyInIMindAndImplementedByMind() throws Exception {
        Method contract = IMind.class.getDeclaredMethod("getValues", ValuesOrder[].class);
        Method implementation = Mind.class.getDeclaredMethod("getValues", ValuesOrder[].class);

        assertFalse(contract.isDefault(),
                "IMind must not publish ordered Values behavior as a default implementation");
        assertTrue(Modifier.isAbstract(contract.getModifiers()),
                "IMind ordered Values method must remain an abstract contract");
        assertFalse(Modifier.isAbstract(implementation.getModifiers()),
                "Mind must own the ordered Values implementation");
    }
}
