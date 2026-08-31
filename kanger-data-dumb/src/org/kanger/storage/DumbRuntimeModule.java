package org.kanger.storage;

import org.kanger.bootstrap.RuntimeCapability;
import org.kanger.bootstrap.RuntimeModule;
import org.kanger.interfaces.IUser;

/**
 * ServiceLoader adapter exposing the DUMB storage implementation.
 */
public final class DumbRuntimeModule implements RuntimeModule {

    @Override
    public RuntimeCapability getCapability() {
        return RuntimeCapability.STORAGE;
    }

    @Override
    public String getId() {
        return "dumb";
    }

    @Override
    public String getDescription() {
        return "DUMB data model";
    }

    @Override
    public void init(IUser user) {
        new DB().init(user);
    }
}
