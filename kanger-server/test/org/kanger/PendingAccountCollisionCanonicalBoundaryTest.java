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

class PendingAccountCollisionCanonicalBoundaryTest {

    @Test
    void registrationLoginCollisionReachesCanonicalAccountBoundary()
            throws Exception {
        assertCanonicalCollision(
                new RejectingGateway(
                        AccountErrorCode.LOGIN_ALREADY_USED,
                        null,
                        "Login already belongs to an active account"),
                registrationPacket(),
                AccountErrorCode.LOGIN_ALREADY_USED,
                "Login already belongs to an active account");
    }

    @Test
    void registrationEmailCollisionReachesCanonicalAccountBoundary()
            throws Exception {
        assertCanonicalCollision(
                new RejectingGateway(
                        AccountErrorCode.EMAIL_ALREADY_USED,
                        null,
                        "E-mail already belongs to an active account"),
                registrationPacket(),
                AccountErrorCode.EMAIL_ALREADY_USED,
                "E-mail already belongs to an active account");
    }

    @Test
    void pendingEmailCollisionReachesCanonicalAccountBoundary()
            throws Exception {
        assertCanonicalCollision(
                new RejectingGateway(
                        null,
                        AccountErrorCode.EMAIL_ALREADY_USED,
                        "E-mail already has a pending registration"),
                packet(new JSONObject()
                        .put("pending_action_token", "pending-action")
                        .put("change_pending_email", true)
                        .put("email", "used@example.org")),
                AccountErrorCode.EMAIL_ALREADY_USED,
                "E-mail already has a pending registration");
    }

    private static void assertCanonicalCollision(
            PendingRegistrationReactor.Gateway gateway,
            JSONObject packet,
            AccountErrorCode code,
            String message) throws Exception {
        AtomicBoolean delegated = new AtomicBoolean();
        IReactor<JSONObject> reactor = new CanonicalErrorBoundaryReactor(
                new PendingRegistrationReactor(
                        gateway,
                        new ValidationOnlyMail(),
                        delegate(delegated)));

        JSONObject response = (JSONObject) reactor.run(packet);

        assertFalse(delegated.get());
        assertEquals("error", response.getString("result"), response.toString());
        assertEquals(code.code(), response.getString("code"), response.toString());
        assertEquals(message, response.getString("description"), response.toString());
        assertFalse(response.has("token"));
        assertFalse(response.has("pending_action_token"));

        JSONObject diagnostic = response.getJSONObject("error");
        assertEquals(1, diagnostic.getInt("schema"));
        assertEquals("account", diagnostic.getString("domain"));
        assertEquals(code.code(), diagnostic.getString("code"));
        assertFalse(diagnostic.getBoolean("retryable"));
        assertEquals("retain", diagnostic.getString("session_action"));
        assertEquals("not_applied", diagnostic.getString("operation_outcome"));
    }

    private static JSONObject registrationPacket() {
        return packet(new JSONObject()
                .put("register", "rick")
                .put("password", "pending password")
                .put("email", "rick@example.org")
                .put("privacy", true));
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
        private final AccountErrorCode registerFailure;
        private final AccountErrorCode changeEmailFailure;
        private final String message;

        private RejectingGateway(AccountErrorCode registerFailure,
                                 AccountErrorCode changeEmailFailure,
                                 String message) {
            this.registerFailure = registerFailure;
            this.changeEmailFailure = changeEmailFailure;
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
                Boolean privacyConsent) throws Exception {
            if (registerFailure == null) {
                throw new AssertionError("register must not be called");
            }
            throw failure(registerFailure);
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
                String email) throws Exception {
            if (changeEmailFailure == null) {
                throw new AssertionError("changeEmail must not be called");
            }
            throw failure(changeEmailFailure);
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
            return false;
        }

        private PendingRegistrationException failure(AccountErrorCode code) {
            return new PendingRegistrationException(code, message);
        }
    }

    private static final class ValidationOnlyMail
            implements PendingRegistrationReactor.MailGateway {
        @Override
        public void validateRecipient(String address) {
            // Collision failures occur after input-level recipient validation.
        }

        @Override
        public void queueConfirmation(String login,
                                      String address,
                                      String confirmationToken) {
            throw new AssertionError("mail queue must not be called");
        }
    }
}
