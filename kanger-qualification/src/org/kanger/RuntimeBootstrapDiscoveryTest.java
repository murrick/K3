package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.bootstrap.RuntimeBootstrap;
import org.kanger.bootstrap.RuntimeBootstrapResult;
import org.kanger.bootstrap.RuntimeCapability;
import org.kanger.interfaces.internal.IData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Qualifies ServiceLoader composition without requiring Console or Server to
 * name concrete runtime plugin implementations.
 */
public class RuntimeBootstrapDiscoveryTest {

    @Test
    public void discoversAttachesAndPreservesRuntimeModules() throws Exception {
        User user = new User();

        RuntimeBootstrapResult first = RuntimeBootstrap.ensure(user);
        assertTrue(first.loaded(RuntimeCapability.STORAGE));
        assertEquals("DUMB data model",
                first.getDescription(RuntimeCapability.STORAGE));
        assertTrue(first.loaded(RuntimeCapability.UDF));
        assertEquals("Rhino JavaScript UDF",
                first.getDescription(RuntimeCapability.UDF));

        IData data = user.getData();
        Class<?> udfClass = user.getUdf().getClass();

        RuntimeBootstrapResult second = RuntimeBootstrap.ensure(user);
        assertFalse(second.loaded(RuntimeCapability.STORAGE));
        assertFalse(second.loaded(RuntimeCapability.UDF));
        assertSame(data, user.getData());
        assertEquals(udfClass, user.getUdf().getClass());
    }
}
