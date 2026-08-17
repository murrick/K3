/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.command.CommandIntent;
import org.kanger.command.CommandInvocation;
import org.kanger.enums.Enums;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Transport-neutral semantic execution boundary for canonical KANGER commands.
 *
 * <p>The processor is introduced incrementally. An intent is moved here only
 * after both interactive adapters can delegate to the same Core operation
 * without bypassing an already-qualified technical boundary. Until then
 * {@link Result#isHandled()} is false and the caller keeps its existing path.</p>
 *
 * <p>Converged command families own semantic state/query dispatch here;
 * Console/Server adapters own only presentation, authentication and
 * transport-specific error projection.</p>
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
                || intent == CommandIntent.TX_ROLLBACK
                || intent == CommandIntent.TX_SQUASH
                || intent == CommandIntent.STORAGE_STATUS
                || intent == CommandIntent.STORAGE_USE
                || intent == CommandIntent.STORAGE_CLOSE
                || intent == CommandIntent.STORAGE_DROP
                || intent == CommandIntent.STORAGE_REINDEX;
    }

    public Result execute(CommandInvocation invocation, IUser user) throws Exception {
        return execute(invocation, user, null);
    }

    /**
     * Executes one canonical command with an optional adapter-owned observer.
     *
     * <p>The observer is currently meaningful only for storage reindex, where
     * the storage implementation reports schema progress. The processor owns
     * semantic dispatch but never renders or prints those events.</p>
     */
    public Result execute(CommandInvocation invocation,
                          IUser user,
                          IReactor<String> progress) throws Exception {
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

            case TX_SQUASH:
                if (mind.getTransactionLevel() <= 1) {
                    return Result.success(mind, "Transaction stack already compact");
                }
                mind = UserTransactionStackSnapshot.squash((Mind) mind);
                user.setCurrentMind(mind);
                return Result.success(mind, "Transaction history squashed");

            case STORAGE_STATUS:
                StorageStatus status = storageStatus(mind);
                return Result.success(mind,
                        "Current storage: " + (status.isUsed()
                                ? status.getCurrent() : "none"),
                        status);

            case STORAGE_USE:
                String logicalName = String.valueOf(invocation.getArgument("name"));
                String storageName = logicalName.replace(".", Enums.FILE_SEPARATOR);
                mind = mind.useStorage(storageName);
                user.setCurrentMind(mind);
                StorageStatus used = storageStatus(mind);
                return Result.success(mind,
                        "Current storage: " + used.getCurrent(),
                        used);

            case STORAGE_CLOSE:
                boolean wasUsed = mind.isStorageUsed();
                String previousStorage = wasUsed ? mind.getStorageName() : null;
                mind = user.close(mind);
                user.setCurrentMind(mind);
                StorageStatus closed = storageStatus(mind);
                return Result.success(mind,
                        wasUsed
                                ? "Database " + previousStorage + " closed"
                                : "No database used",
                        closed);

            case STORAGE_DROP:
                String dropLogicalName = String.valueOf(invocation.getArgument("name"));
                String dropStorageName = dropLogicalName.replace(".", Enums.FILE_SEPARATOR);
                mind = mind.removeStorage(dropStorageName);
                user.setCurrentMind(mind);
                StorageStatus dropped = storageStatus(mind);
                return Result.success(mind,
                        "Database " + dropLogicalName + " dropped",
                        dropped);

            case STORAGE_REINDEX:
                String reindexLogicalName = String.valueOf(
                        invocation.getArgument("name"));
                String reindexStorageName = reindexLogicalName.replace(
                        ".", Enums.FILE_SEPARATOR);
                mind = mind.reindexStorage(reindexStorageName, progress);
                user.setCurrentMind(mind);
                return Result.success(mind,
                        "Database reindexed",
                        storageStatus(mind));

            default:
                return Result.unhandled(mind);
        }
    }

    private Result commit(IUser user, IMind mind) throws Exception {
        IMind parent = mind.getNext();
        if (parent != null) {
            if (!((Mind) parent).commitUserTransaction(mind)) {
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

        ContextQualification qualification =
                ((Mind) parent).qualifyCurrentContext(false);
        if (!qualification.isValid()) {
            List<CollisionWitness> collisions = new ArrayList<CollisionWitness>();
            for (ContextQualification.CollisionWitness witness
                    : qualification.getCollisions()) {
                collisions.add(new CollisionWitness(
                        witness.getLeft(), witness.getRight()));
            }
            String reason = collisions.isEmpty()
                    ? "TARGET_CONTEXT_INVALID"
                    : "STORAGE_BASELINE_COLLISION";
            List<ResolutionAction> actions = new ArrayList<ResolutionAction>();
            actions.add(new ResolutionAction(
                    "USE_COMPATIBLE_STORAGE", null,
                    "Use a storage baseline compatible with the rollback target, then retry rollback."));
            actions.add(new ResolutionAction(
                    "TRANSACTION_SQUASH", "transaction squash",
                    "Keep the current effective context and intentionally discard older rollback history."));
            actions.add(new ResolutionAction(
                    "TRANSACTION_COMMIT", "transaction commit",
                    "Keep the current transaction by merging it into the historical parent level."));

            Rejection rejection = new Rejection(
                    "ROLLBACK_REBASE_CONFLICT",
                    reason,
                    parent.getTransactionLevel(),
                    parent.isStorageUsed() ? parent.getStorageName() : null,
                    collisions,
                    actions);
            String description =
                    "Transaction rollback rejected: target context conflicts with current storage baseline";
            if (!collisions.isEmpty()) {
                CollisionWitness first = collisions.get(0);
                description += " (" + first.getLeft() + " <> " + first.getRight() + ")";
            }
            return Result.rejected(mind, description, rejection);
        }
        parent.release(mind);
        user.setCurrentMind(parent);
        return Result.success(parent, "Transaction rolled back");
    }

    private StorageStatus storageStatus(IMind mind) throws Exception {
        List<String> names = new ArrayList<String>();
        for (String name : mind.getStoragesList()) {
            names.add(name);
        }
        Collections.sort(names);
        String current = mind.isStorageUsed() ? mind.getStorageName() : null;
        return new StorageStatus(names, current);
    }

    /** Transport-neutral read model for canonical storage status. */
    public static final class StorageStatus {
        private final List<String> names;
        private final String current;

        private StorageStatus(List<String> names, String current) {
            this.names = Collections.unmodifiableList(
                    new ArrayList<String>(names));
            this.current = current;
        }

        public List<String> getNames() {
            return names;
        }

        public String getCurrent() {
            return current;
        }

        public boolean isUsed() {
            return current != null;
        }
    }

    /** One exact semantic collision witness. */
    public static final class CollisionWitness {
        private final String left;
        private final String right;

        private CollisionWitness(String left, String right) {
            this.left = left == null ? "" : left;
            this.right = right == null ? "" : right;
        }

        public String getLeft() {
            return left;
        }

        public String getRight() {
            return right;
        }
    }

    /** User-controlled resolution offered for a semantic rejection. */
    public static final class ResolutionAction {
        private final String id;
        private final String command;
        private final String description;

        private ResolutionAction(String id, String command, String description) {
            this.id = id;
            this.command = command;
            this.description = description;
        }

        public String getId() {
            return id;
        }

        public String getCommand() {
            return command;
        }

        public String getDescription() {
            return description;
        }
    }

    /** Transport-neutral typed semantic rejection. */
    public static final class Rejection {
        private final String code;
        private final String reason;
        private final int targetLevel;
        private final String storage;
        private final List<CollisionWitness> collisions;
        private final List<ResolutionAction> actions;

        private Rejection(String code,
                          String reason,
                          int targetLevel,
                          String storage,
                          List<CollisionWitness> collisions,
                          List<ResolutionAction> actions) {
            this.code = code;
            this.reason = reason;
            this.targetLevel = targetLevel;
            this.storage = storage;
            this.collisions = Collections.unmodifiableList(
                    new ArrayList<CollisionWitness>(collisions));
            this.actions = Collections.unmodifiableList(
                    new ArrayList<ResolutionAction>(actions));
        }

        public String getCode() {
            return code;
        }

        public String getReason() {
            return reason;
        }

        public int getTargetLevel() {
            return targetLevel;
        }

        public String getStorage() {
            return storage;
        }

        public List<CollisionWitness> getCollisions() {
            return collisions;
        }

        public List<ResolutionAction> getActions() {
            return actions;
        }
    }

    /** Transport-neutral result of one canonical semantic dispatch. */
    public static final class Result {
        private final boolean handled;
        private final boolean success;
        private final IMind mind;
        private final String description;
        private final StorageStatus storageStatus;
        private final Rejection rejection;

        private Result(boolean handled,
                       boolean success,
                       IMind mind,
                       String description,
                       StorageStatus storageStatus,
                       Rejection rejection) {
            this.handled = handled;
            this.success = success;
            this.mind = mind;
            this.description = description == null ? "" : description;
            this.storageStatus = storageStatus;
            this.rejection = rejection;
        }

        private static Result unhandled(IMind mind) {
            return new Result(false, false, mind, "", null, null);
        }

        private static Result success(IMind mind, String description) {
            return new Result(true, true, mind, description, null, null);
        }

        private static Result success(IMind mind,
                                      String description,
                                      StorageStatus storageStatus) {
            return new Result(true, true, mind, description, storageStatus, null);
        }

        private static Result rejected(IMind mind, String description) {
            return new Result(true, false, mind, description, null, null);
        }

        private static Result rejected(IMind mind,
                                       String description,
                                       Rejection rejection) {
            return new Result(true, false, mind, description, null, rejection);
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

        public StorageStatus getStorageStatus() {
            return storageStatus;
        }

        public Rejection getRejection() {
            return rejection;
        }
    }
}
