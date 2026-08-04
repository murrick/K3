/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger;

import org.json.JSONObject;
import org.kanger.account.RegistrationPolicy;
import org.kanger.interfaces.IReactor;

/**
 * Adds the stable public authentication topology to the ordinary version
 * response. The browser receives product capabilities rather than transport
 * configuration and therefore does not need to infer policy by attempting a
 * registration mutation.
 */
final class PublicAuthCapabilitiesReactor implements IReactor<JSONObject> {

    private final RegistrationPolicy policy;
    private final IReactor<JSONObject> delegate;

    PublicAuthCapabilitiesReactor(RegistrationPolicy policy,
                                  IReactor<JSONObject> delegate) {
        if (policy == null || delegate == null) {
            throw new IllegalArgumentException("policy and delegate must not be null");
        }
        this.policy = policy;
        this.delegate = delegate;
    }

    @Override
    public Object run(JSONObject packet) throws Exception {
        Object response = delegate.run(packet);
        if (response instanceof JSONObject && isVersionRequest(packet)) {
            ((JSONObject) response).put("auth", capabilities(policy));
        }
        return response;
    }

    static JSONObject capabilities(RegistrationPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("policy must not be null");
        }
        boolean verified = policy == RegistrationPolicy.EMAIL_VERIFIED;
        return new JSONObject()
                .put("registration_policy", policy.name())
                .put("public_registration", policy.allowsPublicSelfRegistration())
                .put("email_confirmation_required", verified)
                .put("confirmation_creates_session", false)
                .put("pending_registration_actions", verified);
    }

    static boolean isVersionRequest(JSONObject packet) {
        if (packet == null) {
            return false;
        }
        JSONObject body = packet.optJSONObject("body");
        if (body != null && "version".equalsIgnoreCase(
                body.optString("context", ""))) {
            return true;
        }
        JSONObject query = packet.optJSONObject("query");
        return query != null && "version".equalsIgnoreCase(
                query.optString("context", ""));
    }
}
