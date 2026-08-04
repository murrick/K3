/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger;

import org.json.JSONException;
import org.json.JSONObject;
import org.kanger.account.AccountErrorCode;
import org.kanger.account.PendingRegistration;
import org.kanger.account.PendingRegistrationException;
import org.kanger.account.PendingRegistrationService;
import org.kanger.account.PendingRegistrationStore;
import org.kanger.interfaces.IReactor;

/**
 * Public EMAIL_VERIFIED registration boundary. Handled operations never reach
 * the historical UserFactory-based registration and confirmation paths.
 */
final class PendingRegistrationReactor implements IReactor<JSONObject> {

    interface Gateway {
        PendingRegistrationStore.Created register(
                String login,
                String password,
                String email,
                String name,
                String country,
                String city,
                Boolean privacyConsent) throws Exception;

        PendingRegistrationStore.Authenticated authenticate(
                String login,
                String password) throws Exception;

        PendingRegistrationStore.Rotation resend(String actionToken)
                throws Exception;

        PendingRegistrationStore.Rotation changeEmail(
                String actionToken,
                String email) throws Exception;

        PendingRegistration cancel(String actionToken) throws Exception;

        PendingRegistrationService.Activation confirm(String confirmationToken)
                throws Exception;

        boolean containsLogin(String login) throws Exception;
    }

    interface MailGateway {
        void validateRecipient(String address) throws Exception;

        void queueConfirmation(String login,
                               String address,
                               String confirmationToken) throws Exception;
    }

    private final Gateway registrations;
    private final MailGateway mail;
    private final IReactor<JSONObject> delegate;

    PendingRegistrationReactor(IReactor<JSONObject> delegate) throws Exception {
        this(new ServiceGateway(PendingRegistrationService.runtime()),
                new TransportMailGateway(MailTransport.runtime()),
                delegate);
    }

    PendingRegistrationReactor(Gateway registrations,
                               MailGateway mail,
                               IReactor<JSONObject> delegate) {
        if (registrations == null || mail == null || delegate == null) {
            throw new IllegalArgumentException(
                    "pending gateway, mail gateway and delegate must not be null");
        }
        this.registrations = registrations;
        this.mail = mail;
        this.delegate = delegate;
    }

    @Override
    public Object run(JSONObject packet) throws Exception {
        JSONObject parameters = SessionSerializingReactor.parameters(packet);

        try {
            // The browser confirmation link is a root GET and therefore has no
            // application context. It must be consumed before context routing,
            // otherwise it falls through to the historical confirmation path.
            if (has(parameters, "confirm")) {
                return confirm(string(parameters, "confirm"));
            }
            if (!"login".equalsIgnoreCase(context(packet))) {
                return delegate.run(packet);
            }
            if (isNewRegistration(parameters)) {
                return register(parameters);
            }
            if (has(parameters, "pending_action_token")
                    && has(parameters, "change_pending_email")) {
                return changeEmail(parameters);
            }
            if (has(parameters, "pending_action_token")
                    && has(parameters, "cancel_pending")) {
                return cancel(parameters);
            }
            if (has(parameters, "pending_action_token")
                    && has(parameters, "resend")) {
                return resend(parameters);
            }
            if (isPendingAction(parameters)) {
                return error(
                        AccountErrorCode.AUTHENTICATION_FAILED,
                        "A scoped pending action token is required");
            }
            if (isLogin(parameters)
                    && registrations.containsLogin(string(parameters, "login"))) {
                return pendingLogin(parameters);
            }
            return delegate.run(packet);
        } catch (PendingRegistrationException failure) {
            return error(failure.getCode(), failure.getMessage());
        }
    }

