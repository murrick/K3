/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.command.CommandIntent;
import org.kanger.command.CommandInvocation;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;

/**
 * Transport-neutral semantic execution boundary for canonical KANGER commands.
 *
 * <p>The processor is introduced incrementally. An intent is moved here only
 * after both interactive adapters can delegate to the same Core operation
 * without bypassing an already-qualified technical boundary. Until then
 * {@link Result#isHandled()} is false and the caller keeps its existing path.</p>
 *
 * <p>The first converged family is explicit user transactions. The processor
 * owns the U-stack transition itself; Console/Server adapters own only
 * presentation, authentication and transport-specific error projection.</p>
 */
public final class CanonicalCommandProcessor {

    public boolean handles(CommandInvocation invocation) {
        if (invocation == null || invocation.isCoreLanguage()) {
            return false;
        }
        CommandIntent intent = invocation.getIntent();
        return intent == CommandIntent.TX_STATUS
                || intent == CommandIntent.TX_START
                || intent == CommandIntent.TX_COMMIT
                || intent == CommandIntent.TX_ROLLBACK;
    }

    public Result execute(CommandInvocation invocation, IUser user) throws Exception {
        if (!handles(invocation)) {
            return Result.unhandled(user == null ? null : user.getCurrentMind());
        }
        if (user == null) {
            throw new IllegalArgumentException("Canonical command user is required");
        }

        IMind mind = user.getCurrentMind();
        if (mind == null) {
            mind = new Mind(user);
            user.setCurrentMind(mind);
        }

        switch (invocation.getIntent()) {
            case TX_STATUS:
                return Result.success(mind, "");

            case TX_START:
                mind = new Mind(mind);
                user.setCurrentMind(mind);
                return Result.success(mind, "New transaction created");

            case TX_COMMIT:
                return commit(user, mind);

            case TX_ROLLBACK:
                return rollback(user, mind);

            default:
                return Result.unhandled(mind);
        }
    }

    private Result commit(IUser user, IMind mind) throws Exception {
        IMind parent = mind.getNext();
        if (parent != null) {
            if (!parent.commit(mind)) {
                return Result.rejected(mind, "Transaction commit rejected");
            }
            user.setCurrentMind(parent);
            return Result.success(parent, "Transaction committed");
        }

        IMind checkpointed = user.checkpoint(mind);
        user.setCurrentMind(checkpointed);
        return Result.success(checkpointed, "Storage checkpoint completed");
    }

    private Result rollback(IUser user, IMind mind) throws Exception {
        IMind parent = mind.getNext();
        if (parent == null) {
            return Result.rejected(mind, "No transactions was created");
        }
        parent.release(mind);
        user.setCurrentMind(parent);
        return Result.success(parent, "Transaction rolled back");
    }

    /** Transport-neutral result of one canonical semantic dispatch. */
    public static final class Result {
        private final boolean handled;
        private final boolean success;
        private final IMind mind;
        private final String description;

        private Result(boolean handled,
                       boolean success,
                       IMind mind,
                       String description) {
            this.handled = handled;
            this.success = success;
            this.mind = mind;
            this.description = description == null ? "" : description;
        }

        private static Result unhandled(IMind mind) {
            return new Result(false, false, mind, "");
        }

        private static Result success(IMind mind, String description) {
            return new Result(true, true, mind, description);
        }

        private static Result rejected(IMind mind, String description) {
            return new Result(true, false, mind, description);
        }

        public boolean isHandled() {
            return handled;
        }

        public boolean isSuccess() {
            return success;
        }

        public IMind getMind() {
            return mind;
        }

        public String getDescription() {
            return description;
        }
    }
}
