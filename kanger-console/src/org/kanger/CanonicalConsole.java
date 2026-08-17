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
import org.kanger.compiler.Token;
import org.kanger.enums.Enums;
import org.kanger.enums.LogMode;
import org.kanger.exception.CommandErrorException;
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
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

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
        Scanner scanner = new Scanner(System.in);
        mind = track(shutdownHook, mind);

        while (!stop) {
            String line = "";
            try {
                mind = track(shutdownHook, mind);
                line = accept(scanner);
                if (line == null) {
                    line = "";
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
                    processXplain(trimmed, mind, scanner);
                    continue;
                }

                CommandInvocation invocation = PARSER.parse(line);
                if (invocation.isCoreLanguage()) {
                    if (trimmed.charAt(0) == Enums.SUC) {
                        lastQuery = line;
                    }
                    processCore(line, mind);
                    continue;
                }

                DispatchResult result = dispatch(invocation, mind, scanner, shutdownHook);
                mind = track(shutdownHook, result.mind);
                stop = result.stop;
            } catch (CommandParseException ex) {
                System.err.printf("ERROR: %s: %s%n", ex.getReason(), ex.getMessage());
            } catch (ParseErrorException ex) {
                showParseError(ex, lastQuery);
            } catch (CommandErrorException ex) {
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
                                           Scanner scanner,
                                           ShutdownHook shutdownHook) throws Exception {
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
                acceptWhen(mind, number(invocation, "index"));
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
                showTransaction(mind);
                return same(mind);

            case SOURCE_GET:
                mind = loadSource(mind,
                        String.valueOf(invocation.getArgument("source")));
                return same(track(shutdownHook, mind));
            case SOURCE_PUT:
                saveSource(mind, String.valueOf(invocation.getArgument("source")), scanner);
                return same(mind);
            case SOURCE_DELETE:
                deleteSource(mind, String.valueOf(invocation.getArgument("source")), scanner);
                return same(mind);

            case STORAGE_STATUS:
            case STORAGE_USE:
            case STORAGE_CLOSE:
            case STORAGE_DROP:
                if (invocation.getIntent() == org.kanger.command.CommandIntent.STORAGE_DROP
                        && !confirm(scanner, "Drop storage "
                        + String.valueOf(invocation.getArgument("name")) + "?")) {
                    return same(mind);
                }
                CanonicalCommandProcessor.Result storage =
                        COMMAND_PROCESSOR.execute(invocation, mind.getUser());
                if (!storage.isHandled() || storage.getStorageStatus() == null) {
                    throw new CommandErrorException("Unsupported canonical intent "
                            + invocation.getIntent());
                }
                mind = track(shutdownHook, storage.getMind());
                if (invocation.getIntent() == org.kanger.command.CommandIntent.STORAGE_CLOSE
                        || invocation.getIntent() == org.kanger.command.CommandIntent.STORAGE_DROP) {
                    if (!storage.getDescription().isEmpty()) {
                        System.out.println(storage.getDescription());
                    }
                } else {
                    showStorage(storage.getStorageStatus());
                }
                return same(mind);
            case STORAGE_REINDEX:
                mind = reindexStorage(mind, String.valueOf(invocation.getArgument("name")));
                return same(track(shutdownHook, mind));

            case ERASE:
                mind = erase(mind, scanner);
                return same(track(shutdownHook, mind));
            case HELP:
                showHelp();
                return same(mind);
            case QUIT:
                return new DispatchResult(mind, confirmQuit(mind, scanner));
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

    private static void showValues(IMind mind, final List<SortKey> keys) throws Exception {
        List<Map<String, ITerm>> rows = new ArrayList<Map<String, ITerm>>();
        for (Map<String, ITerm> row : mind.getValues()) {
            rows.add(new LinkedHashMap<String, ITerm>(row));
        }
        if (keys != null && !keys.isEmpty()) {
            for (SortKey key : keys) {
                boolean found = false;
                for (Map<String, ITerm> row : rows) {
                    if (row.containsKey(key.getField())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    throw new CommandErrorException("Values field not found " + key.getField());
                }
            }
            Collections.sort(rows, new Comparator<Map<String, ITerm>>() {
                @Override
                public int compare(Map<String, ITerm> left, Map<String, ITerm> right) {
                    for (SortKey key : keys) {
                        ITerm l = left.get(key.getField());
                        ITerm r = right.get(key.getField());
                        int compared;
                        if (l == null && r == null) {
                            compared = 0;
                        } else if (l == null) {
                            compared = -1;
                        } else if (r == null) {
                            compared = 1;
                        } else {
                            compared = l.compareTo(r);
                        }
                        if (key.getDirection() == SortKey.Direction.DESC) {
                            compared = -compared;
                        }
                        if (compared != 0) {
                            return compared;
                        }
                    }
                    return 0;
                }
            });
        }
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
                    ((Hypothesis) mind.getHypothesis().get(i)).toString(mind));
        }
    }

    private static void acceptWhen(IMind mind, long index) throws Exception {
        mind.optimizeHypothesis();
        if (index < 0 || index >= mind.getHypothesis().size()) {
            throw new CommandErrorException("Hypothesis index out of range " + index);
        }
        IHypothesis selected = mind.getHypothesis().get(index);
        String source = ((Hypothesis) selected).toString(mind);
        String statement = String.format("%s;",
                source.replaceAll(String.format("%c", Enums.EOLN), ""));
        System.out.println("Statement: " + statement);
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

    private static void showTransaction(IMind mind) {
        System.out.printf("Transaction level %d (%d)%n",
                mind.getTransactionLevel(), mind.getId());
    }

    private static void showSourceNames(IMind mind) {
        File[] files = new File(mind.getUser().getSourceDir()).listFiles();
        List<String> names = new ArrayList<String>();
        if (files != null) {
            for (File file : files) {
                if (!file.isDirectory() && file.getName().contains(".k")) {
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

    private static void saveSource(IMind mind, String name, Scanner scanner) throws Exception {
        File file = sourceFile(mind, name);
        if (file.exists() && !confirm(scanner, "Overwrite source file " + name + "?")) {
            return;
        }
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
            writer.write(SourceContextMaterializer.materializeCurrentLevel(mind));
        }
        System.out.println("Source file " + name + " saved.");
    }

    private static void deleteSource(IMind mind, String name, Scanner scanner) throws Exception {
        File file = sourceFile(mind, name);
        if (!file.exists()) {
            System.out.println("Source file " + name + " not found");
            return;
        }
        if (!confirm(scanner, "Delete source file " + name + "?")) {
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

    private static String storageName(String name) {
        return name.replace(".", Enums.FILE_SEPARATOR);
    }

    private static IMind reindexStorage(IMind mind, String logicalName) throws Exception {
        final String name = storageName(logicalName);
        IMind result = mind.reindexStorage(name, new IReactor<String>() {
            @Override
            public Object run(String item) {
                System.out.println("Processing " + item + "...");
                return null;
            }
        });
        System.out.println("Database reindexed");
        return result;
    }

    private static IMind erase(IMind mind, Scanner scanner) throws Exception {
        if (!confirm(scanner, "Erase workspace?")) {
            return mind;
        }
        while (mind.getNext() != null) {
            IMind parent = mind.getNext();
            parent.release(mind);
            mind = parent;
        }
        return mind.clearWorkspace();
    }

    private static boolean confirmQuit(IMind mind, Scanner scanner) {
        if (mind.isStorageUsed() && mind.getTransactionLevel() > 0 && !mind.isEmptyLevel()) {
            return confirm(scanner, "Quit with an uncommitted transaction?");
        }
        return true;
    }

    private static boolean confirm(Scanner scanner, String prompt) {
        System.out.print(prompt + " [y/N]? ");
        String answer = scanner.nextLine().trim();
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

    private static void processXplain(String line, IMind mind, Scanner scanner) throws Exception {
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
            Console.showExplanation(mind, LogMode.ALL, "xplain", scanner);
        } else {
            Console.showExplanation(mind, LogMode.ALL, "xplain " + parts[1], scanner);
        }
    }

    private static String accept(Scanner scanner) {
        boolean repeat;
        String current = "";
        String line;
        do {
            System.out.print(current.isEmpty() ? "\n: " : "  ");
            line = scanner.nextLine();
            if (!current.isEmpty()) {
                current += Enums.LINE_SEPARATOR;
            }
            current += line;

            String trimmed = current.trim();
            String lineStart = trimmed.length() >= 2
                    && (trimmed.startsWith("//") || trimmed.startsWith("/*"))
                    ? trimmed.substring(0, 2)
                    : (trimmed.isEmpty() ? "" : trimmed.substring(0, 1));
            String lineStop = trimmed.length() >= 2 && trimmed.endsWith("*/")
                    ? "*/"
                    : (trimmed.isEmpty() ? "" : trimmed.substring(trimmed.length() - 1));

            if ("/*".equals(lineStart)) {
                repeat = !"*/".equals(lineStop);
            } else if ("=".equals(lineStart)) {
                repeat = !line.trim().isEmpty();
            } else if (!lineStart.isEmpty()
                    && !"?".equals(trimmed)
                    && "!?+-=".contains(lineStart.substring(0, 1))) {
                repeat = !";".equals(lineStop);
            } else {
                repeat = false;
            }
        } while (repeat);
        return current;
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

    private static DispatchResult same(IMind mind) {
        return new DispatchResult(mind, false);
    }

    private static void showParseError(ParseErrorException ex, String lastQuery) {
        int pos = ex.getExceptionPosition();
        System.out.println("ERROR: " + ex.getExceptionMessage());
        if (lastQuery == null || lastQuery.isEmpty()) {
            return;
        }
        StringBuilder marker = new StringBuilder();
        for (int i = 0; i < lastQuery.trim().length(); ++i) {
            char c = lastQuery.trim().charAt(i);
            System.out.print(c);
            if (i == pos) {
                marker.append('^');
            } else if (c == '\n') {
                if (marker.length() > 0 && marker.charAt(marker.length() - 1) == '^') {
                    System.out.println(marker.toString());
                }
                marker.setLength(0);
            } else if (i < pos) {
                marker.append(c == '\t' ? c : ' ');
            }
        }
        System.out.println();
        if (marker.length() > 0 && marker.charAt(marker.length() - 1) == '^') {
            System.out.println(marker.toString());
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
