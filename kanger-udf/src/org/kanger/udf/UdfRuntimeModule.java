package org.kanger.udf;

import org.kanger.bootstrap.RuntimeCapability;
import org.kanger.bootstrap.RuntimeModule;
import org.kanger.interfaces.IUser;

/**
 * ServiceLoader adapter exposing the bundled Rhino UDF capability.
 */
public final class UdfRuntimeModule implements RuntimeModule {

    @Override
    public RuntimeCapability getCapability() {
        return RuntimeCapability.UDF;
    }

    @Override
    public String getId() {
        return "rhino";
    }

    @Override
    public String getDescription() {
        return "Rhino JavaScript UDF";
    }

    @Override
    public void init(IUser user) {
        new UDF().init(user);
    }
}