    private JSONObject register(JSONObject parameters) throws Exception {
        String email = string(parameters, "email");
        mail.validateRecipient(email);
        Boolean privacy = privacy(parameters);
        if (!Boolean.TRUE.equals(privacy)) {
            return new JSONObject()
                    .put("result", "error")
                    .put("description", "Privacy consent is required");
        }

        PendingRegistrationStore.Created created = registrations.register(
                string(parameters, "register"),
                string(parameters, "password"),
                email,
                string(parameters, "name"),
                string(parameters, "country"),
                string(parameters, "city"),
                privacy);

        try {
            mail.queueConfirmation(
                    created.getRegistration().getLogin(),
                    created.getRegistration().getEmail(),
                    created.getConfirmationToken());
        } catch (Exception unavailable) {
            return pendingMailUnavailable(
                    created.getRegistration(), null, unavailable);
        }
        return pending(created.getRegistration());
    }

    private JSONObject pendingLogin(JSONObject parameters) throws Exception {
        PendingRegistrationStore.Authenticated authenticated =
                registrations.authenticate(
                        string(parameters, "login"),
                        string(parameters, "password"));
        return new JSONObject()
                .put("result", "error")
                .put("code", AccountErrorCode.EMAIL_CONFIRMATION_REQUIRED.code())
                .put("description", "E-mail confirmation is required")
                .put("pending_action_token", authenticated.getActionToken())
                .put("email_hint", emailHint(
                        authenticated.getRegistration().getEmail()))
                .put("can_resend", true)
                .put("can_change_email", true)
                .put("can_cancel", true);
    }

    private JSONObject resend(JSONObject parameters) throws Exception {
        PendingRegistrationStore.Rotation rotation = registrations.resend(
                string(parameters, "pending_action_token"));
        try {
            mail.queueConfirmation(
                    rotation.getRegistration().getLogin(),
                    rotation.getRegistration().getEmail(),
                    rotation.getConfirmationToken());
        } catch (Exception unavailable) {
            return pendingMailUnavailable(
                    rotation.getRegistration(),
                    rotation.getActionToken(),
                    unavailable);
        }
        return pendingActionSuccess(rotation.getRegistration(),
                rotation.getActionToken(), "CONFIRMATION_RESENT");
    }

    private JSONObject changeEmail(JSONObject parameters) throws Exception {
        String email = string(parameters, "email");
        mail.validateRecipient(email);
        PendingRegistrationStore.Rotation rotation = registrations.changeEmail(
                string(parameters, "pending_action_token"), email);
        try {
            mail.queueConfirmation(
                    rotation.getRegistration().getLogin(),
                    rotation.getRegistration().getEmail(),
                    rotation.getConfirmationToken());
        } catch (Exception unavailable) {
            return pendingMailUnavailable(
                    rotation.getRegistration(),
                    rotation.getActionToken(),
                    unavailable);
        }
        return pendingActionSuccess(rotation.getRegistration(),
                rotation.getActionToken(), "PENDING_EMAIL_CHANGED");
    }

    private JSONObject cancel(JSONObject parameters) throws Exception {
        PendingRegistration cancelled = registrations.cancel(
                string(parameters, "pending_action_token"));
        return new JSONObject()
                .put("result", "OK")
                .put("state", "PENDING_REGISTRATION_CANCELLED")
                .put("login", cancelled.getLogin());
    }

    private JSONObject confirm(String confirmationToken) throws Exception {
        PendingRegistrationService.Activation activation =
                registrations.confirm(confirmationToken);
        return new JSONObject()
                .put("result", "OK")
                .put("state", "EMAIL_CONFIRMED")
                .put("user_id", activation.getUserId())
                .put("recovered", activation.isRecovered());
    }

    private static JSONObject pending(PendingRegistration registration) {
        return new JSONObject()
                .put("result", "OK")
                .put("state", "PENDING_CONFIRMATION")
                .put("email_hint", emailHint(registration.getEmail()));
    }

    private static JSONObject pendingActionSuccess(
            PendingRegistration registration,
            String actionToken,
            String state) {
        JSONObject response = pending(registration).put("state", state);
        if (actionToken != null && !actionToken.isEmpty()) {
            response.put("pending_action_token", actionToken);
        }
        return response;
    }

