/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger;

import org.json.JSONObject;
import org.kanger.interfaces.IReactor;

/**
 * Public read-only operational boundary for browser polling.
 *
 * <p>Only the maintenance notice is exposed here. The full server status stays
 * on the loopback authenticated admin plane.</p>
 */
final class ServerOperationsReactor implements IReactor<JSONObject> {

    private final IReactor<JSONObject> delegate;

    ServerOperationsReactor(IReactor<JSONObject> delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        this.delegate = delegate;
    }

    @Override
    public Object run(JSONObject packet) throws Exception {
        if (isMaintenanceRequest(packet)) {
            return ServerOperations.publicMaintenance();
        }
        return delegate.run(packet);
    }

    static boolean isMaintenanceRequest(JSONObject packet) {
        if (packet == null) {
            return false;
        }
        JSONObject query = packet.optJSONObject("query");
        return query != null
                && "maintenance".equalsIgnoreCase(
                        query.optString("context", ""));
    }
}
