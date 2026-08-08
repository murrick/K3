/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.kanger.command.CommandHelpRenderer;
import org.kanger.command.CommandInvocation;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;

/**
 * Owns canonical intents that cannot be represented faithfully by the legacy
 * QueryProcessor transport.
 *
 * <p>This boundary sits inside {@link WorkspaceStateReactor}, so canonical
 * results receive the same authoritative workspace projection as legacy
 * operations. Existing lifecycle/stop-loss reactors remain below it and stay
 * authoritative for every operation translated by the ingress adapter.</p>
 *
 * <p>3.7.0.2 starts this boundary with registry-derived HELP and explicit typed
 * rejection for the still-unimplemented narrow bindings. Those bindings are
 * added here incrementally rather than approximated through broader historical
 * endpoints.</p>
 */
final class CanonicalCommandRuntimeReactor implements IReactor<JSONObject> {

    private final IReactor<JSONObject> delegate;
    private final CommandHelpRenderer helpRenderer;

    CanonicalCommandRuntimeReactor(IReactor<JSONObject> delegate) {
        this(delegate, new CommandHelpRenderer());
    }

    CanonicalCommandRuntimeReactor(IReactor<JSONObject> delegate,
                                   CommandHelpRenderer helpRenderer) {
        if (delegate == null || helpRenderer == null) {
            throw new IllegalArgumentException(
                    "delegate and helpRenderer must not be null");
        }
        this.delegate = delegate;
        this.helpRenderer = helpRenderer;
    }

    @Override
    public Object run(JSONObject packet) throws Exception {
        if (!CanonicalCommandIngressReactor.CANONICAL_CONTEXT.equalsIgnoreCase(
                CanonicalCommandIngressReactor.context(packet))) {
            return delegate.run(packet);
        }

        CommandInvocation invocation = CanonicalCommandIngressReactor.invocation(packet);
        if (invocation == null || invocation.isCoreLanguage()) {
            return error("canonical_invocation_missing",
                    "Canonical invocation metadata is missing");
        }

        IUser user = requireUser(packet);
        if (user == null) {
            return error("authentication_required", "User not logged in");
        }
        if (user.getCurrentMind() == null) {
            user.setCurrentMind(new Mind(user));
        }

        switch (invocation.getIntent()) {
            case HELP:
                return new JSONObject()
                        .put("result", "OK")
                        .put("description", helpRenderer.render());
            default:
                return new JSONObject()
                        .put("result", "error")
                        .put("code", "canonical_intent_not_implemented")
                        .put("intent", invocation.getIntent().name())
                        .put("description", "Canonical runtime binding is not implemented yet");
        }
    }

    private IUser requireUser(JSONObject packet) {
        JSONObject parameters = SessionSerializingReactor.parameters(packet);
        String token = parameters.optString("token", "");
        if (token.isEmpty()) {
            return null;
        }
        try {
            return UserFactory.getUser(token);
        } catch (Exception rejected) {
            return null;
        }
    }

    private JSONObject error(String code, String description) {
        return new JSONObject()
                .put("result", "error")
                .put("code", code)
                .put("description", description);
    }
}
