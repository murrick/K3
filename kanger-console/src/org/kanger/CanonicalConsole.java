/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.command.CommandFormatter;
import org.kanger.command.CommandHelpRenderer;
import org.kanger.command.CommandInvocation;
import org.kanger.command.CommandParseException;
import org.kanger.command.CommandParser;
import org.kanger.command.SortKey;
import org.kanger.command.SourceNamePolicy;
import org.kanger.compiler.Token;
import org.kanger.enums.Enums;
import org.kanger.enums.LogMode;
import org.kanger.exception.CommandErrorException;
import org.kanger.exception.DatabaseErrorException;
import org.kanger.exception.ParseErrorException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.interfaces.IHypothesis;
import org.kanger.interfaces.ILogEntry;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IPredicate;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.IUser;
import org.kanger.interfaces.internal.IContextFederation;
import org.kanger.primitives.Hypothesis;
import org.kanger.stores.HypothesisStore;
import org.kanger.units.Rule;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Canonical Java Console session adapter.
 *
 * <p>The shared {@link CommandParser} is the only grammar authority for ordinary
 * commands. Converged command families delegate semantic state transitions to
 * {@link CanonicalCommandProcessor}; this class owns Console presentation,
 * confirmations, local source files and the two explicitly Console-local
 * conveniences: bare source-list forms and {@code xplain}. Core language lines
 * bypass command dispatch and are executed by the existing Mind API.</p>
 */
public final class CanonicalConsole {

    private static final CommandParser PARSER = new CommandParser();
    private static final CommandFormatter FORMATTER = new CommandFormatter();
    private static final CommandHelpRenderer HELP = new CommandHelpRenderer();
    private static final CanonicalCommandProcessor COMMAND_PROCESSOR =
            new CanonicalCommandProcessor();

    private static String lastComments = "";

    private CanonicalConsole() {
    }

    public static void session(IMind mind, ShutdownHook shutdownHook) throws Exception {
        boolean stop = false;
        String lastQuery = "";
        ConsoleLineInput input = ConsoleLineInput.open(mind.getUser());
        mind = track(shutdownHook, mind);

        try {
            while (!stop) {
                String line = "";
                ParseSourceContext parseSource = new ParseSourceContext();
                try {
                    mind = track(shutdownHook, mind);
                    line = input.readCommand();
                    if (line == null) {
                        continue;
                    }

                    String trimmed = line.trim();
                    if (trimmed.length() > 1
                            && (trimmed.startsWith("//") || trimmed.startsWith("/*"))) {
                        if (!lastComments.isEmpty()) {
                            lastComments += Enums.LINE_SEPARATOR;
                        }
                        lastComments += line;
                        continue;
                    }

                    if ("z".equalsIgnoreCase(trimmed)) {
                        if (lastQuery.isEmpty()) {
                            continue;
                        }
                        line = lastQuery;
                        trimmed = line.trim();
                        System.out.println("\n: " + line);
                    }

                    if (trimmed.isEmpty()) {
                        Console.showCopyrigt();
                        continue;
                    }

                    if (isBareSourceList(trimmed)) {
                        showSourceNames(mind);
                        continue;
                    }

                    if (isXplain(trimmed)) {
                        processXplain(trimmed, mind, input);
                        continue;
                    }

                    if (isHiddenTestCommand(trimmed)) {
                        runHiddenTestCommand(trimmed, mind);
                        continue;
                    }

                    CommandInvocation invocation = PARSER.parse(line);
                    if (invocation.isCoreLanguage()) {
                        if (trimmed.charAt(0) == Enums.SUC) {
                            lastQuery = line;
                        }
                        processCore(line, mind, parseSource);
                        continue;
                    }

                    DispatchResult result = dispatch(
                            invocation, mind, input, shutdownHook, parseSource);
                    mind = track(shutdownHook, result.mind);
                    stop = result.stop;
                } catch (CommandParseException ex) {
                    System.err.printf("ERROR: %s: %s%n", ex.getReason(), ex.getMessage());
                } catch (ParseErrorException ex) {
                    ConsoleParseErrorRenderer.show(ex, parseSource.sourceOr(line));
                } catch (CommandErrorException ex) {
                    System.err.println(ex.toString());
                } catch (DatabaseErrorException ex) {
                    System.err.println(ex.toString());
                } catch (StorageLifecycleException ex) {
                    String action = ex.getRequiredAction();
                    System.err.printf("ERROR: %s%s: %s%n",
                            ex.getCode(),
                            action == null || action.isEmpty() ? "" : " [" + action + "]",
                            ex.toString());
                    for (ContextQualification.CollisionWitness witness
                            : ex.getCollisions()) {
                        System.err.printf("  collision: %s <> %s%n",
                                witness.getLeft(), witness.getRight());
                    }
                } catch (RuntimeErrorException ex) {
                    System.err.println(ex.toString());
                } catch (Exception ex) {
                    System.err.println(new Date());
                    ex.printStackTrace(System.err);
                } finally {
                    IMind recovered = mind.getUser().getCurrentMind();
                    if (recovered != null && recovered != mind) {
                        mind = track(shutdownHook, recovered);
                    }
                }
            }
        } finally {
            try {
                input.close();
            } catch (Exception ex) {
                System.err.println(new Date());
                ex.printStackTrace(System.err);
            }
        }

        try {
            mind = track(shutdownHook, mind.closeStorage());
        } catch (Exception ex) {
            System.err.println(new Date());
            ex.printStackTrace(System.err);
        }
        System.out.println("KANGER III Session closed");
    }

    private static DispatchResult dispatch(CommandInvocation invocation,
                                           IMind mind,
                                           ConsoleLineInput input,
                                           ShutdownHook shutdownHook) throws Exception {
        return dispatch(invocation, mind, input, shutdownHook, null);
    }

