package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.account.AccountErrorCode;
import org.kanger.account.RegistrationPolicy;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailBoundaryReactorTest {

    @Test
    void nonLoginRequestPassesThroughUnchanged() throws Exception {
        final AtomicBoolean called = new AtomicBoolean();
        IReactor<JSONObject> delegate = new IReactor<JSONObject>() {
            @Override
            public Object run(JSONObject packet) {
                called.set(true);
                return new JSONObject().put("result", "OK");
            }
        };

        MailBoundaryReactor reactor = new MailBoundaryReactor(
                delegate, new DisabledGateway());
        JSONObject response = (JSONObject) reactor.run(packet(
                "command", new JSONObject().put("ping", "")));

        assertTrue(called.get());
        assertEquals("OK", response.getString("result"));
    }

    @Test
    void disabledMailRejectsEmailRegistrationBeforeLegacyProcessor() throws Exception {
        final AtomicBoolean called = new AtomicBoolean();
        IReactor<JSONObject> delegate = new IReactor<JSONObject>() {
            @Override
            public Object run(JSONObject packet) {
                called.set(true);
                return new JSONObject().put("result", "OK");
            }
        };

        MailBoundaryReactor reactor = new MailBoundaryReactor(
                delegate, new DisabledGateway());
        JSONObject response = (JSONObject) reactor.run(packet(
                "login", registration("rick@example.org")));

        assertFalse(called.get());
        assertEquals("error", response.getString("result"));
        assertTrue(response.getString("description").contains("disabled"));
    }

    @Test
    void trustedDisabledResendReachesCanonicalAccountBoundary() throws Exception {
        User user = new User();
        user.setId(System.currentTimeMillis() * 1000L
                + (System.nanoTime() & 0x3ffL));
        user.setProperty("reg.email", "rick@example.org");
        String token = UserFactory.addUser(user);
        final AtomicBoolean delegated = new AtomicBoolean();

        try {
            IReactor<JSONObject> delegate = new IReactor<JSONObject>() {
                @Override
                public Object run(JSONObject packet) {
                    delegated.set(true);
                    throw new AssertionError(
                            "disabled resend must not reach legacy processor");
                }
            };
            IReactor<JSONObject> reactor = new CanonicalErrorBoundaryReactor(
                    new SessionSerializingReactor(
                            RegistrationPolicy.TRUSTED,
                            new MailBoundaryReactor(
                                    delegate,
                                    new StrictDisabledGateway())));

            JSONObject response = (JSONObject) reactor.run(packet(
                    "login", new JSONObject()
                            .put("resend", "")
                            .put("token", token)));

            assertFalse(delegated.get());
            assertEquals("error", response.getString("result"), response.toString());
            assertEquals(AccountErrorCode.MAIL_DELIVERY_UNAVAILABLE.code(),
                    response.getString("code"), response.toString());
            assertEquals("E-mail confirmation delivery is unavailable",
                    response.getString("description"));
            assertFalse(response.getString("description")
                    .contains("IllegalStateException"));

            JSONObject diagnostic = response.getJSONObject("error");
            assertEquals(1, diagnostic.getInt("schema"));
            assertEquals("account", diagnostic.getString("domain"));
            assertEquals(AccountErrorCode.MAIL_DELIVERY_UNAVAILABLE.code(),
                    diagnostic.getString("code"));
            assertFalse(diagnostic.getBoolean("retryable"));
            assertEquals("retain", diagnostic.getString("session_action"));
            assertEquals("not_applied", diagnostic.getString("operation_outcome"));
        } finally {
            UserFactory.dropUser(user);
        }
    }

    @Test
    void newEmailRegistrationIsStrippedBeforeLegacyDispatchAndRestoredAfterward() throws Exception {
        final AtomicBoolean emailReachedDelegate = new AtomicBoolean();
        final JSONObject parameters = registration("rick@example.org");
        IReactor<JSONObject> delegate = new IReactor<JSONObject>() {
            @Override
            public Object run(JSONObject packet) {
                emailReachedDelegate.set(
                        SessionSerializingReactor.parameters(packet).has("email"));
                return new JSONObject()
                        .put("result", "error")
                        .put("description", "synthetic validation stop");
            }
        };

        MailBoundaryReactor reactor = new MailBoundaryReactor(
                delegate, new AcceptingGateway());
        JSONObject response = (JSONObject) reactor.run(packet("login", parameters));

        assertFalse(emailReachedDelegate.get());
        assertEquals("rick@example.org", parameters.getString("email"));
        assertEquals("error", response.getString("result"));
    }

    private static JSONObject packet(String context, JSONObject parameters) {
        return new JSONObject().put("body", new JSONObject()
                .put("context", context)
                .put("parameters", parameters));
    }

    private static JSONObject registration(String email) {
        return new JSONObject()
                .put("register", "rick")
                .put("password", "correct horse battery staple")
                .put("token", "")
                .put("email", email);
    }

    private static final class DisabledGateway
            implements MailBoundaryReactor.MailGateway {
        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public void validateRecipient(String address) {
            throw new IllegalStateException("E-mail transport is disabled");
        }

        @Override
        public void queueConfirmation(IUser user, String confirmationToken) {
            throw new AssertionError("disabled gateway must not queue");
        }
    }

    private static final class StrictDisabledGateway
            implements MailBoundaryReactor.MailGateway {
        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public void validateRecipient(String address) {
            throw new AssertionError(
                    "disabled resend must be rejected before recipient validation");
        }

        @Override
        public void queueConfirmation(IUser user, String confirmationToken) {
            throw new AssertionError("disabled gateway must not queue");
        }
    }

    private static final class AcceptingGateway
            implements MailBoundaryReactor.MailGateway {
        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public void validateRecipient(String address) {
            // accepted
        }

        @Override
        public void queueConfirmation(IUser user, String confirmationToken) {
            // not reached because the synthetic delegate returns an error
        }
    }
}
