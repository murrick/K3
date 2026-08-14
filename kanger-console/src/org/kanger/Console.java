/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to
 *  deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 *  sell copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 *  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 *  IN THE SOFTWARE.
 *
 */

package org.kanger;

import org.kanger.compiler.Token;
import org.kanger.enums.*;
import org.kanger.exception.CommandErrorException;
import org.kanger.exception.ParseErrorException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.interfaces.*;
import org.kanger.primitives.Hypothesis;
import org.kanger.stores.HypothesisStore;
import org.kanger.stores.ValuesStore;
import org.kanger.test.KangerTest;
import org.kanger.units.Rule;

import java.io.*;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by Dmitry G. Quznetsov on 28.05.15.
 */
public class Console {

    private static String lastLogFile = "analyzer.log";
    private static Scanner sc = null;
    private static String currentLine = "";
    private static String lastComments = "";
    private static CircularBuffer<String> lastQuery = new CircularBuffer(100);

    public static String accept() {
        boolean repeat = false;
        String line = "";
        do {
            String prefix;
            if (repeat) {
                if (!currentLine.isEmpty()) {
                    currentLine += Enums.LINE_SEPARATOR;
                }
                currentLine += line;
                prefix = "  ";
            } else {
                prefix = "\n: ";
            }
            repeat = false;
            System.out.printf(prefix);
            line = sc.nextLine();
            String lineStart = "";
            String lineStop = "";

            if (currentLine.trim().length() > 1) {
                if (currentLine.trim().substring(0, 2).equals("//") || currentLine.trim().substring(0, 2).equals("/*")) {
                    lineStart = currentLine.trim().substring(0, 2);
                } else {
                    lineStart = currentLine.trim().substring(0, 1);
                }
            } else if (currentLine.trim().length() > 0) {
                lineStart = currentLine.trim().substring(0, 1);
            } else if (line.trim().length() > 1) {
                if (line.trim().substring(0, 2).equals("//") || line.trim().substring(0, 2).equals("/*")) {
                    lineStart = line.trim().substring(0, 2);
                } else {
                    lineStart = line.trim().substring(0, 1);
                }
            } else if (line.trim().length() > 0) {
                lineStart = line.trim().substring(0, 1);
            }

            if (line.trim().length() > 1) {
                if (line.trim().substring(line.trim().length() - 2).equals("*/")) {
                    lineStop = line.trim().substring(line.trim().length() - 2);
                } else {
                    lineStop = line.trim().substring(line.trim().length() - 1);
                }
            } else if (line.trim().length() > 0) {
                lineStop = line.trim().substring(line.trim().length() - 1);
            } else if (currentLine.trim().length() > 1) {
                if (lineStart.equals("/*")) {
                    lineStop = currentLine.trim().substring(currentLine.trim().length() - 2);
                } else if (!lineStart.equals("=")) {
                    lineStop = currentLine.trim().substring(currentLine.trim().length() - 1);
                }
            } else if (currentLine.trim().length() > 0) {
                lineStop = currentLine.trim().substring(currentLine.trim().length() - 1);
            }

            if ("/*".equals(lineStart)) {
                repeat = !"*/".equals(lineStop);
            } else if ("=".equals(lineStart)) {
                repeat = !lineStop.isEmpty();
            } else if (!lineStart.isEmpty() && !line.equals("?") && "!?+-=".contains(lineStart.toUpperCase().substring(0, 1))) {
                repeat = !";".equals(lineStop);
            }

        } while (repeat);
        if (!currentLine.isEmpty() && !line.isEmpty()) {
            currentLine += Enums.LINE_SEPARATOR;
        }
        currentLine += line;
        line = currentLine;
        currentLine = "";
        return line;
    }


//    public static String getSourceDir(IUser user) throws IOException {
//        return user.getSourceDir();
//        String sourcesDir = user.getProperty("user.dir") + "SRC";
//        if (user.containsKey("sources.dir")) {
//            sourcesDir = user.getProperty("sources.dir");
//        }
//        if (!sourcesDir.isEmpty() && !sourcesDir.endsWith("/") && !sourcesDir.endsWith("\\")) {
//            sourcesDir += Enums.FILE_SEPARATOR;
//            Files.createDirectories(Paths.get(sourcesDir));
//        }
//        return sourcesDir;
//    }

    public static void session(IMind mind) throws Exception, ClassNotFoundException, RuntimeErrorException {
        session(mind, null);
    }