    private static DispatchResult dispatch(CommandInvocation invocation,
                                           IMind mind,
                                           ConsoleLineInput input,
                                           ShutdownHook shutdownHook,
                                           ParseSourceContext parseSource) throws Exception {
        String canonical = FORMATTER.format(invocation);
        switch (invocation.getIntent()) {
            case RULE_STATUS:
            case RULE_SHOW:
            case RULE_ALL:
            case RULE_PRODUCED:
            case RULE_LEVEL:
            case RULE_TREE:
                if((invocation.getIntent()==org.kanger.command.CommandIntent.RULE_STATUS
                        || invocation.getIntent()==org.kanger.command.CommandIntent.RULE_ALL) && mind.isStorageUsed()
                        && mind.getUser() instanceof org.kanger.User
                        && ((org.kanger.User)mind.getUser()).getData() instanceof IContextFederation)
                    showFederation(((IContextFederation)((org.kanger.User)mind.getUser()).getData()).federationSnapshot(),null);
                Console.showRules(mind, canonical);
                return same(mind);
            case RULE_COMMENT_GET:
                showRuleComment(mind, number(invocation, "id"));
                return same(mind);
            case RULE_COMMENT_SET:
                setRuleComment(mind, number(invocation, "id"),
                        String.valueOf(invocation.getArgument("text")));
                return same(mind);

            case FUNCTIONS:
            case FUNCTION_SHOW:
            case FUNCTION_SOURCE:
                Console.showFunctions(mind, canonical);
                return same(mind);

            case BASE_STATUS:
                Console.showBase(mind, "base");
                return same(mind);
            case BASE_PREDICATES:
                Console.showBase(mind, "base predicates");
                return same(mind);
            case BASE_PREDICATE:
                Console.showBase(mind, "base " + invocation.getArgument("predicate"));
                return same(mind);
            case BASE_TREE:
                showBaseTree(mind, number(invocation, "statementId"));
                return same(mind);

            case VALUES:
                showValues(mind, null);
                return same(mind);
            case VALUES_ORDER:
                showValues(mind, sortKeys(invocation));
                return same(mind);

            case SOLUTIONS:
                showSolutions(mind, -1, false);
                return same(mind);
            case SOLUTION_SHOW:
                showSolutions(mind, number(invocation, "id"), false);
                return same(mind);
            case SOLUTION_TREE:
                showSolutions(mind, number(invocation, "id"), true);
                return same(mind);

            case WHEN_STATUS:
                showWhen(mind);
                return same(mind);
            case WHEN_ACCEPT:
                acceptWhen(mind, number(invocation, "index"), parseSource);
                return same(mind);

            case STATUS:
            case TIMEZONE:
                CanonicalCommandProcessor.Result status =
                        COMMAND_PROCESSOR.execute(invocation, mind.getUser());
                if (!status.isHandled()) {
                    throw new CommandErrorException("Unsupported canonical intent "
                            + invocation.getIntent());
                }
                mind = track(shutdownHook, status.getMind());
                if (!status.getDescription().isEmpty()) {
                    System.out.println(status.getDescription());
                }
                return same(mind);

            case TX_STATUS:
            case TX_START:
            case TX_COMMIT:
            case TX_ROLLBACK:
            case TX_SQUASH:
                if (invocation.getIntent() == org.kanger.command.CommandIntent.TX_COMMIT
                        && mind.getTransactionLevel() == 1 && mind instanceof Mind
                        && ((Mind) mind).isStorageUsed()
                        && ((User) mind.getUser()).getData() instanceof org.kanger.interfaces.internal.IRevisionPublication) {
                    invocation = publicationDescription(invocation, (Mind) mind, input, "Committed transaction");
                }
                CanonicalCommandProcessor.Result transaction =
                        COMMAND_PROCESSOR.execute(invocation, mind.getUser());
                if (!transaction.isHandled()) {
                    throw new CommandErrorException("Unsupported canonical intent "
                            + invocation.getIntent());
                }
                mind = track(shutdownHook, transaction.getMind());
                if (invocation.getIntent() == org.kanger.command.CommandIntent.TX_COMMIT
                        || invocation.getIntent() == org.kanger.command.CommandIntent.TX_ROLLBACK
                        || invocation.getIntent() == org.kanger.command.CommandIntent.TX_SQUASH) {
                    if (!transaction.getDescription().isEmpty()) {
                        System.out.println((transaction.isSuccess() ? "SUCCESS: " : "WARNING: ")
                                + transaction.getDescription());
                    }
                    if (!transaction.isSuccess() && transaction.getRejection() != null) {
                        showRejection(transaction.getRejection());
                    }
                }
                showTransaction(transaction.getTransactionStatus(), mind);
                return same(mind);

            case SOURCE_GET:
                mind = loadSource(mind,
                        String.valueOf(invocation.getArgument("source")), parseSource);
                return same(track(shutdownHook, mind));
            case SOURCE_PUT:
                saveSource(mind, String.valueOf(invocation.getArgument("source")), input);
                return same(mind);
            case SOURCE_DELETE:
                deleteSource(mind, String.valueOf(invocation.getArgument("source")), input);
                return same(mind);

            case STORAGE_STATUS:
            case STORAGE_USE:
            case STORAGE_CLOSE:
            case STORAGE_DROP:
            case STORAGE_REINDEX:
                if (invocation.getIntent() == org.kanger.command.CommandIntent.STORAGE_DROP
                        && !confirm(input, "Drop storage "
                        + String.valueOf(invocation.getArgument("name")) + "?")) {
                    return same(mind);
                }
                IReactor<String> progress = null;
                if (invocation.getIntent()
                        == org.kanger.command.CommandIntent.STORAGE_REINDEX) {
                    progress = new IReactor<String>() {
                        @Override
                        public Object run(String item) {
                            System.out.println("Processing " + item + "...");
                            return null;
                        }
                    };
                }
                CanonicalCommandProcessor.Result storage =
                        COMMAND_PROCESSOR.execute(invocation, mind.getUser(),
                                progress);
                if (!storage.isHandled() || storage.getStorageStatus() == null) {
                    throw new CommandErrorException("Unsupported canonical intent "
                            + invocation.getIntent());
                }
                mind = track(shutdownHook, storage.getMind());
                if (invocation.getIntent() == org.kanger.command.CommandIntent.STORAGE_CLOSE
                        || invocation.getIntent() == org.kanger.command.CommandIntent.STORAGE_DROP
                        || invocation.getIntent()
                        == org.kanger.command.CommandIntent.STORAGE_REINDEX) {
                    if (!storage.getDescription().isEmpty()) {
                        System.out.println(storage.getDescription());
                    }
                } else {
                    showStorage(storage.getStorageStatus());
                }
                return same(mind);

            case CTX_OPINIONS:
            case CTX_VALUES:
            case CTX_SOLVES:
            case CTX_WHEN:
            case CTX_STATUS:
            case CTX_RULES:
            case CTX_PUBLISH:
            case CTX_CONNECT:
            case CTX_DISCONNECT:
            case CTX_SWITCH:
            case CTX_VERSION:
            case CTX_EXPLAIN:
            case CTX_ISOLATED_QUERY:
                if (invocation.getIntent() == org.kanger.command.CommandIntent.CTX_PUBLISH) {
                    ((Mind) mind).requireWritableContext();
                    ((Mind) mind).requirePublicationQuiescence();
                    invocation = publicationDescription(invocation, (Mind) mind, input,
                            "Published Context " + mind.getStorageName());
                    if (mind.getTransactionLevel() > 0) {
                        showFederation(((org.kanger.interfaces.internal.IContextFederation)
                                ((User) mind.getUser()).getData()).federationSnapshot(), null);
                        if (!confirm(input, "Publish U" + mind.getTransactionLevel() + " -> U0: "
                                + invocation.getArgument("description") + "?")) {
                            System.out.println("Publication cancelled");
                            return same(mind);
                        }
                        java.util.Map<String,Object> arguments = new java.util.LinkedHashMap<>(invocation.getArguments());
                        arguments.put("confirmed", Boolean.TRUE);
                        invocation = CommandInvocation.command(invocation.getIntent(), arguments, invocation.getRaw());
                    }
                }
                CanonicalCommandProcessor.Result federation =
                        COMMAND_PROCESSOR.execute(invocation, mind.getUser());
                if (!federation.isHandled()
                        || (federation.getFederationSnapshot() == null
                                && federation.getContextVersionHistory() == null)) {
                    throw new CommandErrorException(
                            "Unsupported canonical intent "
                                    + invocation.getIntent());
                }
                mind = track(shutdownHook, federation.getMind());
                if (!federation.getDescription().isEmpty()
                        && invocation.getIntent()
                                != org.kanger.command.CommandIntent.CTX_STATUS) {
                    System.out.println(federation.getDescription());
                }
                if (federation.getContextOpinions() != null) {
                    showContextOpinions(federation.getContextOpinions(), invocation.getIntent());
                } else if (invocation.getIntent()
                        == org.kanger.command.CommandIntent.CTX_RULES) {
                    showContextRules(federation.getContextRules(),String.valueOf(invocation.getArgument("selection")));
                } else if (invocation.getIntent()
                        == org.kanger.command.CommandIntent.CTX_VERSION) {
                    showContextVersion(
                            federation.getContextVersionHistory());
                } else if (invocation.getIntent()
                        == org.kanger.command.CommandIntent.CTX_EXPLAIN) {
                    showContextExplain(
                            federation.getContextExplainResult());
                } else if (invocation.getIntent()
                        == org.kanger.command.CommandIntent.CTX_ISOLATED_QUERY) {
                    showIsolatedContextQuery(
                            federation.getFederationSnapshot(),
                            federation.getFederationQueryResult(),
                            String.valueOf(
                                    invocation.getArgument("locator")));
                } else {
                    showFederation(
                            federation.getFederationSnapshot(),
                            federation.getFederationQueryResult());
                }
                return same(mind);

            case ERASE:
                mind = erase(mind, input);
                return same(track(shutdownHook, mind));
            case HELP:
                showHelp();
                return same(mind);
            case QUIT:
                return new DispatchResult(mind, confirmQuit(mind, input));
            default:
                throw new CommandErrorException("Unsupported canonical intent "
                        + invocation.getIntent());
        }
    }

