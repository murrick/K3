package org.kanger.bootstrap;

import org.kanger.interfaces.IUser;

/**
 * Service-provider contract for an optional KANGER runtime module.
 *
 * <p>Implementations are discovered through {@link java.util.ServiceLoader}.
 * The module owns only capability attachment; selection and orchestration are
 * performed by {@link RuntimeBootstrap}.</p>
 */
public interface RuntimeModule {

    RuntimeCapability getCapability();

    String getId();

    String getDescription();

    void init(IUser user) throws Exception;
}
