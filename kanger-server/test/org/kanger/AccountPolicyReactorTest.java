/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.account.AccountErrorCode;
import org.kanger.account.RegistrationPolicy;
import org.kanger.interfaces.IReactor;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountPolicyReactorTest {

    @Test
    void trustedPolicyRejectsNewRegistrationBeforeDelegate() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        AccountPolicyReactor reactor = new AccountPolicyReactor(
                RegistrationPolicy.TRUSTED, delegate(called));

        JSONObject response = (JSONObject) reactor.run(packet(
                "login", registration("")));

        assertFalse(called.get());
        assertEquals("error", response.getString("result"));
        assertEquals(AccountErrorCode.REGISTRATION_DISABLED.code(),
                response.getString("code"));
    }

    @Test
    void trustedPolicyTreatsMissingTokenAsNewRegistration() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        JSONObject parameters = new JSONObject()
                .put("register", "rick")
                .put("password", "correct horse battery staple");
        AccountPolicyReactor reactor = new AccountPolicyReactor(
                RegistrationPolicy.TRUSTED, delegate(called));

        JSONObject response = (JSONObject) reactor.run(packet("login", parameters));

        assertFalse(called.get());
        assertEquals(AccountErrorCode.REGISTRATION_DISABLED.code(),
                response.getString("code"));
    }

    @Test
    void emailVerifiedPolicyPassesNewRegistrationToDelegate() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        AccountPolicyReactor reactor = new AccountPolicyReactor(
                RegistrationPolicy.EMAIL_VERIFIED, delegate(called));

        JSONObject response = (JSONObject) reactor.run(packet(
                "login", registration("")));

        assertTrue(called.get());
        assertEquals("OK", response.getString("result"));
    }

    @Test
    void authenticatedProfileUpdatePassesInTrustedMode() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        AccountPolicyReactor reactor = new AccountPolicyReactor(
                RegistrationPolicy.TRUSTED, delegate(called));

        JSONObject response = (JSONObject) reactor.run(packet(
                "login", registration("active-session-token")));

        assertTrue(called.get());
        assertEquals("OK", response.getString("result"));
    }

    @Test
    void unrelatedRequestPassesInTrustedMode() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        AccountPolicyReactor reactor = new AccountPolicyReactor(
                RegistrationPolicy.TRUSTED, delegate(called));

        reactor.run(packet("command", new JSONObject().put("ping", "")));

        assertTrue(called.get());
    }

    private static IReactor<JSONObject> delegate(final AtomicBoolean called) {
        return new IReactor<JSONObject>() {
            @Override
            public Object run(JSONObject packet) {
                called.set(true);
                return new JSONObject().put("result", "OK");
            }
        };
    }

    private static JSONObject registration(String token) {
        return new JSONObject()
                .put("register", "rick")
                .put("password", "correct horse battery staple")
                .put("token", token);
    }

    private static JSONObject packet(String context, JSONObject parameters) {
        return new JSONObject().put("body", new JSONObject()
                .put("context", context)
                .put("parameters", parameters));
    }
}
