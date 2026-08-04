/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.account.RegistrationPolicy;
import org.kanger.interfaces.IReactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicAuthCapabilitiesReactorTest {

    @Test
    void trustedVersionResponseDisablesPublicRegistration() throws Exception {
        JSONObject delegated = new JSONObject()
                .put("result", "OK")
                .put("version", "test");
        PublicAuthCapabilitiesReactor reactor = reactor(
                RegistrationPolicy.TRUSTED, delegated);

        JSONObject response = (JSONObject) reactor.run(packet("version"));
        JSONObject auth = response.getJSONObject("auth");

        assertSame(delegated, response);
        assertEquals("TRUSTED", auth.getString("registration_policy"));
        assertFalse(auth.getBoolean("public_registration"));
        assertFalse(auth.getBoolean("email_confirmation_required"));
        assertFalse(auth.getBoolean("confirmation_creates_session"));
        assertFalse(auth.getBoolean("pending_registration_actions"));
    }

    @Test
    void verifiedVersionResponseExposesPendingRegistrationTopology()
            throws Exception {
        JSONObject delegated = new JSONObject().put("result", "OK");
        PublicAuthCapabilitiesReactor reactor = reactor(
                RegistrationPolicy.EMAIL_VERIFIED, delegated);

        JSONObject auth = ((JSONObject) reactor.run(packet("version")))
                .getJSONObject("auth");

        assertEquals("EMAIL_VERIFIED",
                auth.getString("registration_policy"));
        assertTrue(auth.getBoolean("public_registration"));
        assertTrue(auth.getBoolean("email_confirmation_required"));
        assertFalse(auth.getBoolean("confirmation_creates_session"));
        assertTrue(auth.getBoolean("pending_registration_actions"));
    }

    @Test
    void queryFormVersionRequestIsRecognized() throws Exception {
        JSONObject packet = new JSONObject().put("query", new JSONObject()
                .put("context", "version")
                .put("parameters", new JSONObject()));
        JSONObject delegated = new JSONObject().put("result", "OK");

        JSONObject response = (JSONObject) reactor(
                RegistrationPolicy.TRUSTED, delegated).run(packet);

        assertTrue(response.has("auth"));
    }

    @Test
    void unrelatedResponseIsNotModified() throws Exception {
        JSONObject delegated = new JSONObject().put("result", "OK");

        JSONObject response = (JSONObject) reactor(
                RegistrationPolicy.TRUSTED, delegated).run(packet("command"));

        assertSame(delegated, response);
        assertFalse(response.has("auth"));
    }

    private static PublicAuthCapabilitiesReactor reactor(
            RegistrationPolicy policy,
            final JSONObject response) {
        return new PublicAuthCapabilitiesReactor(policy,
                new IReactor<JSONObject>() {
                    @Override
                    public Object run(JSONObject packet) {
                        return response;
                    }
                });
    }

    private static JSONObject packet(String context) {
        return new JSONObject().put("body", new JSONObject()
                .put("context", context)
                .put("parameters", new JSONObject()));
    }
}
