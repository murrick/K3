/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.account.PendingRegistration;
import org.kanger.account.PendingRegistrationService;
import org.kanger.account.PendingRegistrationStore;
import org.kanger.interfaces.IReactor;

import java.lang.reflect.Constructor;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PendingConfirmationRootContextTest {

    @Test
    void rootGetConfirmationNeverFallsThroughToLegacyProcessor()
            throws Exception {
        AtomicBoolean delegated = new AtomicBoolean();
        PendingRegistrationReactor reactor = new PendingRegistrationReactor(
                new ConfirmOnlyGateway(),
                new NoMail(),
                new IReactor<JSONObject>() {
                    @Override
                    public Object run(JSONObject packet) {
                        delegated.set(true);
                        return new JSONObject().put("result", "legacy");
                    }
                });
        JSONObject packet = new JSONObject().put("query", new JSONObject()
                .put("parameters", new JSONObject()
                        .put("confirm", "root-confirmation-token")));

        JSONObject response = (JSONObject) reactor.run(packet);

        assertFalse(delegated.get());
        assertEquals("OK", response.getString("result"));
        assertEquals("EMAIL_CONFIRMED", response.getString("state"));
        assertFalse(response.has("token"));
    }

    private static PendingRegistrationService.Activation activation()
            throws Exception {
        Constructor<PendingRegistrationService.Activation> constructor =
                PendingRegistrationService.Activation.class.getDeclaredConstructor(
                        long.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(23L, false);
    }

    private static final class ConfirmOnlyGateway
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
            assertEquals("root-confirmation-token", confirmationToken);
            return activation();
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
