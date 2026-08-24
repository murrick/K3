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

class PendingConfirmationCanonicalBoundaryTest {

    @Test
    void invalidConfirmationTokenReachesCanonicalAccountBoundary() throws Exception {
        assertConfirmationFailure(
                AccountErrorCode.CONFIRMATION_TOKEN_INVALID,
                "Confirmation token is invalid");
    }

    @Test
    void expiredConfirmationTokenReachesCanonicalAccountBoundary() throws Exception {
        assertConfirmationFailure(
                AccountErrorCode.CONFIRMATION_TOKEN_EXPIRED,
                "Confirmation token is expired");
    }

    private static void assertConfirmationFailure(AccountErrorCode code,
                                                  String message) throws Exception {
        AtomicBoolean delegated = new AtomicBoolean();
        IReactor<JSONObject> reactor = new CanonicalErrorBoundaryReactor(
                new PendingRegistrationReactor(
                        new RejectingConfirmationGateway(code, message),
                        new NoMail(),
                        delegate(delegated)));
        JSONObject packet = new JSONObject().put("query", new JSONObject()
                .put("parameters", new JSONObject()
                        .put("confirm", "rejected-confirmation-token")));

        JSONObject response = (JSONObject) reactor.run(packet);

        assertFalse(delegated.get());
        assertEquals("error", response.getString("result"), response.toString());
        assertEquals(code.code(), response.getString("code"), response.toString());
        assertEquals(message, response.getString("description"), response.toString());
        assertFalse(response.has("token"));

        JSONObject diagnostic = response.getJSONObject("error");
        assertEquals(1, diagnostic.getInt("schema"));
        assertEquals("account", diagnostic.getString("domain"));
        assertEquals(code.code(), diagnostic.getString("code"));
        assertFalse(diagnostic.getBoolean("retryable"));
        assertEquals("retain", diagnostic.getString("session_action"));
        assertEquals("not_applied", diagnostic.getString("operation_outcome"));
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

    private static final class RejectingConfirmationGateway
            implements PendingRegistrationReactor.Gateway {
        private final AccountErrorCode code;
        private final String message;

        private RejectingConfirmationGateway(AccountErrorCode code, String message) {
            this.code = code;
            this.message = message;
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
            throw new AssertionError("register must not be called");
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
                String confirmationToken) throws Exception {
            throw new PendingRegistrationException(code, message);
        }

        @Override
        public boolean containsLogin(String login) {
            return false;
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
