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
import org.kanger.account.PendingRegistrationService;
import org.kanger.account.PendingRegistrationStore;
import org.kanger.interfaces.IReactor;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PendingPrivacyConsentCanonicalBoundaryTest {

    @Test
    void missingPrivacyConsentReachesCanonicalAccountBoundary()
            throws Exception {
        AtomicBoolean registered = new AtomicBoolean();
        AtomicBoolean delegated = new AtomicBoolean();
        IReactor<JSONObject> reactor = new CanonicalErrorBoundaryReactor(
                new PendingRegistrationReactor(
                        new PrivacyRejectingGateway(registered),
                        new AcceptingMail(),
                        packet -> {
                            delegated.set(true);
                            return new JSONObject().put("result", "OK");
                        }));

        JSONObject response = (JSONObject) reactor.run(new JSONObject()
                .put("body", new JSONObject()
                        .put("context", "login")
                        .put("parameters", new JSONObject()
                                .put("register", "rick")
                                .put("password", "pending password")
                                .put("email", "rick@example.org")
                                .put("privacy", false))));

        assertFalse(registered.get());
        assertFalse(delegated.get());
        assertEquals("error", response.getString("result"), response.toString());
        assertEquals(AccountErrorCode.PRIVACY_CONSENT_REQUIRED.code(),
                response.getString("code"), response.toString());
        assertEquals("Privacy consent is required",
                response.getString("description"), response.toString());

        JSONObject diagnostic = response.getJSONObject("error");
        assertEquals(1, diagnostic.getInt("schema"));
        assertEquals("account", diagnostic.getString("domain"));
        assertEquals(AccountErrorCode.PRIVACY_CONSENT_REQUIRED.code(),
                diagnostic.getString("code"));
        assertFalse(diagnostic.getBoolean("retryable"));
        assertEquals("retain", diagnostic.getString("session_action"));
        assertEquals("not_applied", diagnostic.getString("operation_outcome"));
    }

    private static final class PrivacyRejectingGateway
            implements PendingRegistrationReactor.Gateway {
        private final AtomicBoolean registered;

        private PrivacyRejectingGateway(AtomicBoolean registered) {
            this.registered = registered;
        }

        @Override
        public PendingRegistrationStore.Created register(
                String login,
                String password,
                String email,
                String name,
                String country,
                String city,
                Boolean privacyConsent) {
            registered.set(true);
            throw new AssertionError("registration must not start without privacy consent");
        }

        @Override
        public PendingRegistrationStore.Authenticated authenticate(
                String login,
                String password) {
            throw new AssertionError("authenticate must not be called");
        }

        @Override
        public PendingRegistrationStore.Rotation resend(String actionToken) {
            throw new AssertionError("resend must not be called");
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

    private static final class AcceptingMail
            implements PendingRegistrationReactor.MailGateway {
        @Override
        public void validateRecipient(String address) {
            // Privacy is checked after recipient validation in the registration path.
        }

        @Override
        public void queueConfirmation(String login,
                                      String address,
                                      String confirmationToken) {
            throw new AssertionError("mail must not be queued without privacy consent");
        }
    }
}
