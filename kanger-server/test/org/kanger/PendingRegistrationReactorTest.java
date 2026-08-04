/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.account.AccountErrorCode;
import org.kanger.account.PendingRegistration;
import org.kanger.account.PendingRegistrationService;
import org.kanger.account.PendingRegistrationStore;
import org.kanger.interfaces.IReactor;

import java.lang.reflect.Constructor;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingRegistrationReactorTest {

    @Test
    void registrationPersistsPendingAndNeverCallsLegacyDelegate() throws Exception {
        FakeGateway gateway = new FakeGateway();
        FakeMail mail = new FakeMail();
        AtomicBoolean delegated = new AtomicBoolean();
        PendingRegistrationReactor reactor = new PendingRegistrationReactor(
                gateway, mail, delegate(delegated));

        JSONObject response = (JSONObject) reactor.run(packet(new JSONObject()
                .put("register", "rick")
                .put("password", "pending password")
                .put("email", "rick@example.org")
                .put("privacy", true)));

        assertFalse(delegated.get());
        assertTrue(gateway.registered);
        assertTrue(mail.queued);
        assertEquals("OK", response.getString("result"));
        assertEquals("PENDING_CONFIRMATION", response.getString("state"));
        assertFalse(response.has("token"));
        assertEquals("r***@example.org", response.getString("email_hint"));
    }

    @Test
    void mailQueueFailureRetainsPendingAndReturnsStructuredFailure()
            throws Exception {
        FakeGateway gateway = new FakeGateway();
        FakeMail mail = new FakeMail();
        mail.failQueue = true;
        AtomicBoolean delegated = new AtomicBoolean();
        PendingRegistrationReactor reactor = new PendingRegistrationReactor(
                gateway, mail, delegate(delegated));

        JSONObject response = (JSONObject) reactor.run(packet(new JSONObject()
                .put("register", "rick")
                .put("password", "pending password")
                .put("email", "rick@example.org")
                .put("privacy", true)));

        assertFalse(delegated.get());
        assertTrue(gateway.registered);
        assertEquals("error", response.getString("result"));
        assertEquals(AccountErrorCode.MAIL_DELIVERY_UNAVAILABLE.code(),
                response.getString("code"));
        assertEquals("PENDING_CONFIRMATION", response.getString("state"));
        assertFalse(response.has("token"));
    }

    @Test
    void pendingLoginReturnsOnlyScopedActionToken() throws Exception {
        FakeGateway gateway = new FakeGateway();
        gateway.pendingLogin = true;
        AtomicBoolean delegated = new AtomicBoolean();
        PendingRegistrationReactor reactor = new PendingRegistrationReactor(
                gateway, new FakeMail(), delegate(delegated));

        JSONObject response = (JSONObject) reactor.run(packet(new JSONObject()
                .put("login", "rick")
                .put("password", "pending password")));

        assertFalse(delegated.get());
        assertTrue(gateway.authenticatedPending);
        assertEquals("error", response.getString("result"));
        assertEquals(AccountErrorCode.EMAIL_CONFIRMATION_REQUIRED.code(),
                response.getString("code"));
        assertEquals("pending-action", response.getString("pending_action_token"));
        assertFalse(response.has("token"));
        assertTrue(response.getBoolean("can_resend"));
        assertTrue(response.getBoolean("can_change_email"));
        assertTrue(response.getBoolean("can_cancel"));
    }

    @Test
    void confirmationActivatesWithoutSessionAndNeverDelegates() throws Exception {
        FakeGateway gateway = new FakeGateway();
        AtomicBoolean delegated = new AtomicBoolean();
        PendingRegistrationReactor reactor = new PendingRegistrationReactor(
                gateway, new FakeMail(), delegate(delegated));

        JSONObject response = (JSONObject) reactor.run(packet(new JSONObject()
                .put("confirm", "confirmation-token")));

        assertFalse(delegated.get());
        assertEquals("OK", response.getString("result"));
        assertEquals("EMAIL_CONFIRMED", response.getString("state"));
        assertEquals(17L, response.getLong("user_id"));
        assertFalse(response.has("token"));
    }

    @Test
    void resendChangeEmailAndCancelStayInsidePendingBoundary()
            throws Exception {
        FakeGateway gateway = new FakeGateway();
        FakeMail mail = new FakeMail();
        AtomicBoolean delegated = new AtomicBoolean();
        PendingRegistrationReactor reactor = new PendingRegistrationReactor(
                gateway, mail, delegate(delegated));

        JSONObject resent = (JSONObject) reactor.run(packet(new JSONObject()
                .put("pending_action_token", "pending-action")
                .put("resend", true)));
        assertEquals("CONFIRMATION_RESENT", resent.getString("state"));
        assertTrue(mail.queued);

        mail.queued = false;
        JSONObject changed = (JSONObject) reactor.run(packet(new JSONObject()
                .put("pending_action_token", "pending-action")
                .put("change_pending_email", true)
                .put("email", "new@example.org")));
        assertEquals("PENDING_EMAIL_CHANGED", changed.getString("state"));
        assertEquals("next-action", changed.getString("pending_action_token"));
        assertTrue(mail.queued);

        JSONObject cancelled = (JSONObject) reactor.run(packet(new JSONObject()
                .put("pending_action_token", "next-action")
                .put("cancel_pending", true)));
        assertEquals("PENDING_REGISTRATION_CANCELLED",
                cancelled.getString("state"));
        assertFalse(delegated.get());
        assertTrue(gateway.cancelled);
    }

    @Test
    void legacyResendWithoutScopedTokenNeverDelegatesOrQueuesMail()
            throws Exception {
        FakeGateway gateway = new FakeGateway();
        FakeMail mail = new FakeMail();
        AtomicBoolean delegated = new AtomicBoolean();
        PendingRegistrationReactor reactor = new PendingRegistrationReactor(
                gateway, mail, delegate(delegated));

        JSONObject response = (JSONObject) reactor.run(packet(new JSONObject()
                .put("token", "ordinary-session-token")
                .put("resend", true)));

        assertFalse(delegated.get());
        assertFalse(mail.queued);
        assertEquals("error", response.getString("result"));
        assertEquals(AccountErrorCode.AUTHENTICATION_FAILED.code(),
                response.getString("code"));
        assertFalse(response.has("token"));
    }

    @Test
    void authenticatedActiveLoginWinsOverStalePendingRecord()
            throws Exception {
        FakeGateway gateway = new FakeGateway();
        gateway.pendingLogin = true;
        AtomicBoolean delegated = new AtomicBoolean();
        PendingRegistrationReactor reactor = new PendingRegistrationReactor(
                gateway, new FakeMail(), delegate(delegated));
        JSONObject packet = packet(new JSONObject()
                .put("login", "rick")
                .put("password", "active password"));
        SessionSerializingReactor.markAuthenticatedCredential(packet);

        JSONObject response = (JSONObject) reactor.run(packet);

        assertTrue(delegated.get());
        assertFalse(gateway.authenticatedPending);
        assertEquals("delegated", response.getString("state"));
    }

    @Test
    void ordinaryActiveLoginAndUnrelatedRequestsDelegate() throws Exception {
        FakeGateway gateway = new FakeGateway();
        AtomicBoolean delegated = new AtomicBoolean();
        PendingRegistrationReactor reactor = new PendingRegistrationReactor(
                gateway, new FakeMail(), delegate(delegated));

        JSONObject activeLogin = (JSONObject) reactor.run(packet(new JSONObject()
                .put("login", "active")
                .put("password", "active password")));

        assertTrue(delegated.get());
        assertEquals("delegated", activeLogin.getString("state"));

        delegated.set(false);
        JSONObject unrelated = new JSONObject()
                .put("body", new JSONObject()
                        .put("context", "command")
                        .put("parameters", new JSONObject().put("ping", "")));
        reactor.run(unrelated);
        assertTrue(delegated.get());
    }

    private static IReactor<JSONObject> delegate(final AtomicBoolean called) {
        return new IReactor<JSONObject>() {
            @Override
            public Object run(JSONObject packet) {
                called.set(true);
                return new JSONObject()
                        .put("result", "OK")
                        .put("state", "delegated");
            }
        };
    }

    private static JSONObject packet(JSONObject parameters) {
        return new JSONObject().put("body", new JSONObject()
                .put("context", "login")
                .put("parameters", parameters));
    }

    private static PendingRegistration registration(String email) {
        return new PendingRegistration(
                "pending-id",
                "rick",
                email,
                null,
                "Rick",
                "Austria",
                "Vienna",
                Boolean.TRUE,
                1L,
                Long.MAX_VALUE,
                Long.MAX_VALUE,
                0,
                1L);
    }

    private static PendingRegistrationStore.Created created() throws Exception {
        Constructor<PendingRegistrationStore.Created> constructor =
                PendingRegistrationStore.Created.class.getDeclaredConstructor(
                        PendingRegistration.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                registration("rick@example.org"), "confirmation-token");
    }

    private static PendingRegistrationStore.Authenticated authenticated()
            throws Exception {
        Constructor<PendingRegistrationStore.Authenticated> constructor =
                PendingRegistrationStore.Authenticated.class.getDeclaredConstructor(
                        PendingRegistration.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                registration("rick@example.org"), "pending-action");
    }

    private static PendingRegistrationStore.Rotation rotation(String email)
            throws Exception {
        Constructor<PendingRegistrationStore.Rotation> constructor =
                PendingRegistrationStore.Rotation.class.getDeclaredConstructor(
                        PendingRegistration.class, String.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                registration(email), "next-confirmation", "next-action");
    }

    private static PendingRegistrationService.Activation activation()
            throws Exception {
        Constructor<PendingRegistrationService.Activation> constructor =
                PendingRegistrationService.Activation.class.getDeclaredConstructor(
                        long.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(17L, false);
    }

    private static final class FakeGateway
            implements PendingRegistrationReactor.Gateway {
        private boolean registered;
        private boolean pendingLogin;
        private boolean authenticatedPending;
        private boolean cancelled;

        @Override
        public PendingRegistrationStore.Created register(
                String login,
                String password,
                String email,
                String name,
                String country,
                String city,
                Boolean privacyConsent) throws Exception {
            registered = true;
            return created();
        }

        @Override
        public PendingRegistrationStore.Authenticated authenticate(
                String login,
                String password) throws Exception {
            authenticatedPending = true;
            return authenticated();
        }

        @Override
        public PendingRegistrationStore.Rotation resend(String actionToken)
                throws Exception {
            return rotation("rick@example.org");
        }

        @Override
        public PendingRegistrationStore.Rotation changeEmail(
                String actionToken,
                String email) throws Exception {
            return rotation(email);
        }

        @Override
        public PendingRegistration cancel(String actionToken) {
            cancelled = true;
            return registration("new@example.org");
        }

        @Override
        public PendingRegistrationService.Activation confirm(
                String confirmationToken) throws Exception {
            return activation();
        }

        @Override
        public boolean containsLogin(String login) {
            return pendingLogin;
        }
    }

    private static final class FakeMail
            implements PendingRegistrationReactor.MailGateway {
        private boolean queued;
        private boolean failQueue;

        @Override
        public void validateRecipient(String address) {
            assertNotNull(address);
        }

        @Override
        public void queueConfirmation(String login,
                                      String address,
                                      String confirmationToken) {
            if (failQueue) {
                throw new IllegalStateException("synthetic mail queue failure");
            }
            queued = true;
        }
    }
}
