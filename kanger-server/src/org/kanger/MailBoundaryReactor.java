/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger;

import org.json.JSONException;
import org.json.JSONObject;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;

/**
 * Removes confirmation-mail side effects from the legacy request processor.
 *
 * <p>New registrations with an e-mail address and resend requests are handled
 * through the bounded mail gateway. The legacy processor therefore never
 * reaches its historical raw-thread mail paths.</p>
 */
final class MailBoundaryReactor implements IReactor<JSONObject> {

    interface MailGateway {
        boolean isEnabled();

        void validateRecipient(String address) throws Exception;

        void queueConfirmation(IUser user, String confirmationToken) throws Exception;
    }

    private final IReactor<JSONObject> delegate;
    private final MailGateway mail;

    MailBoundaryReactor(IReactor<JSONObject> delegate) throws Exception {
        this(delegate, MailTransport.runtime());
    }

    MailBoundaryReactor(IReactor<JSONObject> delegate, MailGateway mail) {
        if (delegate == null || mail == null) {
            throw new IllegalArgumentException("delegate and mail gateway must not be null");
        }
        this.delegate = delegate;
        this.mail = mail;
    }

    @Override
    public Object run(JSONObject packet) throws Exception {
        JSONObject parameters = SessionSerializingReactor.parameters(packet);
        if (!"login".equalsIgnoreCase(context(packet))) {
            return delegate.run(packet);
        }

        if (has(parameters, "resend") && has(parameters, "token")) {
            return resend(parameters);
        }

        String email = string(parameters, "email");
        boolean newRegistration = has(parameters, "register")
                && has(parameters, "password")
                && string(parameters, "token").isEmpty();

        if (!newRegistration || email.isEmpty()) {
            return delegate.run(packet);
        }

        mail.validateRecipient(email);

        parameters.remove("email");
        final Object response;
        try {
            response = delegate.run(packet);
        } finally {
            parameters.put("email", email);
        }

        if (!(response instanceof JSONObject)) {
            return response;
        }

        JSONObject result = (JSONObject) response;
        if (!"OK".equalsIgnoreCase(result.optString("result"))
                || result.optString("token").isEmpty()) {
            return result;
        }

        IUser user = UserFactory.getUser(result.getString("token"));
        user.setProperty("reg.email", email);
        String confirmationToken = UserFactory.getUserToken(user);
        mail.queueConfirmation(user, confirmationToken);
        result.put("description", "Sending e-mail to " + email + " queued");
        return result;
    }

    private JSONObject resend(JSONObject parameters) {
        JSONObject result = new JSONObject();
        try {
            IUser user = UserFactory.getUser(parameters.getString("token"));
            String email = user.getProperty("reg.email", "");
            if (email.isEmpty()) {
                result.put("result", "error");
                result.put("description", "E-mail address not defined");
                return result;
            }

            mail.validateRecipient(email);
            String confirmationToken = UserFactory.getUserToken(user);
            mail.queueConfirmation(user, confirmationToken);
            result.put("result", "OK");
            result.put("description", "Sending e-mail to " + email + " queued");
        } catch (Exception error) {
            result.put("result", "error");
            result.put("description", error.toString());
        }
        return result;
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
        return parameters != null && parameters.has(key) && !parameters.isNull(key);
    }

    private static String string(JSONObject parameters, String key) {
        if (!has(parameters, key)) {
            return "";
        }
        try {
            return parameters.getString(key).trim();
        } catch (JSONException error) {
            return "";
        }
    }
}