    private static JSONObject pendingMailUnavailable(
            PendingRegistration registration,
            String actionToken,
            Exception failure) {
        JSONObject response = error(
                AccountErrorCode.MAIL_DELIVERY_UNAVAILABLE,
                "Confirmation e-mail could not be queued: " + failure.getMessage())
                .put("state", "PENDING_CONFIRMATION")
                .put("email_hint", emailHint(registration.getEmail()));
        if (actionToken != null && !actionToken.isEmpty()) {
            response.put("pending_action_token", actionToken);
        }
        return response;
    }

    private static JSONObject error(AccountErrorCode code, String description) {
        return new JSONObject()
                .put("result", "error")
                .put("code", code.code())
                .put("description", description == null ? code.code() : description);
    }

    private static boolean isNewRegistration(JSONObject parameters) {
        return has(parameters, "register")
                && has(parameters, "password")
                && string(parameters, "token").isEmpty();
    }

    private static boolean isLogin(JSONObject parameters) {
        return has(parameters, "login")
                && has(parameters, "password")
                && !has(parameters, "register")
                && string(parameters, "token").isEmpty();
    }

    private static boolean isPendingAction(JSONObject parameters) {
        return has(parameters, "resend")
                || has(parameters, "change_pending_email")
                || has(parameters, "cancel_pending");
    }

    private static Boolean privacy(JSONObject parameters) {
        if (!has(parameters, "privacy")) {
            return null;
        }
        Object value = parameters.opt("privacy");
        return value instanceof Boolean
                ? (Boolean) value
                : Boolean.valueOf(String.valueOf(value));
    }

    private static String emailHint(String email) {
        if (email == null || email.isEmpty()) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String visible = local.substring(0, 1);
        return visible + "***" + email.substring(at);
    }

    private static String context(JSONObject packet) {
        if (packet == null) {
            return "";
        }
        JSONObject body = packet.optJSONObject("body");
        if (body != null) {
            String value = body.optString("context", "");
            if (!value.isEmpty()) {
                return value;
            }
        }
        JSONObject query = packet.optJSONObject("query");
        return query == null ? "" : query.optString("context", "");
    }

    private static boolean has(JSONObject parameters, String key) {
        return parameters != null
                && parameters.has(key)
                && !parameters.isNull(key);
    }

    private static String string(JSONObject parameters, String key) {
        if (!has(parameters, key)) {
            return "";
        }
        try {
            return parameters.getString(key).trim();
        } catch (JSONException error) {
            return String.valueOf(parameters.opt(key)).trim();
        }
    }

    private static final class ServiceGateway implements Gateway {
        private final PendingRegistrationService service;

        private ServiceGateway(PendingRegistrationService service) {
            this.service = service;
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
            return service.register(login, password, email,
                    name, country, city, privacyConsent);
        }

        @Override
        public PendingRegistrationStore.Authenticated authenticate(
                String login,
                String password) throws Exception {
            return service.authenticate(login, password);
        }

        @Override
        public PendingRegistrationStore.Rotation resend(String actionToken)
                throws Exception {
            return service.resend(actionToken);
        }

        @Override
        public PendingRegistrationStore.Rotation changeEmail(
                String actionToken,
                String email) throws Exception {
            return service.changeEmail(actionToken, email);
        }

        @Override
        public PendingRegistration cancel(String actionToken) throws Exception {
            return service.cancel(actionToken);
        }

        @Override
        public PendingRegistrationService.Activation confirm(
                String confirmationToken) throws Exception {
            return service.confirm(confirmationToken);
        }

        @Override
        public boolean containsLogin(String login) throws Exception {
            return service.containsLogin(login);
        }
    }

    private static final class TransportMailGateway implements MailGateway {
        private final MailTransport transport;

        private TransportMailGateway(MailTransport transport) {
            this.transport = transport;
        }

        @Override
        public void validateRecipient(String address) throws Exception {
            transport.validateRecipient(address);
        }

        @Override
        public void queueConfirmation(String login,
                                      String address,
                                      String confirmationToken) throws Exception {
            transport.queueConfirmation(login, address, confirmationToken);
        }
    }
}