    private static void showRejection(
            CanonicalCommandProcessor.Rejection rejection) {
        System.out.printf("REJECTED: %s [%s]%n",
                rejection.getCode(), rejection.getReason());
        for (CanonicalCommandProcessor.CollisionWitness witness
                : rejection.getCollisions()) {
            System.out.printf("  collision: %s <> %s%n",
                    witness.getLeft(), witness.getRight());
        }
        System.out.println("  possible actions:");
        for (CanonicalCommandProcessor.ResolutionAction action
                : rejection.getActions()) {
            System.out.printf("  - %s%s: %s%n",
                    action.getId(),
                    action.getCommand() == null ? "" : " [" + action.getCommand() + "]",
                    action.getDescription());
        }
    }

    private static void processCore(String line, IMind mind) throws Exception {
        processCore(line, mind, null);
    }

    private static void processCore(String line,
                                    IMind mind,
                                    ParseSourceContext parseSource) throws Exception {
        setParseSource(parseSource, line);
        String trimmed = line.trim();
        if (trimmed.charAt(0) == Enums.FOO) {
            mind.compile(line);
            if (!lastComments.isEmpty() && mind.getAcceptedRule() != null) {
                mind.getAcceptedRule().setComment(lastComments);
                lastComments = "";
            }
            if ((mind.getDebugLevel() & Enums.DEBUG_OPTION_RTLOGS) == 0) {
                ILogEntry log = mind.getCurrentLogRecord(LogMode.ANALYZER);
                if (log != null) {
                    System.out.println(log.getRecord());
                }
            }
            return;
        }

        /*
         * Bare '?' is a Core program check, not an unterminated query line.
         * Mind.query("?") owns its own transactional queryCheck() and commits
         * the regenerated program state on success. Running it inside the
         * ordinary query overlay and releasing that overlay would discard the
         * generated-rule rebuild that this operator explicitly requests.
         */
        if ("?".equals(trimmed)) {
            setParseSource(parseSource, "?");
            Boolean response = mind.query("?");
            if ((mind.getDebugLevel() & Enums.DEBUG_OPTION_RTLOGS) == 0) {
                ILogEntry log = mind.getCurrentLogRecord(LogMode.ANALYZER);
                if (log != null) {
                    System.out.println(log.getRecord());
                }
                if (response != null) {
                    Console.showLog(mind, LogMode.SOLVES, null, null);
                    Console.showLog(mind, LogMode.VALUES, null, null);
                }
            }
            return;
        }

        mind.clearLog();
        ((HypothesisStore) mind.getHypothesis()).clear();
        Token token = null;
        Mind overlay = new Mind(mind);
        boolean settlementStarted = false;
        boolean query = false;
        Boolean response = null;
        try {
            while ((token = Tools.extractLine(line, token)) != null) {
                String operator = token.getToken(line);
                if (operator.charAt(0) == '?') {
                    query = true;
                }
                setParseSource(parseSource, operator);
                response = overlay.query(operator);
                if (!lastComments.isEmpty() && overlay.getAcceptedRule() != null) {
                    overlay.getAcceptedRule().setComment(lastComments);
                    lastComments = "";
                }
            }
            if (!query) {
                settlementStarted = true;
                mind.commit(overlay);
            } else {
                List<IHypothesis> hypotheses = new ArrayList<IHypothesis>();
                if (response == null) {
                    for (IHypothesis hypothesis : overlay.getHypothesis()) {
                        hypotheses.add(hypothesis);
                    }
                }
                settlementStarted = true;
                mind.release(overlay);
                if (response == null) {
                    ((HypothesisStore) mind.getHypothesis()).clear();
                    for (IHypothesis hypothesis : hypotheses) {
                        ((HypothesisStore) mind.getHypothesis()).add(hypothesis);
                    }
                }
            }
        } finally {
            if (!settlementStarted) {
                mind.release(overlay);
            }
        }
        if ((mind.getDebugLevel() & Enums.DEBUG_OPTION_RTLOGS) == 0) {
            ILogEntry log = mind.getCurrentLogRecord(LogMode.ANALYZER);
            if (log != null) {
                System.out.println(log.getRecord());
            }
            if (mind.hasOtherContextOpinions()) System.out.println("Other opinions may be available (ctx opinions)");
            if (response != null) {
                Console.showLog(mind, LogMode.SOLVES, null, null);
                Console.showLog(mind, LogMode.VALUES, null, null);
            }
            if (response == null && line.trim().charAt(0) == Enums.SUC) {
                showWhen(mind);
            }
        }
    }