    static void session(IMind mind, ShutdownHook shutdownHook) throws Exception, ClassNotFoundException, RuntimeErrorException {
        boolean stop = false;


        String lastQuery = "";

        sc = new Scanner(System.in);
        mind = trackShutdownMind(shutdownHook, mind);

        while (!stop) {
            String line = "";
            try {
                mind = trackShutdownMind(shutdownHook, mind);
                line = accept();

                if (line == null) {
                    line = "";
                } else if (line.trim().length() > 1 && (line.trim().substring(0, 2).equals("//") || line.trim().substring(0, 2).equals("/*"))) {
                    if (!lastComments.isEmpty()) {
                        lastComments += Enums.LINE_SEPARATOR;
                    }
                    lastComments += line;
                    continue;
                } else if (line.trim().length() > 0 && line.trim().toUpperCase().charAt(0) == 'Z') {
                    if (!lastQuery.isEmpty()) {
                        line = lastQuery;
                        System.out.println("\n: " + line);
                    } else {
                        continue;
                    }
                }

                if (line.length() > 0) {
                    switch (line.toUpperCase().charAt(0)) {
                        case 'Q':
                            if (mind.isStorageUsed() && mind.getTransactionLevel() > 0 && !mind.isEmptyLevel()) {
                                System.out.printf("Transaction level %d (%d)\n", mind.getTransactionLevel(), mind.getId());
                                System.out.printf("Are you sure to quit ? [y/N]? ");
                                String s = sc.nextLine().toUpperCase();
                                if (!s.isEmpty() && s.charAt(0) == 'Y') {
                                    stop = true;
                                }
                            } else {
                                stop = true;
                            }
                            break;
                        case 'H':
                            showCommonHelp();
                            break;
                        case 'R':
                            showRules(mind, line);
                            break;
                        case 'B':
                            showBase(mind, line);
                            break;
                        case 'F':
                            showFunctions(mind, line);
                            break;
                        case 'L':
                            showHypo(mind);
                            break;
                        case 'A':
                            makeHypo(mind, line, sc);
                            break;
                        case 'V':
                            showLog(mind, LogMode.VALUES, null, sc);
                            break;
                        case 'S':
                            showSolutions(mind, line);
                            break;
                        case 'X':
                            showExplanation(mind, LogMode.ALL, line, sc);
                            break;
                        case 'E':
                            mind = trackShutdownMind(shutdownHook, clearWorkspace(mind, sc));
                            break;
                        case 'G':
                            mind = trackShutdownMind(shutdownHook, loadSourceFile(mind, loadSource(line, mind, sc)));
                            break;
                        case 'P':
                            saveSource(line, mind, sc);
                            break;
                        case 'C':
                            mind = trackShutdownMind(shutdownHook, closeDatabase(mind, sc));
                            break;
                        case 'U':
                            mind = trackShutdownMind(shutdownHook, useDatabase(line, mind, sc));
                            break;
                        case 'D':
                            mind = trackShutdownMind(shutdownHook, dropDatabase(line, mind, sc));
                            break;
                        case 'I':
                            mind = trackShutdownMind(shutdownHook, packDatabase(line, mind, sc));
                            break;
                        case 'O':
                            options(line, mind, sc);
                            break;
                        case 'T':
                            mind = trackShutdownMind(shutdownHook, processTransaction(line, mind, sc, shutdownHook));
                            break;
                        case Enums.SUC:
                            lastQuery = line;
                        case Enums.ANT:
                        case Enums.INS:
                        case Enums.DEL:
                            processQuery(line, mind);
                            break;
                        case Enums.FOO:
                            mind.compile(line);
                            if ((mind.getDebugLevel() & Enums.DEBUG_OPTION_RTLOGS) == 0) {
                                System.out.println(mind.getCurrentLogRecord(LogMode.ANALYZER).getRecord());
                            }
                            break;
                        default:
                            System.out.printf("ERROR: Unknown Instruction\n");
                    }

                } else if (line.isEmpty()) {
                    showCopyrigt();
                }
            } catch (ParseErrorException ex) {
                int pos = ex.getExceptionPosition();
                String msg = ex.getExceptionMessage();
                System.out.println("ERROR: " + msg);
                String ps = "";
                for (int i = 0; i < lastQuery.trim().length(); ++i) {
                    char c = lastQuery.trim().charAt(i);
                    System.out.print(c);
                    if (i == pos) {
                        ps += '^';
                    }
                    if (c == '\n') {
                        if (ps.endsWith("^")) {
                            System.out.println(ps);
                        }
                        ps = "";
                    } else if (i < pos) {
                        ps += c == '\t' ? c : ' ';
                    }
                }
                System.out.println();
                if (ps.endsWith("^")) {
                    System.out.println(ps);
                }
            } catch (CommandErrorException ex) {
                System.err.println(ex.toString());
            } catch (StorageLifecycleException ex) {
                String action = ex.getRequiredAction();
                System.err.printf(
                        "ERROR: %s%s: %s%n",
                        ex.getCode(),
                        action == null || action.isEmpty()
                                ? ""
                                : " [" + action + "]",
                        ex.toString()
                );
            } catch (RuntimeErrorException ex) {
                System.err.println(ex.toString());
            } catch (Exception e) {
                System.err.println(new Date());
                e.printStackTrace(System.err);
            }

        }
        try {
            mind = trackShutdownMind(shutdownHook, mind.closeStorage());
        } catch (Exception e) {
            System.err.println(new Date());
            e.printStackTrace(System.err);
        }
        System.out.println("KANGER III Session closed");

    }

    static IMind trackShutdownMind(ShutdownHook shutdownHook, IMind mind) {
        if (shutdownHook != null) {
            shutdownHook.setMind(mind);
        }
        return mind;
    }

    private static IMind processTransaction(String line, IMind mind, Scanner sc) throws Exception {
        return processTransaction(line, mind, sc, null);
    }

    private static IMind processTransaction(String line, IMind mind, Scanner sc, ShutdownHook shutdownHook) throws Exception {
        for (String s : line.split(" ")) {
            if (!s.trim().isEmpty()) {
                switch (s.trim().toUpperCase().charAt(0)) {
                    case 'T':
                        break;
                    case 'S':
                        mind = trackShutdownMind(shutdownHook, new Mind(mind));
                        break;
                    case 'C': {
                        IMind m = mind.getNext();
                        if (m != null) {
                            if (m.commit(mind)) {
                                System.out.printf("SUCCESS: Transaction committed\n");
                                mind = trackShutdownMind(shutdownHook, m);
                            } else {
                                System.out.printf("WARNING: Commit rejected. See xplanation log for details\n");
                            }
                        } else if (mind.isStorageUsed()) {
                            mind = trackShutdownMind(shutdownHook, mind.getUser().checkpoint(mind));
                            System.out.printf("SUCCESS: Storage checkpoint completed\n");
                        }
                    }
                    break;
                    case 'R': {
                        IMind m = mind.getNext();
                        if (m != null) {
                            m.release(mind);
                            System.out.printf("SUCCESS: Transaction rolled back\n");
                            mind = trackShutdownMind(shutdownHook, m);
                        }
                    }
                    break;
                }
            }
        }
        System.out.printf("Transaction level %d (%d)\n", mind.getTransactionLevel(), mind.getId());
        return trackShutdownMind(shutdownHook, mind);
    }

    private static void processQuery(String line, IMind mind) throws Exception {
        mind.clearLog();
        ((HypothesisStore) mind.getHypothesis()).clear();
        Token t = null;
        Mind m = new Mind(mind);
        boolean succ = false;
        Boolean res = null;
        while ((t = Tools.extractLine(line, t)) != null) {
            if (t.getToken(line).charAt(0) == '?') {
                succ = true;
            }
            res = m.query(t.getToken(line));
            if (!lastComments.isEmpty() && m.getAcceptedRule() != null) {
                m.getAcceptedRule().setComment(lastComments);
                lastComments = "";
            }
        }
        if (!succ) {
            mind.commit(m);
        } else {
            mind.release(m);
            if(res == null) {
                ((HypothesisStore) mind.getHypothesis()).commit(m.getHypothesis());
            }
        }
        if ((mind.getDebugLevel() & Enums.DEBUG_OPTION_RTLOGS) == 0) {
            System.out.println(mind.getCurrentLogRecord(LogMode.ANALYZER).getRecord());
            if (res != null) {
                showLog(mind, LogMode.SOLVES, null, null);
                showLog(mind, LogMode.VALUES, null, null);
            }
            if (res == null && line.trim().charAt(0) == Enums.SUC) {
                showHypo(mind);
            }
        }
    }

