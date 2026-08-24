/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */

package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.account.AccountErrorCode;
import org.kanger.account.PendingRegistration;
import org.kanger.account.PendingRegistrationException;
import org.kanger.account.PendingRegistrationService;
import org.kanger.account.PendingRegistrationStore;
import org.kanger.interfaces.IReactor;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingAccountRateLimitCanonicalBoundaryTest {

    @Test
    void resendCooldownReachesRetryableCanonicalAccountBoundary()
            throws Exception {
        AtomicBoolean delegated = new AtomicBoolean();
        IReactor<JSONObject> reactor = new CanonicalErrorBoundaryReactor(
                new PendingRegistrationReactor(
                        new RateLimitedGateway(),
                        new NoMail(),
                        delegate(delegated)));

        JSONObject response = (JSONObject) reactor.run(packet());

        assertFalse(delegated.get());
        assertEquals("error", response.getString("result"), response.toString());
        assertEquals(AccountErrorCode.RESEND_RATE_LIMITED.code(),
                response.getString("code"), response.toString());
        assertEquals("Confirmation resend cooldown is active",
                response.getString("description"), response.toString());
        assertFalse(response.has("pending_action_token"));

        JSONObject diagnostic = response.getJSONObject("error");
        assertEquals(1, diagnostic.getInt("schema"));
        assertEquals("account", diagnostic.getString("domain"));
        assertEquals(AccountErrorCode.RESEND_RATE_LIMITED.code(),
                diagnostic.getString("code"));
        assertTrue(diagnostic.getBoolean("retryable"));
        assertEquals("retain", diagnostic.getString("session_action"));
        assertEquals("not_applied", diagnostic.getString("operation_outcome"));
    }

    private static JSONObject packet() {
        return new JSONObject().put("body", new JSONObject()
                .put("context", "login")
                .put("parameters", new JSONObject()
                        .put("pending_action_token", "pending-action")
                        .put("resend", true)));
    }

    private static IReactor<JSONObject> delegate(final AtomicBoolean called) {
        return new IReactor<JSONObject>() {
            @Override
            public Object run(JSONObject packet) {
                called.set(true);
                return new JSONObject().put("result", "legacy");
            }
        };
    }

    private static final class RateLimitedGateway
            implements PendingRegistrationReactor.Gateway {
        @Override
        public PendingRegistrationStore.Created register(
                String login,
                String password,
                String email,
                String name,
                String country,
                String city,
                Boolean privacyConsent) {
            throw new AssertionError("register must not be called");
        }

        @Override
        public PendingRegistrationStore.Authenticated authenticate(
                String login,
                String password) {
            throw new AssertionError("authenticate must not be called");
        }

        @Override
        public PendingRegistrationStore.Rotation resend(String actionToken)
                throws Exception {
            throw new PendingRegistrationException(
                    AccountErrorCode.RESEND_RATE_LIMITED,
                    "Confirmation resend cooldown is active");
        }

        @Override
        public PendingRegistrationStore.Rotation changeEmail(
                String actionToken,
                String email) {
            throw new AssertionError("changeEmail must not be called");
        }

        @Override
        public PendingRegistration cancel(String actionToken) {
            throw new AssertionError("cancel must not be called");
        }

        @Override
        public PendingRegistrationService.Activation confirm(
                String confirmationToken) {
            throw new AssertionError("confirm must not be called");
        }

        @Override
        public boolean containsLogin(String login) {
            throw new AssertionError("containsLogin must not be called");
        }
    }

    private static final class NoMail
            implements PendingRegistrationReactor.MailGateway {
        @Override
        public void validateRecipient(String address) {
            throw new AssertionError("mail validation must not be called");
        }

        @Override
        public void queueConfirmation(String login,
                                      String address,
                                      String confirmationToken) {
            throw new AssertionError("mail queue must not be called");
        }
    }
}