    private static void showRuleComment(IMind mind, long id) throws Exception {
        IRule rule = mind.getRules().get(id);
        if (rule == null) {
            throw new CommandErrorException("Rule not found " + id);
        }
        System.out.printf("Rule %03d comment:%n%s%n", id, rule.getComment());
    }

    private static void setRuleComment(IMind mind, long id, String text) throws Exception {
        ((Mind)mind).requireWritableContext();
        IRule rule = mind.getRules().get(id);
        if (rule == null) {
            throw new CommandErrorException("Rule not found " + id);
        }
        rule.setComment(text == null ? "" : text);
        showRuleComment(mind, id);
    }

    private static void showBaseTree(IMind mind, long id) throws Exception {
        IRule selected = null;
        for (IRule rule : mind.getRules()) {
            if (rule.getId() == id && !rule.isDeleted(mind) && rule.isStored()) {
                selected = rule;
                break;
            }
        }
        if (selected == null) {
            throw new CommandErrorException("Base statement not found " + id);
        }
        System.out.printf("Statement %03d: %s%n", selected.getId(), selected.toString());
        if (selected.getCauses().isEmpty()) {
            System.out.println("Have not solutions variants");
        } else {
            Console.showCauses(mind, selected.getCauses(), -1);
        }
    }

    private static void showValues(IMind mind, List<SortKey> keys) throws Exception {
        if (keys != null && !keys.isEmpty() && mind.getValues().iterator().hasNext()) {
            for (SortKey key : keys) {
                boolean found = false;
                for (Map<String, ITerm> row : mind.getValues()) {
                    if (row.containsKey(key.getField())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    throw new CommandErrorException("Values field not found " + key.getField());
                }
            }
        }

        ValuesOrder[] coreOrder;
        if (keys == null || keys.isEmpty()) {
            coreOrder = new ValuesOrder[0];
        } else {
            coreOrder = new ValuesOrder[keys.size()];
            for (int i = 0; i < keys.size(); ++i) {
                SortKey key = keys.get(i);
                coreOrder[i] = key.getDirection() == SortKey.Direction.DESC
                        ? ValuesOrder.desc(key.getField())
                        : ValuesOrder.asc(key.getField());
            }
        }
        List<Map<String, ITerm>> rows = mind.getValues(coreOrder);

        if (rows.isEmpty()) {
            System.out.println("No values found");
            return;
        }
        int index = 0;
        for (Map<String, ITerm> row : rows) {
            System.out.printf("%03d:", index++);
            for (Map.Entry<String, ITerm> entry : row.entrySet()) {
                Object value = entry.getValue() == null ? null : entry.getValue().getValue();
                System.out.printf("\t%s=%s", entry.getKey(), value);
            }
            System.out.println();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<SortKey> sortKeys(CommandInvocation invocation) {
        return (List<SortKey>) invocation.getArgument("keys");
    }

    private static void showSolutions(IMind mind, long id, boolean tree) throws Exception {
        if (mind.getSolutions().isEmpty()) {
            System.out.println("No solutions found");
            return;
        }
        boolean found = false;
        for (IRule rule : mind.getSolutions()) {
            if (id < 0 || rule.getId() == id) {
                found = true;
                System.out.printf("Solution %03d: %s%n", rule.getId(), rule.toString());
                if (tree && !rule.getCauses().isEmpty()) {
                    Console.showCauses(mind, rule.getCauses(), 0);
                    System.out.println();
                }
                if (id >= 0) {
                    break;
                }
            }
        }
        if (!found) {
            System.out.println("No solutions selected");
        }
    }

    private static void showWhen(IMind mind) throws Exception {
        if (mind.getHypothesis().isEmpty()) {
            System.out.println("No hypothesis found");
            return;
        }
        System.out.print("Optimizing hypothesis list...");
        mind.optimizeHypothesis();
        System.out.printf("%nHypothesis list (%d):%n", mind.getHypothesis().size());
        for (int i = 0; i < mind.getHypothesis().size(); ++i) {
            System.out.printf("\t%03d:\t%s%n", i,
                    ((Hypothesis) mind.getHypothesis().get(i)).toAssertionString(mind));
        }
    }

    private static void acceptWhen(IMind mind, long index) throws Exception {
        acceptWhen(mind, index, null);
    }

    private static void acceptWhen(IMind mind,
                                   long index,
                                   ParseSourceContext parseSource) throws Exception {
        mind.optimizeHypothesis();
        if (index < 0 || index >= mind.getHypothesis().size()) {
            throw new CommandErrorException("Hypothesis index out of range " + index);
        }
        IHypothesis selected = mind.getHypothesis().get(index);
        String source = ((Hypothesis) selected).toAssertionString(mind);
        String statement = String.format("%s;",
                source.replaceAll(String.format("%c", Enums.EOLN), ""));
        System.out.println("Statement: " + statement);
        setParseSource(parseSource, statement);
        Boolean response = ((Mind) mind).queryAccept(statement, null, true);
        if (response != null && (mind.getDebugLevel() & Enums.DEBUG_OPTION_RTLOGS) == 0) {
            Console.showLog(mind, LogMode.SOLVES, null, null);
            Console.showLog(mind, LogMode.VALUES, null, null);
            ILogEntry log = mind.getCurrentLogRecord(LogMode.ANALYZER);
            if (log != null) {
                System.out.println(log.getRecord());
            }
        }
    }

    private static void showTransaction(
            CanonicalCommandProcessor.TransactionStatus status,
            IMind mind) {
        if (status == null) {
            System.out.printf("Transaction level %d (%d)%n",
                    mind.getTransactionLevel(), mind.getId());
            return;
        }
        System.out.printf("Transaction stack: U%d current, storage %s%n",
                status.getCurrentLevel(),
                status.getStorage() == null ? "none" : status.getStorage());
        for (CanonicalCommandProcessor.TransactionLevelStatus level
                : status.getLevels()) {
            System.out.printf("  U%d  %-12s  id=%d%s%n",
                    level.getLevel(),
                    level.getCompatibility(),
                    level.getId(),
                    level.isCurrent() ? "  [current]" : "");
            for (CanonicalCommandProcessor.CollisionWitness witness
                    : level.getCollisions()) {
                System.out.printf("      collision: %s <> %s%n",
                        witness.getLeft(), witness.getRight());
            }
        }
    }

    private static void showSourceNames(IMind mind) {
        File[] files = new File(mind.getUser().getSourceDir()).listFiles();
        List<String> names = new ArrayList<String>();
        if (files != null) {
            for (File file : files) {
                if (!file.isDirectory()
                        && SourceNamePolicy.isCanonicalSourceFileName(file.getName())) {
                    names.add(file.getName());
                }
            }
        }
        Collections.sort(names);
        if (names.isEmpty()) {
            System.out.println("No source files available");
            return;
        }
        System.out.println("Source files available:");
        for (String name : names) {
            System.out.println("\t" + name);
        }
    }

    private static File sourceFile(IMind mind, String name) throws Exception {
        File root = new File(mind.getUser().getSourceDir()).getCanonicalFile();
        File file = new File(root, name).getCanonicalFile();
        if (!root.equals(file.getParentFile())) {
            throw new CommandErrorException("Invalid source name " + name);
        }
        return file;
    }

    private static IMind loadSource(IMind mind, String name) throws Exception {
        return loadSource(mind, name, null);
    }

    private static IMind loadSource(IMind mind,
                                    String name,
                                    ParseSourceContext parseSource) throws Exception {
        File file = sourceFile(mind, name);
        if (!file.isFile()) {
            System.out.println("WARNING: File " + name + " not found");
            return mind;
        }
        if (file.length() == 0L) {
            System.out.println("WARNING: File " + name + " is empty");
            return mind;
        }

        String text = new String(Files.readAllBytes(file.toPath()), "UTF-8");
        setParseSource(parseSource, text);
        boolean accepted = mind.compile(text);
        if ((mind.getDebugLevel() & Enums.DEBUG_OPTION_RTLOGS) == 0) {
            ILogEntry log = mind.getCurrentLogRecord(LogMode.ANALYZER);
            if (log != null) {
                System.out.println(log.getRecord());
            }
        }
        if (accepted) {
            System.out.println("File " + file.getName() + " loaded");
        } else {
            showCompileCollisions(mind);
            System.out.println("Use xplain for analysis");
        }
        return mind;
    }

    private static void showCompileCollisions(IMind mind) {
        if (!(mind instanceof Mind)) {
            return;
        }
        ContextQualification qualification =
                ((Mind) mind).getLastCompileQualification();
        if (qualification == null || qualification.isValid()) {
            return;
        }
        for (ContextQualification.CollisionWitness witness
                : qualification.getCollisions()) {
            System.out.printf("  collision: %s <> %s%n",
                    witness.getLeft(), witness.getRight());
        }
    }

    private static CommandInvocation publicationDescription(CommandInvocation invocation, Mind mind,
            ConsoleLineInput input, String fallback) throws Exception {
        String explicit=(String) invocation.getArgument("description");
        String description=mind.resolveRevisionDescription(explicit,fallback);
        if (explicit == null || explicit.isEmpty()) {
            String entered=input.readAuxiliary("Revision description [" + description + "] (Enter to use): ");
            if (entered != null && !entered.trim().isEmpty()) description=mind.resolveRevisionDescription(entered,fallback);
        }
        java.util.Map<String,Object> arguments=new java.util.LinkedHashMap<>(invocation.getArguments());
        arguments.put("description", description);
        return CommandInvocation.command(invocation.getIntent(), arguments, invocation.getRaw());
    }

    private static void saveSource(IMind mind, String name, ConsoleLineInput input) throws Exception {
        File file = sourceFile(mind, name);
        if (file.exists() && !confirm(input, "Overwrite source file " + name + "?")) {
            return;
        }
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
            writer.write(SourceContextMaterializer.materializeCurrentLevel(mind));
        }
        System.out.println("Source file " + name + " saved.");
    }

    private static void deleteSource(IMind mind, String name, ConsoleLineInput input) throws Exception {
        File file = sourceFile(mind, name);
        if (!file.exists()) {
            System.out.println("Source file " + name + " not found");
            return;
        }
        if (!confirm(input, "Delete source file " + name + "?")) {
            return;
        }
        if (!file.delete()) {
            throw new CommandErrorException("Cannot delete source file " + name);
        }
        System.out.println("Source file " + name + " deleted.");
    }

    private static void showContextExplain(
            IContextFederation.ExplainResult explain) {
        if (explain == null) {
            return;
        }
        IContextFederation.Snapshot snapshot =
                explain.getContext();

        System.out.printf(
                "Context %s@%d [explain]%n",
                snapshot.getSourceLocator(),
                snapshot.getSourceRevision());
        if (snapshot.getConnections().isEmpty()) {
            System.out.println("Pins: none");
        } else {
            System.out.println("Pins:");
            for (IContextFederation.Connection connection
                    : snapshot.getConnections()) {
                System.out.printf(
                        "  %s@%d%n",
                        connection.getLocator(),
                        connection.getPinnedRevision());
            }
        }

        System.out.println(
                "Local X: " + explain.getLocalTruth());

        for (IContextFederation.ExplainPass pass
                : explain.getPasses()) {
            IContextFederation.QueryResult continuation =
                    pass.getContinuation();
            System.out.println(
                    pass.getPolarity()
                            == IContextFederation.ExplainPolarity.FALSE_PASS
                            ? "FALSE pass:"
                            : "TRUE pass:");

            for (IContextFederation.FrontierObservation observation
                    : continuation.getObservations()) {
                System.out.printf(
                        "  wave %d  %s  => %s%n",
                        observation.getWave(),
                        observation.getQuerySource(),
                        observation.getTruth());
                showRevisionSources(
                        "TRUE",
                        observation.getTrueSources(),
                        snapshot);
                showRevisionSources(
                        "FALSE",
                        observation.getFalseSources(),
                        snapshot);
                showRevisionSources(
                        "UNKNOWN",
                        observation.getUnknownSources(),
                        snapshot);
            }

            for (IContextFederation.CausalStep step : continuation.getCausalSteps()) {
                System.out.printf("  causal wave %d  %s@%d  %s => %s%n", step.getWave(),
                        contextLocator(snapshot, step.getTarget().getContextId()),
                        step.getTarget().getRevision(), step.getQuery(), step.getTruth());
                for (IContextFederation.EvidenceInjection fact : step.getSuppliedEvidence()) {
                    System.out.println("    supplied: " + fact.getStatement());
                    showRevisionSources("supports", fact.getSupports(), snapshot);
                }
                for (IContextFederation.CausalDemand demand : step.getDemands()) {
                    System.out.println("    needs " + demand.getChildQuery() + " for " + demand.getParentQuery());
                    StringBuilder mapping = new StringBuilder("    parent arguments <- child arguments: ");
                    for (int i = 0; i < demand.getParentToChild().size(); ++i) {
                        if (i > 0) mapping.append(", ");
                        int child = demand.getParentToChild().get(i);
                        mapping.append(i + 1).append(" <- ").append(child < 0 ? "local" : Integer.toString(child + 1));
                    }
                    System.out.println(mapping.toString());
                }
                for (IContextFederation.ValueRow row : step.getValues()) {
                    StringBuilder bindings = new StringBuilder("    native bindings: ");
                    for (java.util.Map.Entry<String, String> binding : row.getBindings().entrySet()) {
                        if (bindings.length() > "    native bindings: ".length()) bindings.append(", ");
                        bindings.append('$').append(binding.getKey()).append(" <- ").append(binding.getValue());
                    }
                    System.out.println(bindings.toString());
                }
            }

            for (IContextFederation.EvidenceInjection injection
                    : continuation.getEvidenceInjections()) {
                StringBuilder line =
                        new StringBuilder(
                                "  inject into X: ")
                                .append(
                                        injection.getStatement());
                if (!injection.getSubstitutions()
                        .isEmpty()) {
                    line.append("  ");
                    boolean first = true;
                    for (java.util.Map.Entry<String, String> binding
                            : injection.getSubstitutions()
                                    .entrySet()) {
                        if (!first) {
                            line.append(", ");
                        }
                        line.append('$')
                                .append(binding.getKey())
                                .append(" <- ")
                                .append(binding.getValue());
                        first = false;
                    }
                }
                if (!injection.getSupports().isEmpty()) {
                    line.append("  [");
                    boolean first = true;
                    for (IContextFederation.Revision support
                            : injection.getSupports()) {
                        if (!first) {
                            line.append(", ");
                        }
                        line.append(
                                contextLocator(
                                        snapshot,
                                        support.getContextId()))
                                .append('@')
                                .append(
                                        support.getRevision());
                        first = false;
                    }
                    line.append(']');
                }
                System.out.println(line.toString());
            }

            System.out.printf(
                    "  continuation X: %s, waves=%d, evidence=%d%n",
                    continuation.isResolved()
                            ? "RESOLVED"
                            : "UNRESOLVED",
                    continuation.getWaves(),
                    continuation.getEvidenceCount());
        }

        System.out.println(
                "Final: " + explain.getFinalTruth());

        if (!explain.getValues().isEmpty()) {
            System.out.println("Values:");
            for (IContextFederation.ValueRow row
                    : explain.getValues()) {
                StringBuilder line =
                        new StringBuilder("  ");
                boolean first = true;
                for (java.util.Map.Entry<String, String> binding
                        : row.getBindings().entrySet()) {
                    if (!first) {
                        line.append(", ");
                    }
                    line.append('$')
                            .append(binding.getKey())
                            .append(" <- ")
                            .append(binding.getValue());
                    first = false;
                }
                System.out.println(line.toString());
            }
        }

        if (!explain.getSolutions().isEmpty()) {
            System.out.println("Solutions:");
            for (String solution
                    : explain.getSolutions()) {
                System.out.println(
                        "  " + solution);
            }
        }
    }

    private static void showContextVersion(
            IContextFederation.VersionHistory history) {
        if (history == null) {
            return;
        }
        System.out.println(
                "Context " + history.getLocator());
        if (history.hasPinnedRevision()) {
            System.out.println(
                    "Pinned: "
                            + history.getPinnedRevision());
        }
        System.out.println(
                "Current: "
                        + history.getCurrentRevision());
        System.out.println();
        System.out.println(
                "Revision   Description");
        for (IContextFederation.RevisionVersion revision
                : history.getRevisions()) {
            StringBuilder markers =
                    new StringBuilder();
            if (revision.getRevision()
                    == history.getCurrentRevision()) {
                markers.append(" [CURRENT]");
            }
            if (history.hasPinnedRevision()
                    && revision.getRevision()
                            == history.getPinnedRevision()) {
                markers.append(" [PINNED]");
            }
            System.out.printf(
                    "%-10d %s%s%n",
                    revision.getRevision(),
                    revision.getDescription(),
                    markers.toString());
        }
    }

    private static void showContextOpinions(java.util.Map<String, IContextFederation.Opinion> opinions,
            org.kanger.command.CommandIntent intent) {
        if (opinions.isEmpty()) System.out.println("No meaningful source opinions");
        boolean first = true;
        for (IContextFederation.Opinion opinion : opinions.values()) {
            if (!first) System.out.println();
            first = false;
            System.out.printf("Context %s@%d [%s, isolated opinion]%n", opinion.getLocator(),
                    opinion.getSource().getRevision(), opinion.isWorking() ? "live X" : "pinned");
            System.out.println("Result: " + opinion.getResult().getResultTruth());
            if (intent == org.kanger.command.CommandIntent.CTX_OPINIONS || intent == org.kanger.command.CommandIntent.CTX_SOLVES) {
                System.out.println("Solutions (" + opinion.getSolutions().size() + "):");
                for (IContextFederation.RuleRow solution : opinion.getSolutions()) {
                    System.out.println("  Solution " + solution.id + ": " + solution.statement);
                    if (intent == org.kanger.command.CommandIntent.CTX_SOLVES)
                        showOpinionCauses(solution.causes, "    ");
                }
            }
            if (intent == org.kanger.command.CommandIntent.CTX_OPINIONS || intent == org.kanger.command.CommandIntent.CTX_VALUES) {
                System.out.println("Values (" + opinion.getResult().getValues().size() + "):");
                for (IContextFederation.ValueRow row : opinion.getResult().getValues()) System.out.println("  " + row.getBindings());
            }
            if (intent == org.kanger.command.CommandIntent.CTX_OPINIONS || intent == org.kanger.command.CommandIntent.CTX_WHEN) {
                System.out.println("Hypotheses (" + opinion.getResult().getProvisionalHypotheses().size() + "):");
                for (IContextFederation.ProvisionalHypothesis hypothesis : opinion.getResult().getProvisionalHypotheses())
                    System.out.println("  " + hypothesis.getStatement());
            }
        }
    }

    private static void showOpinionCauses(List<IContextFederation.ProofCause> causes, String indent) {
        for (IContextFederation.ProofCause cause : causes) {
            System.out.println(indent + "Rule " + cause.ruleId + ": " + cause.ruleStatement);
            System.out.println(indent + "  Donor: " + cause.donorStatement + (cause.cycle ? " [cycle]" : ""));
            showOpinionCauses(cause.causes, indent + "    ");
        }
    }

    private static void showIsolatedContextQuery(
            IContextFederation.Snapshot snapshot,
            IContextFederation.QueryResult query,
            String locator) {
        if (query == null) {
            return;
        }
        long revision = isolatedRevision(snapshot, locator);
        System.out.printf(
                "Context %s%s [isolated]%n",
                locator,
                revision < 0 ? "" : "@" + revision);
        System.out.println("Result: " + query.getResultTruth());

        if (!query.getValues().isEmpty()) {
            System.out.println("Values:");
            for (IContextFederation.ValueRow row : query.getValues()) {
                StringBuilder line = new StringBuilder("  ");
                boolean first = true;
                for (java.util.Map.Entry<String, String> binding
                        : row.getBindings().entrySet()) {
                    if (!first) {
                        line.append(", ");
                    }
                    line.append("$")
                            .append(binding.getKey())
                            .append(" = ")
                            .append(binding.getValue());
                    first = false;
                }
                System.out.println(line.toString());
            }
        }

        if (!query.getProvisionalHypotheses().isEmpty()) {
            System.out.println("Hypotheses:");
            for (IContextFederation.ProvisionalHypothesis hypothesis
                    : query.getProvisionalHypotheses()) {
                System.out.println("  " + hypothesis.getStatement());
            }
        }
    }

    private static long isolatedRevision(
            IContextFederation.Snapshot snapshot,
            String locator) {
        if (snapshot.getSourceLocator().equals(locator)) {
            return snapshot.getSourceRevision();
        }
        for (IContextFederation.Connection connection
                : snapshot.getConnections()) {
            if (connection.getLocator().equals(locator)) {
                return connection.getPinnedRevision();
            }
        }
        return -1L;
    }
    private static void showContextRules(java.util.List<IContextFederation.RuleBlock> blocks,String selection) {
        boolean first = true;
        for(IContextFederation.RuleBlock block:blocks) {
            if (!first) System.out.println();
            first = false;
            System.out.printf("Context %s@%d [%s]%n",block.locator,block.revision.getRevision(),block.working?"live X":"pinned");
            if(block.rules.isEmpty()) System.out.println("No rules selected");
            for(IContextFederation.RuleRow rule:block.rules) {
                System.out.printf("Rule %03d%s: %s%n",rule.id,rule.generated?" G":"",rule.statement);
                if("COMMENT".equals(selection)) System.out.println(rule.comment);
                for(java.util.List<String> row:rule.tree) System.out.println(String.join(" ",row));
            }
        }
    }

    private static void showFederation(
            IContextFederation.Snapshot snapshot,
            IContextFederation.QueryResult query) {
        System.out.printf("Context %s@%d%n",
                snapshot.getSourceLocator(),
                snapshot.getSourceRevision());
        System.out.println(snapshot.hasWorkingChanges()?"Connections: working changes [not saved; use ctx publish]":"Connections: saved");
        for(IContextFederation.DependencyNotice notice:snapshot.getDependencyNotices()) {
            System.out.printf("%s@%d declares %s@%d: %s%s%n",notice.owner.getLocator(),notice.owner.getRevision(),
                    notice.dependency.getLocator(),notice.dependency.getRevision(),notice.getStatus(),
                    notice.actualRevision==null?" — you may connect it to expand available knowledge":
                            notice.getStatus().equals("DIFFERENT_REVISION")?" [connected @"+notice.actualRevision+"]":"");
        }
        if (snapshot.getConnections().isEmpty()) {
            System.out.println("Direct connections: none");
        } else {
            System.out.println("Direct connections:");
            for (IContextFederation.Connection connection
                    : snapshot.getConnections()) {
                System.out.printf(
                        "  %s@%d  %-10s  %s%s%n",
                        connection.getLocator(),
                        connection.getPinnedRevision(),
                        connection.getCompatibilityStatus(),
                        connection.getPinPolicy(),
                        connection.hasNewerRevision()
                                ? "  [CURRENT="
                                        + connection.getCurrentRevision()
                                        + "]"
                                : "");
            }
        }

        if (query == null) {
            return;
        }
        System.out.printf(
                "Federated query: %s, waves=%d, evidence=%d%n",
                query.isResolved() ? "RESOLVED" : "UNRESOLVED",
                query.getWaves(),
                query.getEvidenceCount());
        for (IContextFederation.FrontierObservation observation
                : query.getObservations()) {
            System.out.printf("  wave %d  %s  => %s%n",
                    observation.getWave(),
                    observation.getQuerySource(),
                    observation.getTruth());
            showRevisionSources("TRUE",
                    observation.getTrueSources(), snapshot);
            showRevisionSources("FALSE",
                    observation.getFalseSources(), snapshot);
            showRevisionSources("UNKNOWN",
                    observation.getUnknownSources(), snapshot);
        }
        if (!query.getProvisionalHypotheses().isEmpty()) {
            System.out.println("  provisional hypotheses:");
            for (IContextFederation.ProvisionalHypothesis hypothesis
                    : query.getProvisionalHypotheses()) {
                System.out.printf("    %s@%d  %s%n",
                        contextLocator(
                                snapshot,
                                hypothesis.getSource().getContextId()),
                        hypothesis.getSource().getRevision(),
                        hypothesis.getStatement());
            }
        }
    }

    private static void showRevisionSources(
            String label,
            List<IContextFederation.Revision> revisions,
            IContextFederation.Snapshot snapshot) {
        for (IContextFederation.Revision revision : revisions) {
            System.out.printf("      %s: %s@%d%n",
                    label,
                    contextLocator(snapshot, revision.getContextId()),
                    revision.getRevision());
        }
    }

    private static String contextLocator(
            IContextFederation.Snapshot snapshot,
            java.util.UUID contextId) {
        if (snapshot.getSourceContextId().equals(contextId)) {
            return snapshot.getSourceLocator();
        }
        for (IContextFederation.Connection connection
                : snapshot.getConnections()) {
            if (connection.getTargetContextId().equals(contextId)) {
                return connection.getLocator();
            }
        }
        return "<unknown-context>";
    }

    private static void showStorage(CanonicalCommandProcessor.StorageStatus status) {
        List<String> names = status.getNames();
        String current = status.getCurrent();
        if (names.isEmpty()) {
            System.out.println("No storages available");
        } else {
            System.out.println("Storages available:");
            for (String name : names) {
                System.out.printf("\t%s%s%n", name,
                        current != null && current.equals(name) ? "  [current]" : "");
            }
        }
        System.out.println("Current storage: "
                + (status.isUsed() ? current : "none"));
    }

    private static void showStorage(IMind mind) throws Exception {
        List<String> names = new ArrayList<String>();
        for (String name : mind.getStoragesList()) {
            names.add(name);
        }
        Collections.sort(names);
        if (names.isEmpty()) {
            System.out.println("No storages available");
        } else {
            System.out.println("Storages available:");
            String current = mind.isStorageUsed() ? mind.getStorageName() : null;
            for (String name : names) {
                System.out.printf("\t%s%s%n", name,
                        current != null && current.equals(name) ? "  [current]" : "");
            }
        }
        if (mind.isStorageUsed()) {
            System.out.println("Current storage: " + mind.getStorageName());
        } else {
            System.out.println("Current storage: none");
        }
    }

    private static IMind erase(IMind mind, ConsoleLineInput input) throws Exception {
        String prompt = "Erase workspace?";
        if (mind.isStorageUsed()) {
            prompt += "\nWARNING: The contents of the currently open database "
                    + "will also be erased.";
        }
        if (!confirm(input, prompt)) {
            return mind;
        }
        while (mind.getNext() != null) {
            IMind parent = mind.getNext();
            parent.release(mind);
            mind = parent;
        }
        return mind.clearWorkspace();
    }

    private static boolean confirmQuit(IMind mind, ConsoleLineInput input) {
        if (mind.isStorageUsed() && mind.getTransactionLevel() > 0 && !mind.isEmptyLevel()) {
            return confirm(input, "Quit with an uncommitted transaction?");
        }
        return true;
    }

    private static boolean confirm(ConsoleLineInput input, String prompt) {
        String answer = input.readAuxiliary(prompt + " [y/N]? ").trim();
        return !answer.isEmpty() && Character.toUpperCase(answer.charAt(0)) == 'Y';
    }

    private static void showHelp() {
        System.out.print(HELP.render());
        System.out.println();
        System.out.println("Console-local forms:");
        System.out.println("  get | put | delete     list available source names (read-only)");
        System.out.println("  xplain                 show accumulated analyzer log");
        System.out.println("  xplain <file>          write accumulated analyzer log to file");
        System.out.println("  xplain mode on|off     toggle runtime explanation display mode");
        System.out.println("  z                      repeat the last Core query");
    }

    private static boolean isBareSourceList(String line) {
        return "get".equalsIgnoreCase(line)
                || "put".equalsIgnoreCase(line)
                || "delete".equalsIgnoreCase(line);
    }

    private static boolean isXplain(String line) {
        String first = line.split("\\s+", 2)[0].toLowerCase();
        return first.length() > 0 && "xplain".startsWith(first);
    }

    /**
     * Console-only developer hook. Deliberately bypasses the canonical command
     * grammar and is intentionally absent from help/documentation.
     */
    private static boolean isHiddenTestCommand(String line) {
        String[] parts = line.trim().split("\\s+");
        return parts.length >= 2
                && "options".equalsIgnoreCase(parts[0])
                && "test".equalsIgnoreCase(parts[1]);
    }

    private static void runHiddenTestCommand(String line, IMind mind) throws Exception {
        String[] parts = line.trim().split("\\s+");
        if (parts.length > 3) {
            throw new CommandErrorException("Invalid options test syntax");
        }
        String prefix = parts.length == 3 ? parts[2] : "";

        /*
         * Do not lend the live Console Mind/User/storage to the historical test
         * corpus. The qualification runtime creates a disposable User + Mind and,
         * when the current Console is database-backed, a private temporary DUMB
         * database. This preserves the live transaction stack and storage exactly.
         *
         * Reflection keeps the production Console independent of the qualification
         * module. The hidden command exists only when that developer/test plane is
         * present on the runtime class path.
         */
        java.net.URLClassLoader developerLoader = null;
        try {
            Class<?> runtime;
            try {
                runtime = Class.forName("org.kanger.IsolatedKangerTestRuntime");
            } catch (ClassNotFoundException missingFromRuntime) {
                File directory = new File(System.getProperty("user.dir", "."))
                        .getCanonicalFile();
                File classes = null;
                for (int depth = 0; depth < 5 && directory != null; ++depth) {
                    File candidate = new File(directory,
                            "kanger-qualification/target/test-classes");
                    File marker = new File(candidate,
                            "org/kanger/IsolatedKangerTestRuntime.class");
                    if (marker.isFile()) {
                        classes = candidate;
                        break;
                    }
                    directory = directory.getParentFile();
                }
                if (classes == null) {
                    throw missingFromRuntime;
                }
                developerLoader = new java.net.URLClassLoader(
                        new java.net.URL[]{classes.toURI().toURL()},
                        CanonicalConsole.class.getClassLoader());
                runtime = Class.forName(
                        "org.kanger.IsolatedKangerTestRuntime",
                        true,
                        developerLoader);
            }

            java.lang.reflect.Method run =
                    runtime.getDeclaredMethod("run", String.class, boolean.class);
            run.setAccessible(true);
            Object result = run.invoke(null, prefix, mind.isStorageUsed());
            if (!(result instanceof Boolean) || !((Boolean) result).booleanValue()) {
                throw new CommandErrorException("KANGER test failed");
            }
        } catch (ClassNotFoundException ex) {
            throw new CommandErrorException(
                    "Console test runtime is unavailable; compile kanger-qualification first");
        } catch (java.lang.reflect.InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new RuntimeException(cause);
        } finally {
            if (developerLoader != null) {
                developerLoader.close();
            }
        }
    }

    private static void processXplain(String line, IMind mind, ConsoleLineInput input) throws Exception {
        String[] parts = line.split("\\s+");
        if (parts.length == 3
                && "mode".equalsIgnoreCase(parts[1])
                && ("on".equalsIgnoreCase(parts[2]) || "off".equalsIgnoreCase(parts[2]))) {
            if ("on".equalsIgnoreCase(parts[2])) {
                mind.setDebugLevel(mind.getDebugLevel() | Enums.DEBUG_OPTION_RTLOGS);
            } else {
                mind.setDebugLevel(mind.getDebugLevel() & ~Enums.DEBUG_OPTION_RTLOGS);
            }
            System.out.println("Xplain runtime mode: "
                    + (((mind.getDebugLevel() & Enums.DEBUG_OPTION_RTLOGS) != 0) ? "ON" : "OFF"));
            return;
        }
        if (parts.length > 2) {
            throw new CommandErrorException("Invalid xplain syntax");
        }
        if (parts.length == 1) {
            Console.showExplanation(mind, LogMode.ALL, "xplain", null);
            return;
        }
        if (!parts[1].isEmpty() && Character.toUpperCase(parts[1].charAt(0)) == 'W') {
            String fileName = input.readAuxiliary("Save analyzer log to file: ").trim();
            Console.showExplanation(mind, LogMode.ALL,
                    fileName.isEmpty() ? "xplain" : "xplain " + fileName, null);
            return;
        }
        Console.showExplanation(mind, LogMode.ALL, "xplain " + parts[1], null);
    }

    private static long number(CommandInvocation invocation, String name) {
        return ((Number) invocation.getArgument(name)).longValue();
    }

    private static IMind track(ShutdownHook hook, IMind mind) {
        if (mind != null) {
            mind.getUser().setCurrentMind(mind);
        }
        if (hook != null) {
            hook.setMind(mind);
        }
        return mind;
    }

    private static void setParseSource(ParseSourceContext context, String source) {
        if (context != null) {
            context.source = source;
        }
    }

    private static DispatchResult same(IMind mind) {
        return new DispatchResult(mind, false);
    }

    private static final class ParseSourceContext {
        private String source;

        private String sourceOr(String fallback) {
            return source == null ? fallback : source;
        }
    }

    private static final class DispatchResult {
        private final IMind mind;
        private final boolean stop;

        private DispatchResult(IMind mind, boolean stop) {
            this.mind = mind;
            this.stop = stop;
        }
    }
}