    private static void showOptions(IMind mind) {
        System.out.print("Debug level: ");
        switch (mind.getDebugLevel() & 0xFF) {
            case Enums.DEBUG_LEVEL_QUIET:
                System.out.println("QUIET");
                break;
            case Enums.DEBUG_LEVEL_DEBUG:
                System.out.println("DEBUG");
                break;
        }
        System.out.println("Values of vars and funcs showed in logs: " + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_VALUES) == 0 ? "NO" : "YES"));
        System.out.println("Status of statements and rules showed in logs: " + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_STATUS) == 0 ? "NO" : "YES"));
        System.out.println("Log showing runtime: " + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_RTLOGS) == 0 ? "NO" : "YES"));
        System.out.println("Order for sorting values: " +
                (mind.getOrder().isEmpty() ? "natural" : (mind.getOrder() + " " + (mind.isAscending() ? "ASCEND" : "DESCEND"))));
    }

    private static void options(String line, IMind mind, Scanner sc) throws Exception {
        if (line.split(" ").length == 1) {
            showOptions(mind);
        } else if (line.split(" ").length > 1) {
            switch (line.split(" ")[1].toUpperCase().charAt(0)) {
                case 'H':
                    showOptionsHelp();
                    break;
                case 'M':
                    System.out.println("Memory status:");
                    System.out.println();
                    System.out.println("Total memory: " + (Runtime.getRuntime().maxMemory() / 1024 / 1024) + " mb");
                    System.out.println("Used memory: " + ((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024) + " mb");
                    System.out.println();
                    System.out.println("Dictionary: " + mind.getTerms().size());
                    System.out.println("Functions: " + mind.getLibrary().size());
                    System.out.println("Predicates: " + mind.getPredicates().size());
                    System.out.println("Rules: " + mind.getRules().size());
                    System.out.println();
                    System.out.println("Hypothesis: " + mind.getHypothesis().size());
                    System.out.println("Solutions: " + mind.getSolutions().size());
                    System.out.println("Values: " + mind.getValues().size());
                    break;
                case 'D':
                    if (line.split(" ").length > 2) {
                        mind.setDebugLevel(mind.getDebugLevel() & ~0xFF);
                        mind.setDebugLevel(line.split(" ")[2].toUpperCase().charAt(0) == 'Y'
                                ? mind.getDebugLevel() | Enums.DEBUG_LEVEL_DEBUG
                                : mind.getDebugLevel() | Enums.DEBUG_LEVEL_QUIET);
                    }
                    System.out.print("Debug level: ");
                    switch (mind.getDebugLevel() & 0xFF) {
                        case Enums.DEBUG_LEVEL_QUIET:
                            System.out.println("QUIET");
                            break;
                        case Enums.DEBUG_LEVEL_DEBUG:
                            System.out.println("DEBUG");
                            break;
                    }
                    break;
                case 'V':
                    if (line.split(" ").length > 2) {
                        mind.setDebugLevel(line.split(" ")[2].toUpperCase().charAt(0) == 'Y'
                                ? mind.getDebugLevel() | Enums.DEBUG_OPTION_VALUES
                                : mind.getDebugLevel() & ~Enums.DEBUG_OPTION_VALUES);
                    }
                    System.out.println("Values of vars and funcs showed in logs: " + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_VALUES) == 0 ? "NO" : "YES"));
                    break;
                case 'S':
                    if (line.split(" ").length > 2) {
                        mind.setDebugLevel(line.split(" ")[2].toUpperCase().charAt(0) == 'Y'
                                ? mind.getDebugLevel() | Enums.DEBUG_OPTION_STATUS
                                : mind.getDebugLevel() & ~Enums.DEBUG_OPTION_STATUS);
                    }
                    System.out.println("Status of statements and rules showed in logs: " + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_STATUS) == 0 ? "NO" : "YES"));
                    break;
                case 'L':
                    if (line.split(" ").length > 2) {
                        mind.setDebugLevel(line.split(" ")[2].toUpperCase().charAt(0) == 'Y'
                                ? mind.getDebugLevel() | Enums.DEBUG_OPTION_RTLOGS
                                : mind.getDebugLevel() & ~Enums.DEBUG_OPTION_RTLOGS);
                    }
                    System.out.println("Log showing runtime: " + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_RTLOGS) == 0 ? "NO" : "YES"));
                    break;
                case 'O':
                    if (line.split(" ").length > 2) {
                        String order = line.split(" ")[2];
                        boolean ascend = true;
                        if (line.split(" ").length > 3) {
                            ascend = line.split(" ")[3].trim().toUpperCase().charAt(0) != 'D';
                        }
                        ((ValuesStore) mind.getValues()).clear();
                        mind.setOrder("-".equals(order) ? "" : order);
                        mind.setAscending(ascend);
                    }
                    System.out.println("Order for sorting values: " +
                            (mind.getOrder().isEmpty() ? "natural" : (mind.getOrder() + " " + (mind.isAscending() ? "ASCEND" : "DESCEND"))));
                    break;
                case 'X':
                    System.out.println(Diagnostics.snapshot(mind, "console"));
                    break;
                case 'T':
                    String prefix = "";
                    if (line.split(" ").length > 2) {
                        prefix = line.split(" ")[2];
                    }
                    KangerTest.test((Mind) mind, "set_" + prefix);
                    break;
            }
        }
    }

    private static void saveSource(String line, IMind mind, Scanner sc) throws Exception {
        String fname = null;
        if (line.split(" ").length == 1) {
            System.out.println(SourceContextMaterializer.materializeCurrentLevel(mind));
            System.out.printf("Save source code to file? [Y/n]? ");
            String s = sc.nextLine().toUpperCase();
            if (s.isEmpty() || s.charAt(0) == 'Y') {
                System.out.print("Enter file name. Space for cancel: ");
                s = sc.nextLine();
                if (!s.trim().isEmpty()) {
                    fname = s.trim();
                }
            }
        } else {
            try {
                Long id = Long.parseLong(line.split(" ")[1].trim());
                System.out.println(formatRightWithComments(mind, id));
                System.out.printf("Change comments for rule? [y/N]? ");
                String s = sc.nextLine().toUpperCase();
                if (!s.isEmpty() && s.charAt(0) == 'Y') {
                    System.out.println("Enter new comment for rule ID " + id + ". Two ENTERs ends input:");
                    String out = "";
                    String text = null;
                    int counter = 0;
                    while (sc.hasNextLine()) {
                        text = sc.nextLine();
                        if (!text.isEmpty()) {
                            out += text + Enums.LINE_SEPARATOR;
                            counter = 0;
                        } else if (++counter < 2) {
                            out += Enums.LINE_SEPARATOR;
                        } else {
                            break;
                        }
                    }
                    if (out.replaceAll(Enums.LINE_SEPARATOR, "").trim().isEmpty()) {
                        out = "";
                    }
                    IRule r = mind.getRules().get(id);
                    r.setComment(out.trim());
                    System.out.println(formatRightWithComments(mind, id));
                }
            } catch (Exception ex) {
                fname = line.split(" ")[1].trim();
            }
        }
        if (fname != null && !fname.isEmpty()) {
            File f = new File(mind.getUser().getSourceDir() + fname);
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(f))) {
                bw.write(SourceContextMaterializer.materializeCurrentLevel(mind));
                System.out.println("Source file " + fname + " saved.");
            }
        }
    }

    private static IMind packDatabase(String line, IMind mind, Scanner sc) throws Exception {
        String name = null;
        if (line.split(" ").length == 2) {
            name = line.split("\\ ")[1].replace(".", Enums.FILE_SEPARATOR);
        } else if (mind.isStorageUsed()) {
            name = mind.getStorageName();
        } else {
            List<String> list = (List<String>) mind.getStoragesList();
            if (list.size() > 0) {
                System.out.println("DBs available:");
                int i = 0;
                int n = 1;
                int cnt = 4;
                for (String s : list) {
                    System.out.printf("\t%d: %s", n, s);
                    if (++i >= cnt) {
                        System.out.println();
                        i = 0;
                    }
                    ++n;
                }
                System.out.printf("\nEnter DB name %s: ", list.isEmpty() ? "" : "or file number");
                name = sc.nextLine();
                try {
                    int ps = Integer.parseInt(name);
                    ps -= 1;
                    if (ps < list.size()) {
                        name = list.get(ps);
                    }
                } catch (Exception ex) {
                }
                if (!name.isEmpty()) {
                    name = name.replace(".", Enums.FILE_SEPARATOR);
                } else {
                    System.out.println("No database used");
                }
            } else {
                System.out.println("No database used");
            }
        }
        if (name != null) {
            System.out.printf("Are you sure to pack database " + name + "? [y/N]? ");
            String s = sc.nextLine().toUpperCase();
            if (!s.isEmpty() && s.charAt(0) == 'Y') {
                mind = mind.reindexStorage(name, new IReactor<String>() {
                    @Override
                    public Object run(String o) {
                        System.out.println("Processing " + o + "...");
                        return null;
                    }
                });
                System.out.println("Database reindexed");
            }
        } else {
            System.out.println("No database used");
        }
        return mind;
    }

    private static IMind dropDatabase(String line, IMind mind, Scanner sc) throws Exception {
        String name = null;
        if (line.split(" ").length == 2) {
            name = line.split("\\ ")[1].replace(".", Enums.FILE_SEPARATOR);
        } else if (mind.isStorageUsed()) {
            name = mind.getStorageName();
        } else {
            List<String> list = (List<String>) mind.getStoragesList();
            if (list.size() > 0) {
                System.out.println("DBs available:");
                int i = 0;
                int n = 1;
                int cnt = 4;
                for (String s : list) {
                    System.out.printf("\t%d: %s", n, s);
                    if (++i >= cnt) {
                        System.out.println();
                        i = 0;
                    }
                    ++n;
                }
                System.out.printf("\nEnter DB name %s: ", list.isEmpty() ? "" : "or file number");
                name = sc.nextLine();
                try {
                    int ps = Integer.parseInt(name);
                    ps -= 1;
                    if (ps < list.size()) {
                        name = list.get(ps);
                    }
                } catch (Exception ex) {
                }
                if (!name.isEmpty()) {
                    name = name.replace(".", Enums.FILE_SEPARATOR);
                } else {
                    System.out.println("No database used");
                }
            } else {
                System.out.println("No database used");
            }
        }
        if (name != null) {
            System.out.printf("Are you sure to drop database " + name + "? [y/N]? ");
            String s = sc.nextLine().toUpperCase();
            if (!s.isEmpty() && s.charAt(0) == 'Y') {
                mind = mind.removeStorage(name);
                System.out.println("Database files removed");
            }
        }
        return mind;
    }

    private static IMind useDatabase(String line, IMind mind, Scanner sc) throws Exception {
        String backup = SourceContextMaterializer.materializeCurrentLevel(mind);
        if (line.split(" ").length == 2) {
            String name = line.split("\\ ")[1].replace(".", Enums.FILE_SEPARATOR);
            return insertStorageBaseline(name, mind, backup);
        }
        if (mind.isStorageUsed()) {
            showDBrief(mind);
            return mind;
        }
        List<String> list = (List<String>) mind.getStoragesList();
        if (list.isEmpty()) {
            System.out.println("No database used");
            return mind;
        }
        System.out.println("DBs available:");
        int i = 0;
        int n = 1;
        int cnt = 4;
        for (String s : list) {
            System.out.printf("\t%d: %s", n, s);
            if (++i >= cnt) {
                System.out.println();
                i = 0;
            }
            ++n;
        }
        System.out.printf("\nEnter DB name %s: ", "or file number");
        String name = sc.nextLine();
        try {
            int ps = Integer.parseInt(name) - 1;
            if (ps >= 0 && ps < list.size()) {
                name = list.get(ps);
            }
        } catch (Exception ex) {
        }
        if (name.isEmpty()) {
            System.out.println("No database used");
            return mind;
        }
        name = name.replace(".", Enums.FILE_SEPARATOR);
        return insertStorageBaseline(name, mind, backup);
    }

    private static IMind insertStorageBaseline(
            String name,
            IMind mind,
            String backup) throws Exception {

        mind = mind.useStorage(name);
        if (!mind.isStorageUsed()) {
            System.out.println("No database used");
            return mind;
        }
        if (backup.isEmpty()) {
            showDBrief(mind);
            return mind;
        }

        IMind imported = new Mind(mind);
        boolean importReservationOpen = true;
        try {
            if (imported.compile(backup)) {
                if (!imported.isEmptyLevel()) {
                    mind = imported;
                    System.out.printf(
                            "Transaction level %d (%d)\n",
                            mind.getTransactionLevel(),
                            mind.getId());
                } else {
                    mind.release(imported);
                    importReservationOpen = false;
                }
                showDBrief(mind);
                return mind;
            }

            List<ILogEntry> importLog = new ArrayList<>();
            for (ILogEntry entry : imported.getLog()) {
                importLog.add(entry);
            }

            mind.release(imported);
            importReservationOpen = false;
            mind = mind.closeStorage();

            if (!mind.compile(backup)) {
                throw new IllegalStateException(
                        "Cannot restore offline workspace after rejected database insertion");
            }
            mind.clearLog();
            org.kanger.stores.LogStore restoredLog =
                    (org.kanger.stores.LogStore) mind.getLog();
            for (ILogEntry entry : importLog) {
                restoredLog.add(entry.getType(), entry.getRecord());
            }

            if ((mind.getDebugLevel() & Enums.DEBUG_OPTION_RTLOGS) == 0) {
                System.out.println(
                        mind.getCurrentLogRecord(LogMode.ANALYZER).getRecord());
            }
            System.out.println("Use XPLAIN command for analisys");
            System.out.println("No database used");
            return mind;
        } catch (Exception failure) {
            if (importReservationOpen) {
                try {
                    mind.release(imported);
                    importReservationOpen = false;
                } catch (Exception rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            if (mind.isStorageUsed()) {
                try {
                    mind = mind.closeStorage();
                } catch (Exception closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            if (!backup.isEmpty() && !mind.isStorageUsed()) {
                try {
                    if (!mind.compile(backup)) {
                        failure.addSuppressed(new IllegalStateException(
                                "Cannot restore offline workspace after insertion failure"));
                    }
                } catch (Exception restoreFailure) {
                    failure.addSuppressed(restoreFailure);
                }
            }
            throw failure;
        }
    }

    private static void showDBrief(IMind mind) throws Exception {
        IMind root = mind.getTop();
        System.out.println("Database used: "
                + mind.getStorageName().replace(Enums.FILE_SEPARATOR, "."));
        System.out.println("Transaction level: "
                + mind.getTransactionLevel()
                + " (" + mind.getId() + ")");
        System.out.println("Root state: Rules "
                + root.getRules().size()
                + ", UDF " + root.getLibrary().size());
        System.out.println("Runtime canonical cache: Predicates "
                + mind.getPredicates().size()
                + ", Dictionary " + mind.getTerms().size());
    }

    private static IMind closeDatabase(IMind mind, Scanner sc) throws Exception {
        if (mind.isStorageUsed()) {
            String tmp = mind.getStorageName();
            mind = mind.closeStorage();
            System.out.printf("Database " + tmp + " closed\n");
        } else {
            System.out.println("No database used");
        }
        return mind;
    }

    private static IMind clearWorkspace(IMind mind, Scanner sc) throws Exception {
        System.out.printf("Are you sure to erase workspace? [y/N]? ");
        String s = sc.nextLine().toUpperCase();
        if (!s.isEmpty() && s.charAt(0) == 'Y') {
            mind = mind.clearWorkspace();
        }
        return mind;
    }

    private static void showSolutions(IMind mind, String line) throws Exception {
        long id = -1;
        boolean tree = false;
        for (String s : line.split(" ")) {
            if (!s.trim().isEmpty()) {
                switch (s.trim().toUpperCase().charAt(0)) {
                    case 'S':
                        break;
                    case 'T':
                        tree = true;
                        break;
                    default:
                        try {
                            id = Long.parseLong(s);
                        } catch (Exception ex) {
                            throw new CommandErrorException();
                        }
                }
            }
        }
        if (tree || id != -1) {
            if (!mind.getSolutions().isEmpty()) {
                boolean found = false;
                for (IRule log : mind.getSolutions()) {
                    if (id == -1 || id == log.getId()) {
                        found = true;
                        System.out.println(String.format("\tSolution %03d: %s", log.getId(), log.toString()));
                        if (tree && !log.getCauses().isEmpty()) {
                            showCauses(mind, log.getCauses(), 0);
                            System.out.println();
                        }
                        if (id != -1) {
                            break;
                        }
                    }
                }
                if (!found) {
                    System.out.println("No solutions selected");
                }
            } else {
                System.out.println("No solutions found");
            }
        } else {
            showLog(mind, LogMode.SOLVES, null, null);
        }
    }

    public static void showLog(IMind mind, LogMode type, File fi, Scanner sc) throws Exception {
        if (mind.getLog().size() > 0) {
            BufferedWriter f = null;
            try {
                if (fi != null) {
                    f = new BufferedWriter(new FileWriter(fi));
                }
                for (ILogEntry log : mind.getLog()) {
                    if (type == LogMode.ALL || log.getType() == type) {
                        if (f != null) {
                            String line = String.format("%s [%8s] %s",
                                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(log.getTime()),
                                    log.getType(),
                                    log.getRecord());
                            f.write(line + "\n");
                        } else {
                            System.out.println(log.getRecord());
                        }
                    }
                }
            } finally {
                if (f != null) {
                    try {
                        f.close();
                        System.out.println("Log to file " + fi.getName() + " saved.");
                    } catch (IOException e) {
                    }
                }
            }
        }
    }

    public static void showExplanation(IMind mind, LogMode type, String line, Scanner sc) throws Exception {
        if (mind.getLog().size() > 0) {
            File f = null;
            String fname = null;
            boolean write = false;
            for (String s : line.split(" ")) {
                if (!s.trim().isEmpty()) {
                    switch (s.trim().toUpperCase().charAt(0)) {
                        case 'X':
                            break;
                        case 'W':
                            write = true;
                            break;
                        default:
                            fname = s.trim();
                    }
                }
            }
            if (write && fname == null) {
                System.out.print("Save analyzer log to file (" + lastLogFile + "): ");
                String s = sc.nextLine();
                if (!s.isEmpty()) {
                    fname = s;
                }
            }
            if (fname != null) {
                try {
                    f = new File(mind.getUser().getUserDir() + fname);
                    f.createNewFile();
                    lastLogFile = fname;
                } catch (IOException ex) {
                    System.out.printf("ERROR: %s\n", ex);
                }
            }
            showLog(mind, type, f, sc);
        } else {
            System.out.printf("Explanation log is empty");
        }
    }

    public static void showCopyrigt() {
        System.out.printf("KANGER III, Version %s\n"
                + "Copyright (C) 1986-%d, Dmitry G. Quznetsov, All rights reserved!\n"
                + "Compiled: %s\n", Version.VERSION_S, Version.YEAR, Version.DATE_S);
    }

    public static void showOptionsHelp() {
        System.out.printf(
                "Available options:\n\n"
                        + "   options help                     - Get this message\n"
                        + "\n"
                        + "   options order <var> [asc|desc] - Set sort order for results to column\n"
                        + "                                    with name using ASCEND or DESCEND order\n"
                        + "   options order -                - Set sort order to natural order\n"
                        + "   options debug [yes|no]         - Show debug information in logs\n"
                        + "   options values [yes|no]        - Values of vars and funcs showed in logs\n"
                        + "   options status [yes|no]        - Status of statements and rules showed in logs\n"
                        + "   options log [yes|no]           - Show runtime log during analysis\n"
                        + "   options memory                 - Show memory status\n"
                        + "\n"
                        + "You can use just first letters of keywords.\n");
    }

    public static void showCommonHelp() {
        System.out.printf(
                "Available keywords:\n\n"
                        + "INFORMATION:\n"
                        + "   help                    - Get this message\n"
                        + "   rules                   - View rules list\n"
                        + "      rules produced         only produced statements\n"
                        + "      rules all              all rules and statements\n"
                        + "      rules level [<n>]      all rules and statements for transaction level n\n"
                        + "      rule <n>               rule with ID = n\n"
                        + "      rules tree             rules list with compiled trees\n"
                        + "      rule tree <n>          rule with compiled tree for rule with ID = n\n"
                        + "   base                    - Show predicate-split statements list\n"
                        + "      base predicates        predicates only list with IDs\n"
                        + "      base <name>            statements list for predicate with name = name\n"
                        + "      base <n>               statements list for predicate with ID = n\n"
                        + "      base tree              statements list with inference tree\n"
                        + "      base tree <n>          inference tree for statement with ID = n\n"
                        + "      base tree <name>       inference tree for statements with predicate name = name\n"
                        + "   functions               - Show user defined functions list\n"
                        + "      function <n>           function with ID = n\n"
                        + "      functions source       functions list with sources\n"
                        + "      functions source <n>   source for function with ID = n\n"
                        + "\n"
                        + "QUERY RESULTS:\n"
                        + "   values                  - Show values list\n"
                        + "   solutions               - Show solutions list\n"
                        + "      solution <n>           solution with ID = n\n"
                        + "      solutions tree         solutions list with inference tree\n"
                        + "      solutions tree <n>     inference tree for solution with ID = n\n"
                        + "   xplain                  - Show explanation log\n"
                        + "      xplain write [<fn>]    write explanation log to file with name fn\n"
                        + "      xplain <fn>            write explanation log to file with name fn\n"
                        + "   list                    - View last hypothesis list\n"
                        + "   append                  - Append hypothesis as a rule\n"
                        + "      append <n>             hypothesis with index = n\n"
                        + "   transaction             - Show current transaction level\n"
                        + "      transaction start      start new transaction\n"
                        + "      transaction commit     commit current transaction or checkpoint root storage\n"
                        + "      transaction rollback   rollback current transaction\n"
                        + "\n"
                        + "SOURCE FILES:\n"
                        + "   get [<fn>]              - Load source file with name fn from disk\n"
                        + "   put                     - Show and save source file to disk\n"
                        + "      put <n>                set comment for rule with ID = n\n"
                        + "      put <fn>               save source file with name fn\n"
                        + "\n"
                        + "DATABASE:\n"
                        + "   use [<name>]            - Create, open database with name name or show name of currently opened\n"
                        + "   close                   - Close currently opened database\n"
                        + "   drop [<name>]           - Drop currently opened or selected by name database\n"
                        + "   index [<name>]          - Pack and reindex currently opened or selected by name database\n"
                        + "\n"
                        + "SYSTEM:\n"
                        + "   ?                       - Check program for collisions\n"
                        + "   options [<options>]     - Show or change workspace options. Use \"options help\" for details\n"
                        + "   erase                   - Erase workspace\n"
                        + "   quit                    - Quit KANGER console\n"
                        + "\n"
                        + "You can use just first letters of keywords.\n");
    }

    public static void showFunctions(IMind mind, String line) throws Exception {
        long id = -1;
        boolean source = false;
        for (String s : line.split(" ")) {
            if (!s.trim().isEmpty()) {
                switch (s.trim().toUpperCase().charAt(0)) {
                    case 'F':
                        break;
                    case 'S':
                        source = true;
                        break;
                    default:
                        try {
                            id = Long.parseLong(s);
                        } catch (Exception ex) {
                            throw new CommandErrorException();
                        }
                }
            }
        }
        if (!mind.getLibrary().isEmpty()) {
            if (id == -1) {
                System.out.printf("Defined functions (%d):\n", mind.getLibrary().size());
            }
            boolean found = false;
            for (IOperation op : mind.getLibrary()) {
                if (!op.isDeleted(mind) && op.getMode() == LibMode.FUNCTION && (id == -1 || id == op.getId())) {
                    found = true;
                    System.out.printf("Function %03d: %s;\n", op.getId(), op.toString());
                    if (source && !op.getScripts().isEmpty()) {
                        for (String s : op.asString().split("\n")) {
                            System.out.printf("\t%s\n", s);
                        }
                        System.out.printf("\n");
                    }
                    if (id != -1) {
                        break;
                    }
                }
            }
            if (!found) {
                System.out.printf("No functions selected\n");
            }
        } else {
            System.out.printf("No functions defined\n");
        }
    }

    public static void showCauses(IMind mind, Set<ICause> causes, int level) throws Exception {
        String indent = "";
        for (int i = 0; i <= level; ++i) {
            indent += "\t";
        }
        if (level > 50) {
            System.out.printf("\t%s...\n", indent);
            return;
        }
        boolean ruleShowed = false;
        for (ICause c : causes) {
            IRule donor = c.getDonor(mind);
            if (donor != null) {
                if (!ruleShowed) {
                    System.out.printf("\t%sRule:  %s\n", indent, c.getRule(mind).toString().replaceAll("\n", " ").replaceAll("  ", " "));
                }
                System.out.printf("\t%sCause: %s\n", indent, donor.toString());
                showCauses(mind, donor.getCauses(), level + 1);
            }
        }
    }

    private static void showPredRecurse(IMind mind, IRule dest, boolean showCauses) throws Exception {
        if (dest != null) {
            if (showCauses) {
                System.out.println("\t-------------------------------------------");
            }
            System.out.printf("\t%s%03d: %s\n",
                    (mind.getDebugLevel() & Enums.DEBUG_OPTION_STATUS) != 0 ? String.format("%03d ", ((Rule) dest).getMindId()) : "",
                    dest.getId(),
                    dest.toString());
            if (showCauses && !dest.getCauses().isEmpty()) {
                showCauses(mind, dest.getCauses(), 0);
            }
        }
    }

    public static void showPred(IMind mind, IPredicate p, boolean showCauses) throws Exception {
        System.out.printf("Predicate %s(%d) :\n", p.getName(mind), p.getRange());
        for (IRule r : mind.getRules()) {
            if (!r.isDeleted(mind) && r.isStored() && ((Rule) r).getPredicateId() == p.getId()) {
                showPredRecurse(mind, r, showCauses);
            }
        }
    }

    public static void showBase(IMind mind, String line) throws Exception {
        long id = -1;
        String name = "";
        boolean preds = false;
        boolean tree = false;
        for (String s : line.split(" ")) {
            if (!s.trim().isEmpty()) {
                switch (s.trim().toUpperCase().charAt(0)) {
                    case 'B':
                        break;
                    case 'P':
                        preds = true;
                        break;
                    case 'T':
                        tree = true;
                        break;
                    default:
                        try {
                            id = Long.parseLong(s);
                        } catch (Exception ex) {
                            name = s.trim();
                        }
                }
            }
        }
        if (id != -1) {
            if (!tree) {
                IPredicate p = mind.getPredicates().get(id);
                if (p != null && (name.isEmpty() || p.getName(mind).equalsIgnoreCase(name))) {
                    if (preds) {
                        System.out.printf(
                                "Predicate %03d: %s",
                                p.getId(),
                                ((org.kanger.units.Predicate) p).toString(mind));
                    } else {
                        showPred(mind, p, tree);
                    }
                }
            } else {
                IRule dest = mind.getRules().get(id);
                if (dest != null) {
                    System.out.printf("Statement %03d: %s\n", dest.getId(), dest.toString());
                    if (!dest.getCauses().isEmpty()) {
                        showCauses(mind, dest.getCauses(), -1);
                    } else {
                        System.out.printf("Have not solutions variants\n");
                    }
                }
            }
        } else {
            boolean found = false;
            for (IPredicate p : mind.getPredicates()) {
                if (!p.isDeleted(mind) && !((Mind) mind).isSystem(p) && !p.isEmpty(mind)
                        && (name.isEmpty() || p.getName(mind).equalsIgnoreCase(name))) {
                    if (preds) {
                        found = true;
                        System.out.printf(
                                "Predicate %03d: %s;%n",
                                p.getId(),
                                ((org.kanger.units.Predicate) p).toString(mind));
                    } else {
                        found = true;
                        showPred(mind, p, tree);
                        System.out.printf("\n");
                    }
                }
            }
            if (!found) {
                System.out.printf("No statements selected\n");
            }
        }
    }

    public static void showHypo(IMind mind) throws Exception {
        int i;
        if (!mind.getHypothesis().isEmpty()) {
            System.out.printf("Optimizing hypothesis list...");
            mind.optimizeHypothesis();
            System.out.printf("\nHypothesis list (%d):\n", mind.getHypothesis().size());
            for (i = 0; i < mind.getHypothesis().size(); ++i) {
                System.out.printf("\t%03d:\t%s\n", i + 1, ((Hypothesis) mind.getHypothesis().get(i)).toString(mind));
            }
        } else {
            System.out.printf("No hypothesis found\n");
        }
    }

    public static void showTree(IMind mind, IRule r) throws Exception {
        List<List<String>> net = ((Mind) mind).formatTree(r);
        if (net.size() > 0 && net.get(0).size() > 0) {
            for (int i = 0; i < net.get(0).size(); ++i) {
                for (int k = 0; k < net.size(); ++k) {
                    System.out.print(net.get(k).get(i));
                    if (k + 1 < net.size()) {
                        System.out.print(" ");
                    }
                }
                System.out.println();
            }
        }
    }

    public static void showRules(IMind mind, String line) throws Exception {
        long id = -1;
        boolean tree = false;
        int prods = 1;
        boolean level = false;
        Long requestedLevel = null;
        for (String s : line.split(" ")) {
            if (!s.trim().isEmpty()) {
                switch (s.trim().toUpperCase().charAt(0)) {
                    case 'R':
                        break;
                    case 'T':
                        tree = true;
                        break;
                    case 'P':
                        prods = 2;
                        break;
                    case 'A':
                        prods = 0;
                        break;
                    case 'L':
                        level = true;
                        break;
                    default:
                        try {
                            long value = Long.parseLong(s);
                            if (level) {
                                requestedLevel = value;
                            } else {
                                id = value;
                            }
                        } catch (Exception ex) {
                            throw new CommandErrorException();
                        }
                }
            }
        }

        if (level) {
            if (requestedLevel != null) {
                showRulesForLevel(mind, transactionLevelMind(mind, requestedLevel), tree);
            } else {
                List<IMind> levels = new ArrayList<>();
                for (IMind m = mind; m != null; m = m.getNext()) {
                    levels.add(m);
                }
                Collections.reverse(levels);
                for (IMind m : levels) {
                    showRulesForLevel(mind, m, tree);
                }
            }
            return;
        }

        boolean found = false;
        for (IRule r : mind.getRules()) {
            if (!r.isDeleted(mind)
                    && (r.getId() == id || (id == -1 && (
                    (prods == 1 && !r.isGenerated())
                            || (prods == 2 && r.isGenerated())
                            || prods == 0)))) {
                found = true;
                showRule(mind, mind, r, tree);
                if (id != -1) {
                    break;
                }
            }
        }
        if (!found) {
            System.out.printf("No rules selected\n");
        }
    }

    private static IMind transactionLevelMind(IMind mind, long level) throws Exception {
        if (level < 0 || level > mind.getTransactionLevel()) {
            throw new CommandErrorException("Invalid transaction level " + level);
        }
        for (IMind m = mind; m != null; m = m.getNext()) {
            if (m.getTransactionLevel() == level) {
                return m;
            }
        }
        throw new CommandErrorException("Invalid transaction level " + level);
    }

    private static boolean ruleVisibleAt(IMind mind, long id) throws Exception {
        if (mind == null) {
            return false;
        }
        IRule rule = mind.getRules().get(id);
        return rule != null && !rule.isDeleted(mind);
    }

    private static boolean ruleIntroducedAt(IRule rule, IMind level) throws Exception {
        if (!ruleVisibleAt(level, rule.getId())) {
            return false;
        }
        IMind parent = level.getNext();
        return parent == null || !ruleVisibleAt(parent, rule.getId());
    }

    private static void showRulesForLevel(IMind activeMind, IMind level, boolean tree) throws Exception {
        System.out.printf(" --- Rules for transaction level %d (%d)\n",
                level.getTransactionLevel(), level.getId());
        boolean found = false;
        for (IRule r : level.getRules()) {
            if (ruleIntroducedAt(r, level)) {
                found = true;
                showRule(activeMind, level, r, tree);
            }
        }

        Map<UnitType, Set<Long>> deleted = ((Mind) level).getDeleted();
        if (deleted.containsKey(UnitType.RULE) && !deleted.get(UnitType.RULE).isEmpty()) {
            if (found) {
                System.out.printf("\n");
            }
            System.out.printf(" --- Deleted rules for level %d (%d)\n",
                    level.getTransactionLevel(), level.getId());
            for (long rid : deleted.get(UnitType.RULE)) {
                IRule r = level.getRules().get(rid);
                if (r != null) {
                    found = true;
                    showRule(activeMind, level, r, tree);
                }
            }
        }
        if (!found) {
            System.out.printf("No rules selected\n");
        }
    }

    private static void showRule(IMind activeMind, IMind stateMind, IRule r, boolean tree) throws Exception {
        System.out.printf("%sRule %03d%s: %s\n",
                (tree ? " --- " : "")
                        + ((activeMind.getDebugLevel() & Enums.DEBUG_OPTION_STATUS) != 0
                        ? String.format("%03d ", ((Rule) r).getMindId()) : ""),
                r.getId(),
                (activeMind.getDebugLevel() & Enums.DEBUG_OPTION_STATUS) != 0
                        && (r.isGenerated() || r.isQuery() || r.isStored() || r.isDeleted(stateMind))
                        ? " "
                        + (r.isGenerated() ? "G" : "")
                        + (r.isStored() ? "B" : "")
                        + (r.isQuery() ? "Q" : "")
                        + (r.isDeleted(stateMind) ? "D" : "")
                        : "",
                r.getOrigin());
        if (tree || r.getOrigin().isEmpty()) {
            int save = activeMind.getDebugLevel();
            activeMind.setDebugLevel(save & ~Enums.DEBUG_OPTION_STATUS);
            showTree(activeMind, r);
            activeMind.setDebugLevel(save);
            System.out.printf("\n");
        }
    }

    public static void makeHypo(IMind mind, String line, Scanner sc) throws Exception {
        int i = -1;
        if (line.split(" ").length >= 2) {
            try {
                i = Integer.parseInt(line.split(" ")[1]);
            } catch (Exception e) {
                throw new CommandErrorException();
            }
        }
        if (i == -1) {
            System.out.printf("Enter Hypothesis Number: ");
            String n = sc.nextLine();
            try {
                i = Integer.parseInt(n);
            } catch (Exception e) {
                throw new CommandErrorException();
            }
        }
        --i;
        try {
            String temp = ((Hypothesis) mind.getHypothesis().get(i)).toString(mind);
            String h = String.format("%s;", temp.replaceAll(String.format("%c", Enums.EOLN), ""));
            if (h != null) {
                System.out.println("Statement: " + h);
                Boolean res = ((Mind) mind).queryAccept(h, null, true);
                if (res != null && (mind.getDebugLevel() & Enums.DEBUG_OPTION_RTLOGS) == 0) {
                    showLog(mind, LogMode.SOLVES, null, null);
                    showLog(mind, LogMode.VALUES, null, null);
                    System.out.println(mind.getCurrentLogRecord(LogMode.ANALYZER).getRecord());
                }
            }
        } catch (Exception e) {
            throw new CommandErrorException();
        }
    }

    public static File loadSource(String line, IMind mind, Scanner sc) throws Exception {
        File f = null;
        if (line.split(" ").length == 1) {
            List<File> list = new ArrayList<>();
            File[] dir = new File(mind.getUser().getSourceDir()).listFiles();
            if (dir != null) {
                for (File fl : dir) {
                    if (!fl.isDirectory() && fl.getName().contains(".k")) {
                        list.add(fl);
                    }
                }
            }
            if (list.size() > 0) {
                System.out.println("Files available:");
                int i = 0;
                int n = 1;
                int cnt = 4;
                for (File fl : list) {
                    System.out.printf("\t%d: %s", n, fl.getName());
                    if (++i >= cnt) {
                        System.out.println();
                        i = 0;
                    }
                    ++n;
                }
            }
            System.out.printf("\nEnter file name %s: ", list.isEmpty() ? "" : "or file number");
            String fn = sc.nextLine();
            try {
                int ps = Integer.parseInt(fn);
                ps -= 1;
                if (ps < list.size()) {
                    f = list.get(ps);
                }
            } catch (Exception ex) {
            }
        } else {
            f = new File(mind.getUser().getSourceDir() + line.split(" ")[1]);
        }
        return f;
    }

    public static IMind loadSourceFile(IMind mind, File f) throws Exception {
        boolean res = false;
        if (f == null) {
            System.out.printf("No source files selected");
        } else {
            if (f.exists()) {
                final int length = (int) f.length();
                if (length != 0) {
                    char[] cbuf = new char[length];
                    InputStreamReader isr = new InputStreamReader(new FileInputStream(f), "UTF-8");
                    final int read = isr.read(cbuf);
                    StringBuffer buf = new StringBuffer(new String(cbuf).replace("\r\n", "\r"));
                    isr.close();
                    if (mind.isStorageUsed()) {
                        mind = new Mind(mind);
                    }
                    res = mind.compile(buf.toString());
                    if ((mind.getDebugLevel() & Enums.DEBUG_OPTION_RTLOGS) == 0) {
                        System.out.println(mind.getCurrentLogRecord(LogMode.ANALYZER).getRecord());
                    }
                    if (res) {
                        System.out.printf("File %s loaded\n", f.getName());
                    } else {
                        System.out.printf("Use XPLAIN command for analisys\n");
                    }
                    if (mind.isStorageUsed() && mind.isEmptyLevel()) {
                        IMind m = mind.getNext();
                        m.release(mind);
                        mind = m;
                    }
                    if (mind.getTransactionLevel() > 0) {
                        System.out.printf("Transaction level %d (%d)\n", mind.getTransactionLevel(), mind.getId());
                    }
                } else {
                    System.out.printf("WARNING: File %s is empty\n", f.getName());
                }
            } else {
                System.out.printf("WARNING: File %s not found\n", f.getName());
            }
        }
        return mind;
    }

    public static String formatRightWithComments(IMind mind, long id) throws Exception {
        String str = String.format(" -- Right %03d: ", id);
        str += Enums.LINE_SEPARATOR;
        IRule r = mind.getRules().get(id);
        if (r != null) {
            if (!r.getComment().isEmpty()) {
                for (String s : r.getComment().split("\\R")) {
                    str += s + Enums.LINE_SEPARATOR;
                }
            }
            for (String s : r.getOrigin().split("\\R")) {
                str += s + Enums.LINE_SEPARATOR;
            }
        }
        return str;
    }

    public static class CircularBuffer<T> {
        private T[] buffer;
        private int tail;
        private int head;

        @SuppressWarnings("unchecked")
        public CircularBuffer(int n) {
            buffer = (T[]) new Object[n];
            tail = 0;
            head = 0;
        }

        public void add(T toAdd) {
            if (head != (tail - 1)) {
                buffer[head++] = toAdd;
            } else {
                throw new BufferOverflowException();
            }
            head = head % buffer.length;
        }

        public T get() {
            T t = null;
            int adjTail = tail > head ? tail - buffer.length : tail;
            if (adjTail < head) {
                t = (T) buffer[tail++];
                tail = tail % buffer.length;
            } else {
                throw new BufferUnderflowException();
            }
            return t;
        }

        public String toString() {
            return "CircularBuffer(size=" + buffer.length + ", head=" + head + ", tail=" + tail + ")";
        }
    }
}
