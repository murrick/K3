package org.kanger.storage.dumb2;

import org.kanger.bootstrap.RuntimeCapability;
import org.kanger.bootstrap.RuntimeModule;
import org.kanger.interfaces.IUser;

/**
 * ServiceLoader adapter exposing DUMB 2.0 for explicit manual-soak runtimes.
 *
 * <p>The normal Console/Server runtime layout still contains only the stable
 * DUMB provider. DUMB2 is selected by launchers that deliberately place this
 * module, and not kanger-data-dumb, on the runtime classpath.</p>
 */
public final class Dumb2RuntimeModule implements RuntimeModule {

    @Override
    public RuntimeCapability getCapability() {
        return RuntimeCapability.STORAGE;
    }

    @Override
    public String getId() {
        return "dumb2";
    }

    @Override
    public String getDescription() {
        return "DUMB 2.0 data model";
    }

    @Override
    public void init(IUser user) {
        new DB().init(user);
    }
}
