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
            System.out.println("Use xplain for analysis");
        }
        return mind;
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
