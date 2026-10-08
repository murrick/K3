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
import org.kanger.interfaces.internal.IContextFederation;
import org.kanger.interfaces.internal.IData;

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
        return intent == CommandIntent.STATUS
                || intent == CommandIntent.TIMEZONE
                || intent == CommandIntent.TX_STATUS
                || intent == CommandIntent.TX_START
                || intent == CommandIntent.TX_COMMIT
                || intent == CommandIntent.TX_ROLLBACK
                || intent == CommandIntent.TX_SQUASH
                || intent == CommandIntent.STORAGE_STATUS
                || intent == CommandIntent.STORAGE_USE
                || intent == CommandIntent.STORAGE_CLOSE
                || intent == CommandIntent.STORAGE_DROP
                || intent == CommandIntent.STORAGE_REINDEX
                || intent == CommandIntent.CTX_OPINIONS
                || intent == CommandIntent.CTX_VALUES
                || intent == CommandIntent.CTX_SOLVES
                || intent == CommandIntent.CTX_WHEN
                || intent == CommandIntent.CTX_STATUS
                || intent == CommandIntent.CTX_RULES
                || intent == CommandIntent.CTX_PUBLISH
                || intent == CommandIntent.CTX_CONNECT
                || intent == CommandIntent.CTX_DISCONNECT
                || intent == CommandIntent.CTX_SWITCH
                || intent == CommandIntent.CTX_VERSION
                || intent == CommandIntent.CTX_EXPLAIN
                || intent == CommandIntent.CTX_ISOLATED_QUERY;
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
            case STATUS:
                return canonicalStatus(invocation, user, mind);

            case TIMEZONE:
                Object zoneId = invocation.getArgument("zoneId");
                if (zoneId != null && !String.valueOf(zoneId).isEmpty()) {
                    try {
                        user.setTimeZone(String.valueOf(zoneId));
                    } catch (java.time.DateTimeException invalid) {
                        throw new org.kanger.exception.CommandErrorException(
                                "Invalid time zone " + zoneId);
                    }
                }
                return Result.success(mind, timezoneStatus(user));

            case TX_STATUS:
                return Result.successTransaction(mind, "", transactionStatus(mind));

            case TX_START:
                mind = new Mind(mind);
                ((Mind) mind).checkpointUserConnections();
                TransactionCompatibilityRegistry.markValid((Mind) mind);
                user.setCurrentMind(mind);
                return Result.success(mind, "New transaction created");

            case TX_COMMIT:
                return commit(user, mind, (String) invocation.getArgument("description"));

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

            case CTX_STATUS: {
                IContextFederation federation =
                        contextFederation(user, mind);
                return Result.successFederation(
                        mind, "",
                        federation.federationSnapshot(), null);
            }

            case CTX_RULES: {
                IContextFederation federation=contextFederation(user,mind);
                Object locator=invocation.getArgument("locator");
                List<IContextFederation.RuleBlock> rules=federation.inspectRules(mind,
                        locator==null?null:String.valueOf(locator),
                        IContextFederation.RuleSelection.valueOf(String.valueOf(invocation.getArgument("selection"))),
                        (Long)invocation.getArgument("id"));
                return Result.successContextRules(mind,federation.federationSnapshot(),rules);
            }
            case CTX_PUBLISH: {
                IContextFederation federation=contextFederation(user,mind);
                if (!(mind instanceof Mind) || !(((User) user).getData() instanceof org.kanger.interfaces.internal.IRevisionPublication))
                    throw new org.kanger.exception.CommandErrorException("Context publication is unavailable");
                Mind source=(Mind) mind;
                source.requireWritableContext();
                source.requirePublicationQuiescence();
                String description=source.resolveRevisionDescription((String) invocation.getArgument("description"),
                        "Published Context " + mind.getStorageName());
                if (mind.getTransactionLevel() > 0 && !Boolean.TRUE.equals(invocation.getArgument("confirmed"))) {
                    String preview="Publication confirmation required: U" + mind.getTransactionLevel()
                            + " -> U0; description: " + description;
                    Rejection rejection=new Rejection("publication_confirmation_required", preview, 0,
                            mind.getStorageName(), Collections.<CollisionWitness>emptyList(), Collections.<ResolutionAction>emptyList());
                    return new Result(true,false,mind,preview,null,rejection,null,federation.federationSnapshot(),null);
                }
                IMind published=((org.kanger.interfaces.internal.IRevisionPublication) ((User) user).getData()).publishContext(mind,description);
                IContextFederation.Snapshot snapshot=federation.federationSnapshot();
                return Result.successFederation(published,"Context published: " + published.getStorageName()
                        + "@" + snapshot.getSourceRevision() + ": " + description,snapshot,null);
            }

            case CTX_CONNECT: {
                IContextFederation federation =
                        contextFederation(user, mind);
                IContextFederation.Connection connection =
                        federation.connectContext(String.valueOf(
                                invocation.getArgument("locator")));
                return Result.successFederation(
                        mind,
                        "Context connected: "
                                + connection.getLocator()
                                + "@"
                                + connection.getPinnedRevision(),
                        federation.federationSnapshot(), null);
            }

            case CTX_DISCONNECT: {
                IContextFederation federation =
                        contextFederation(user, mind);
                String locator = String.valueOf(
                        invocation.getArgument("locator"));
                federation.disconnectContext(locator);
                return Result.successFederation(
                        mind,
                        "Context disconnected: " + locator,
                        federation.federationSnapshot(), null);
            }

            case CTX_SWITCH: {
                IContextFederation federation =
                        contextFederation(user, mind);
                String locator = String.valueOf(
                        invocation.getArgument("locator"));
                long revision = ((Number) invocation.getArgument(
                        "RevisionId")).longValue();
                IContextFederation.Connection connection =
                        federation.switchContextRevision(
                                locator, revision);
                return Result.successFederation(
                        mind,
                        "Context pin switched: "
                                + connection.getLocator()
                                + "@"
                                + connection.getPinnedRevision(),
                        federation.federationSnapshot(), null);
            }

            case CTX_VERSION: {
                if (!(user instanceof User) || !(((User) user).getData() instanceof IContextFederation))
                    throw new org.kanger.exception.CommandErrorException("Context version history is unavailable");
                IContextFederation federation = (IContextFederation) ((User) user).getData();
                Object rawLocator =
                        invocation.getArgument("locator");
                IContextFederation.VersionHistory versions =
                        federation.versionHistory(
                                rawLocator == null
                                        ? null
                                        : String.valueOf(
                                                rawLocator));
                return Result.successContextVersion(
                        mind,
                        mind.isStorageUsed() ? federation.federationSnapshot() : null,
                        versions);
            }

            case CTX_EXPLAIN: {
                if (!(mind instanceof Mind)) {
                    throw new org.kanger.exception.CommandErrorException(
                            "Context explain requires the canonical Mind runtime");
                }
                IContextFederation.ExplainResult explain =
                        ((Mind) mind).explainQuery(
                                String.valueOf(
                                        invocation.getArgument("query")));
                return Result.successContextExplain(
                        mind,
                        explain);
            }

            case CTX_OPINIONS:
            case CTX_VALUES:
            case CTX_SOLVES:
            case CTX_WHEN: {
                IContextFederation federation = contextFederation(user, mind);
                String locator = (String) invocation.getArgument("locator");
                java.util.Map<String, IContextFederation.Opinion> opinions = invocation.getIntent() == CommandIntent.CTX_OPINIONS
                        ? mind.collectContextOpinions(locator) : mind.getContextOpinions(locator);
                return Result.successContextOpinions(mind, federation.federationSnapshot(), opinions);
            }

            case CTX_ISOLATED_QUERY: {
                IContextFederation federation =
                        contextFederation(user, mind);
                String locator = String.valueOf(
                        invocation.getArgument("locator"));
                IContextFederation.QueryResult query =
                        federation.executeIsolatedQuery(
                                mind,
                                locator,
                                String.valueOf(
                                        invocation.getArgument("query")));
                return Result.successFederation(
                        mind,
                        "Context query: " + locator,
                        federation.federationSnapshot(),
                        query);
            }

            default:
                return Result.unhandled(mind);
        }
    }

    private IContextFederation contextFederation(
            IUser user, IMind mind) throws Exception {
        if (!mind.isStorageUsed()) {
            throw new org.kanger.exception.CommandErrorException(
                    "No storage is open");
        }
        if (!(user instanceof User)) {
            throw new org.kanger.exception.CommandErrorException(
                    "Context federation requires the canonical User runtime");
        }
        IData data = ((User) user).getData();
        if (!(data instanceof IContextFederation)) {
            throw new org.kanger.exception.CommandErrorException(
                    "Current storage does not support Context federation");
        }
        return (IContextFederation) data;
    }

    private Result canonicalStatus(CommandInvocation invocation,
                                   IUser user,
                                   IMind mind) throws Exception {
        Object section = invocation.getArgument("section");
        Object subsection = invocation.getArgument("subsection");
        CanonicalStatusSnapshot snapshot = CanonicalStatusSnapshot.capture(user, mind);
        return Result.success(mind, CanonicalStatusRenderer.render(
                snapshot,
                section == null ? null : String.valueOf(section),
                subsection == null ? null : String.valueOf(subsection)));
    }

    private String timezoneStatus(IUser user) {
        return "Session timezone: " + user.getTimeZone();
    }

    private Result commit(IUser user, IMind mind, String description) throws Exception {
        ((Mind) mind).proposeRevisionDescription(description);
        IMind parent = mind.getNext();
        if (parent != null) {
            if (!((Mind) parent).commitUserTransaction(mind)) {
                return Result.rejected(mind, "Transaction commit rejected");
            }
            TransactionCompatibilityRegistry.markValid((Mind) parent);
            user.setCurrentMind(parent);
            return Result.success(parent, "Transaction committed");
        }

        IMind checkpointed = user.checkpoint(mind);
        TransactionCompatibilityRegistry.markValid((Mind) checkpointed);
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
            TransactionCompatibilityRegistry.markIncompatible(
                    (Mind) parent, qualification);
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
            return Result.rejectedTransaction(mind, description, rejection, null);
        }
        TransactionCompatibilityRegistry.markValid((Mind) parent);
        parent.release(mind);
        user.setCurrentMind(parent);
        return Result.success(parent, "Transaction rolled back");
    }

    private TransactionStatus transactionStatus(IMind mind) throws Exception {
        List<Mind> lineage = UserTransactionStackSnapshot.lineage((Mind) mind);
        List<TransactionLevelStatus> levels = new ArrayList<TransactionLevelStatus>();
        for (Mind level : lineage) {
            TransactionCompatibilityRegistry.Record record =
                    TransactionCompatibilityRegistry.status(level);
            List<CollisionWitness> collisions = new ArrayList<CollisionWitness>();
            for (TransactionCompatibilityRegistry.Witness witness : record.getCollisions()) {
                collisions.add(new CollisionWitness(witness.getLeft(), witness.getRight()));
            }
            levels.add(new TransactionLevelStatus(
                    level.getTransactionLevel(),
                    level.getId(),
                    level == mind,
                    record.getCompatibility().name(),
                    record.getStorage(),
                    collisions));
        }
        return new TransactionStatus(
                mind.getTransactionLevel(),
                mind.isStorageUsed() ? mind.getStorageName() : null,
                levels);
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

    /**
     * Read-only rollback-target compatibility of the complete explicit U-stack
     * against the current persistent U0/storage baseline.
     *
     * <p>This does not claim that an effective visible higher-level context is
     * invalid. A historical level may be incompatible only if it were made
     * current by rollback while the present U0 baseline remained attached.</p>
     */
    public static final class TransactionStatus {
        private final int currentLevel;
        private final String storage;
        private final List<TransactionLevelStatus> levels;

        private TransactionStatus(int currentLevel,
                                  String storage,
                                  List<TransactionLevelStatus> levels) {
            this.currentLevel = currentLevel;
            this.storage = storage;
            this.levels = Collections.unmodifiableList(
                    new ArrayList<TransactionLevelStatus>(levels));
        }

        public int getCurrentLevel() {
            return currentLevel;
        }

        public String getStorage() {
            return storage;
        }

        public List<TransactionLevelStatus> getLevels() {
            return levels;
        }
    }

    /** Read-only rollback-target compatibility of one explicit U-level. */
    public static final class TransactionLevelStatus {
        private final int level;
        private final long id;
        private final boolean current;
        private final String compatibility;
        private final String storage;
        private final List<CollisionWitness> collisions;

        private TransactionLevelStatus(int level,
                                       long id,
                                       boolean current,
                                       String compatibility,
                                       String storage,
                                       List<CollisionWitness> collisions) {
            this.level = level;
            this.id = id;
            this.current = current;
            this.compatibility = compatibility;
            this.storage = storage;
            this.collisions = Collections.unmodifiableList(
                    new ArrayList<CollisionWitness>(collisions));
        }

        public int getLevel() {
            return level;
        }

        public long getId() {
            return id;
        }

        public boolean isCurrent() {
            return current;
        }

        public String getCompatibility() {
            return compatibility;
        }

        public String getStorage() {
            return storage;
        }

        public List<CollisionWitness> getCollisions() {
            return collisions;
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
        private final TransactionStatus transactionStatus;
        private final IContextFederation.Snapshot federationSnapshot;
        private final IContextFederation.QueryResult federationQueryResult;
        private final IContextFederation.VersionHistory contextVersionHistory;
        private final IContextFederation.ExplainResult contextExplainResult;
        private final List<IContextFederation.RuleBlock> contextRules;
        private final java.util.Map<String, IContextFederation.Opinion> contextOpinions;

        private Result(boolean handled,
                       boolean success,
                       IMind mind,
                       String description,
                       StorageStatus storageStatus,
                       Rejection rejection,
                       TransactionStatus transactionStatus) {
            this(handled, success, mind, description,
                    storageStatus, rejection, transactionStatus,
                    null, null, null, null);
        }

        private Result(boolean handled,
                       boolean success,
                       IMind mind,
                       String description,
                       StorageStatus storageStatus,
                       Rejection rejection,
                       TransactionStatus transactionStatus,
                       IContextFederation.Snapshot federationSnapshot,
                       IContextFederation.QueryResult federationQueryResult) {
            this(handled, success, mind, description,
                    storageStatus, rejection, transactionStatus,
                    federationSnapshot, federationQueryResult,
                    null, null);
        }

        private Result(boolean handled,
                       boolean success,
                       IMind mind,
                       String description,
                       StorageStatus storageStatus,
                       Rejection rejection,
                       TransactionStatus transactionStatus,
                       IContextFederation.Snapshot federationSnapshot,
                       IContextFederation.QueryResult federationQueryResult,
                       IContextFederation.VersionHistory contextVersionHistory,
                       IContextFederation.ExplainResult contextExplainResult) {
            this(handled,success,mind,description,storageStatus,rejection,transactionStatus,
                    federationSnapshot,federationQueryResult,contextVersionHistory,contextExplainResult,null);
        }

        private Result(boolean handled,boolean success,IMind mind,String description,StorageStatus storageStatus,
                       Rejection rejection,TransactionStatus transactionStatus,IContextFederation.Snapshot federationSnapshot,
                       IContextFederation.QueryResult federationQueryResult,IContextFederation.VersionHistory contextVersionHistory,
                       IContextFederation.ExplainResult contextExplainResult,List<IContextFederation.RuleBlock> contextRules) {
            this(handled, success, mind, description, storageStatus, rejection, transactionStatus,
                    federationSnapshot, federationQueryResult, contextVersionHistory, contextExplainResult, contextRules, null);
        }

        private Result(boolean handled, boolean success, IMind mind, String description, StorageStatus storageStatus,
                Rejection rejection, TransactionStatus transactionStatus, IContextFederation.Snapshot federationSnapshot,
                IContextFederation.QueryResult federationQueryResult, IContextFederation.VersionHistory contextVersionHistory,
                IContextFederation.ExplainResult contextExplainResult, List<IContextFederation.RuleBlock> contextRules,
                java.util.Map<String, IContextFederation.Opinion> contextOpinions) {
            this.contextOpinions = contextOpinions;
            this.handled = handled;
            this.success = success;
            this.mind = mind;
            this.description = description == null ? "" : description;
            this.storageStatus = storageStatus;
            this.rejection = rejection;
            this.transactionStatus = transactionStatus;
            this.federationSnapshot = federationSnapshot;
            this.federationQueryResult = federationQueryResult;
            this.contextVersionHistory = contextVersionHistory;
            this.contextExplainResult = contextExplainResult;
            this.contextRules=contextRules;
        }

        private static Result unhandled(IMind mind) {
            return new Result(false, false, mind, "", null, null, null);
        }

        private static Result success(IMind mind, String description) {
            return new Result(true, true, mind, description, null, null, null);
        }

        private static Result success(IMind mind,
                                      String description,
                                      StorageStatus storageStatus) {
            return new Result(true, true, mind, description, storageStatus, null, null);
        }

        private static Result successTransaction(IMind mind,
                                                 String description,
                                                 TransactionStatus transactionStatus) {
            return new Result(true, true, mind, description, null, null,
                    transactionStatus);
        }

        private static Result successFederation(
                IMind mind,
                String description,
                IContextFederation.Snapshot federationSnapshot,
                IContextFederation.QueryResult federationQueryResult) {
            return new Result(
                    true, true, mind, description,
                    null, null, null,
                    federationSnapshot, federationQueryResult);
        }

        private static Result successContextExplain(
                IMind mind,
                IContextFederation.ExplainResult contextExplainResult) {
            return new Result(
                    true, true, mind, "",
                    null, null, null,
                    contextExplainResult.getContext(),
                    null, null,
                    contextExplainResult);
        }

        private static Result successContextOpinions(IMind mind, IContextFederation.Snapshot snapshot,
                java.util.Map<String, IContextFederation.Opinion> opinions) {
            return new Result(true, true, mind, "", null, null, null, snapshot, null, null, null, null, opinions);
        }
        public java.util.Map<String, IContextFederation.Opinion> getContextOpinions() { return contextOpinions; }

        private static Result successContextRules(IMind mind,IContextFederation.Snapshot snapshot,List<IContextFederation.RuleBlock> rules) {
            return new Result(true,true,mind,"",null,null,null,snapshot,null,null,null,rules);
        }

        private static Result successContextVersion(
                IMind mind,
                IContextFederation.Snapshot federationSnapshot,
                IContextFederation.VersionHistory contextVersionHistory) {
            return new Result(
                    true, true, mind, "",
                    null, null, null,
                    federationSnapshot, null,
                    contextVersionHistory, null);
        }

        private static Result rejected(IMind mind, String description) {
            return new Result(true, false, mind, description, null, null, null);
        }

        private static Result rejectedTransaction(IMind mind,
                                                  String description,
                                                  Rejection rejection,
                                                  TransactionStatus transactionStatus) {
            return new Result(true, false, mind, description, null, rejection,
                    transactionStatus);
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

        public TransactionStatus getTransactionStatus() {
            return transactionStatus;
        }

        public IContextFederation.Snapshot getFederationSnapshot() {
            return federationSnapshot;
        }

        public IContextFederation.QueryResult getFederationQueryResult() {
            return federationQueryResult;
        }

        public IContextFederation.VersionHistory getContextVersionHistory() {
            return contextVersionHistory;
        }

        public IContextFederation.ExplainResult getContextExplainResult() {
            return contextExplainResult;
        }

        public List<IContextFederation.RuleBlock> getContextRules() { return contextRules; }
    }
}
