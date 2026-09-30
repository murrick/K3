package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.kanger.User;
import org.kanger.bootstrap.RuntimeCapability;
import org.kanger.bootstrap.RuntimeModule;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

public class Dumb2RuntimeModuleTest {

    @Test
    void serviceLoaderDiscoversDumb2Provider() {
        List<RuntimeModule> providers = new ArrayList<RuntimeModule>();
        for (RuntimeModule module : ServiceLoader.load(RuntimeModule.class)) {
            if (module.getCapability() == RuntimeCapability.STORAGE) {
                providers.add(module);
            }
        }

        assertEquals(1, providers.size());
        assertEquals("dumb2", providers.get(0).getId());
        assertEquals("DUMB 2.0 data model", providers.get(0).getDescription());
    }

    @Test
    void providerAttachesDumb2WithoutOpeningAContext() throws Exception {
        User user = new User();
        Dumb2RuntimeModule module = new Dumb2RuntimeModule();

        module.init(user);

        assertEquals("DUMB 2.0 data model", user.getData().getDescription());
        assertFalse(user.getData() == null);
        assertSame(user.getData(), user.getData());
    }
}
