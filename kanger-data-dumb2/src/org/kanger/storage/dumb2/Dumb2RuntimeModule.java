package org.kanger.storage.dumb2;

import org.kanger.bootstrap.RuntimeCapability;
import org.kanger.bootstrap.RuntimeModule;
import org.kanger.interfaces.IUser;

/** ServiceLoader adapter for the default DUMB2 Context storage provider. */
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
