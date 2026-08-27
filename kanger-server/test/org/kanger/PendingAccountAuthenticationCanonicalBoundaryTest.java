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

class PendingAccountAuthenticationCanonicalBoundaryTest {

    @Test
    void pendingLoginAuthenticationFailureReachesCanonicalAccountBoundary()
            throws Exception {
        assertCanonicalAuthenticationFailure(
                new RejectingGateway(true, false,
                        "Pending registration authentication failed"),
                packet(new JSONObject()
                        .put("login", "rick")
                        .put("password", "wrong password")),
                "Pending registration authentication failed");
    }

    @Test
    void scopedActionAuthenticationFailureReachesCanonicalAccountBoundary()
            throws Exception {
        assertCanonicalAuthenticationFailure(
                new RejectingGateway(false, true,
                        "Pending action token is invalid or expired"),
                packet(new JSONObject()
                        .put("pending_action_token", "expired-action-token")
                        .put("resend", true)),
                "Pending action token is invalid or expired");
    }

    private static void assertCanonicalAuthenticationFailure(
            PendingRegistrationReactor.Gateway gateway,
            JSONObject packet,
            String message) throws Exception {
        AtomicBoolean delegated = new AtomicBoolean();
        IReactor<JSONObject> reactor = new CanonicalErrorBoundaryReactor(
                new PendingRegistrationReactor(
                        gateway,
                        new NoMail(),
                        delegate(delegated)));

        JSONObject response = (JSONObject) reactor.run(packet);

        assertFalse(delegated.get());
        assertEquals("error", response.getString("result"), response.toString());
        assertEquals(AccountErrorCode.AUTHENTICATION_FAILED.code(),
                response.getString("code"), response.toString());
        assertEquals(message, response.getString("description"), response.toString());
        assertFalse(response.has("token"));

        JSONObject diagnostic = response.getJSONObject("error");
        assertEquals(1, diagnostic.getInt("schema"));
        assertEquals("account", diagnostic.getString("domain"));
        assertEquals(AccountErrorCode.AUTHENTICATION_FAILED.code(),
                diagnostic.getString("code"));
        assertFalse(diagnostic.getBoolean("retryable"));
        assertEquals("retain", diagnostic.getString("session_action"));
        assertEquals("not_applied", diagnostic.getString("operation_outcome"));
    }

    private static JSONObject packet(JSONObject parameters) {
        return new JSONObject().put("body", new JSONObject()
                .put("context", "login")
                .put("parameters", parameters));
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

    private static final class RejectingGateway
            implements PendingRegistrationReactor.Gateway {
        private final boolean pendingLogin;
        private final boolean rejectResend;
        private final String message;

        private RejectingGateway(boolean pendingLogin,
                                 boolean rejectResend,
                                 String message) {
            this.pendingLogin = pendingLogin;
            this.rejectResend = rejectResend;
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
                String password) throws Exception {
            if (!pendingLogin) {
                throw new AssertionError("authenticate must not be called");
            }
            throw authenticationFailure();
        }

        @Override
        public PendingRegistrationStore.Rotation resend(String actionToken)
                throws Exception {
            if (!rejectResend) {
                throw new AssertionError("resend must not be called");
            }
            throw authenticationFailure();
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
            return pendingLogin;
        }

        private PendingRegistrationException authenticationFailure() {
            return new PendingRegistrationException(
                    AccountErrorCode.AUTHENTICATION_FAILED, message);
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
