package org.kanger;

import org.kanger.enums.Enums;
import org.kanger.enums.LibMode;
import org.kanger.enums.LogMode;
import org.kanger.enums.Tools;
import org.kanger.exception.ParseErrorException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.primitives.Cause;
import org.kanger.primitives.Hypothesis;
import org.kanger.primitives.LogEntry;
import org.kanger.stores.LogStore;
import org.kanger.test.KangerTest;
import org.kanger.units.*;

import java.io.*;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

/**
 * Created by Dmitry G. Qusnetsov on 28.05.15. $Author: murray $
 */
public class Screen {

    private static String lastLogFile = "analizer.log";
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
                if (currentLine.trim().substring(0, 2).equals("/*")) {
                    lineStop = currentLine.trim().substring(currentLine.trim().length() - 2);
                } else {
                    lineStop = currentLine.trim().substring(currentLine.trim().length() - 1);
                }
            } else if (currentLine.trim().length() > 0) {
                lineStop = currentLine.trim().substring(currentLine.trim().length() - 1);
            }

            if ("/*".equals(lineStart)) {
                repeat = !"*/".equals(lineStop);
            } else if (!lineStart.isEmpty() && !line.equals("?") && "!?+-=".contains(lineStart.toUpperCase().substring(0, 1))) {
                repeat = !";".equals(lineStop);
            }

        } while (repeat);
        line = currentLine + line;
        currentLine = "";
        return line;
    }


    public static void session(IUser user) throws Exception, ClassNotFoundException, RuntimeErrorException {
        boolean stop = false;
        Mind mind = new Mind(user);

        //TODO: Волшебство
        mind.query("?a;");

        String lastQuery = "";

        String sourcesDir = user.getProperty("user.dir") + Enums.FILE_SEPARATOR + "SRC";
        if (user.containsKey("sources.dir")) {
            sourcesDir = user.getProperty("sources.dir");
        }
        if (!sourcesDir.isEmpty() && !sourcesDir.endsWith("/") && !sourcesDir.endsWith("\\")) {
            sourcesDir += Enums.FILE_SEPARATOR;
            Files.createDirectories(Paths.get(sourcesDir));
        }

        showCopyrigt(user);

        sc = new Scanner(System.in);

        try {
            Global.getUdf();
            System.out.println("UDF module loaded");
        } catch (RuntimeErrorException e) {
        }

        try {
            user.getData();
            System.out.println("DB module loaded: " + user.getData().getDescription());
        } catch (RuntimeErrorException e) {
        }

        while (!stop) {
            String line = "";
            try {
                line = accept();

                if (line == null) {
                    line = "";
                } else if (line.length() > 1 && (line.trim().substring(0, 2).equals("//") || line.trim().substring(0, 2).equals("/*"))) {
                    if (!lastComments.isEmpty()) {
                        lastComments += Enums.LINE_SEPARATOR;
                    }
                    lastComments += line;
                    continue;
                } else if (line.toUpperCase().charAt(0) == 'A') {
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
                            stop = true;
                            break;
                        case 'H':
                            showCommonHelp();
                            break;
                        case 'R':
                            showRights(mind, line.charAt(0) != 'r');
                            break;
                        case 'B':
                            showBase(mind, line.charAt(0) != 'b', line.trim().contains(" ") ? line.split(" ")[1] : null);
                            break;
                        case 'F':
                            showFunctions(mind, line.charAt(0) != 'f');
                            break;
                        case 'L':
                            showHypo(mind);
                            break;
                        case 'V':
                            showLog(mind, LogMode.VALUES, false);
                            break;
                        case 'S':
                            showSolutions(mind, line);
                            break;
                        case 'X':
                            showLog(mind, LogMode.ALL, line.charAt(0) != 'x');
                            break;
                        case 'I':
                            makeHypo(mind, sc);
                            break;
                        case 'E':
                            clearWorkspace(user, mind, sc);
                            break;
                        case 'C':
                            closeDatabase(user, mind, sc);
                            break;
                        case 'G':
                            loadSource(mind, sourcesDir);
                            break;
                        case 'U':
                            useDatabase(line, user, mind, sc);
                            break;
                        case 'D':
                            dropDatabase(user, mind, sc);
                            break;
                        case 'M':
                            saveSource(line, user, mind, sc);
                            break;
                        case 'P':
                            packDatabase(user, mind, sc);
                            break;
                        case 'O':
                            options(line, user, mind, sc);
                            break;
                        case Enums.SUC:
                            lastQuery = line;
                        case Enums.ANT:
                        case Enums.INS:
                        case Enums.DEL:
                            processQuery(line, mind);
                            break;
                        case Enums.FOO:
                            processFunction(line, mind);
                            break;
                        default:
                            System.out.printf("ERROR: Unknown Instruction\n");
                    }

                } else if (line.isEmpty()) {
                    showCopyrigt(user);
                }
            } catch (ParseErrorException ex) {
                String x = ex.toString();
                int pos = Integer.parseInt(x.split("@")[0]);
                String msg = x.split("@")[1];
                System.out.println("ERROR: " + msg);
                System.out.println(mind.getCompliedLine());
                while (pos-- > 0) {
                    System.out.print(" ");
                }
                System.out.println("^");
            } catch (RuntimeErrorException ex) {
                System.err.println(ex.toString());
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }

        }
        try {
            user.close();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        System.out.println("KANGER III Session closed");

    }

    private static void processFunction(String line, Mind mind) throws Exception {
        mind.setCompliedLine(line);
        SysOp op = (SysOp) mind.compileLine(line, false, null);
        System.out.printf("SUCCESS: Library updated: =%s;\n", op.toString());
    }

    private static void processQuery(String line, Mind mind) throws Exception {
        int pos = 0;
        Object[] t = null;
        while ((t = Tools.extractLine(line, pos)) != null) {
            pos = (int) t[1];
            String ln = (String) t[0];

            Boolean res = mind.query(ln);
            if (!lastComments.isEmpty() && mind.getAcceptedRight() != null) {
                mind.getComments().add(mind.getAcceptedRight().getId(), lastComments);
                lastComments = "";
            }
            if ((mind.getDebugLevel() & Enums.DEBUG_OPTION_RTLOGS) == 0) {
                System.out.println(mind.getLog().getCurrent(LogMode.ANALIZER).getRecord());
                if (res != null) {
                    showLog(mind, LogMode.SOLVES, false);
                    showLog(mind, LogMode.VALUES, false);
                }
                if (res == null) {
                    showHypo(mind);
                }
            }
        }
    }

    private static void options(String line, IUser user, Mind mind, Scanner sc) throws Exception {
        if (line.length() == 1) {
            showOptions(mind);
        } else {
            switch (line.charAt(1)) {
                case 'h':
                case 'H':
                    showOptionsHelp();
                    break;
                case 'R':
                    mind.setDebugLevel(mind.getDebugLevel() | Enums.DEBUG_OPTION_RIGHTS);
                    System.out.println("Rights showed in logs: " + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_RIGHTS) == 0 ? "OFF" : "ON"));
                    break;
                case 'r':
                    mind.setDebugLevel(mind.getDebugLevel() & ~Enums.DEBUG_OPTION_RIGHTS);
                    System.out.println("Rights showed in logs: " + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_RIGHTS) == 0 ? "OFF" : "ON"));
                    break;
                case 'V':
                    mind.setDebugLevel(mind.getDebugLevel() | Enums.DEBUG_OPTION_VALUES);
                    System.out.println("Values of vars and funcs showed in logs: " + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_VALUES) == 0 ? "OFF" : "ON"));
                    break;
                case 'v':
                    mind.setDebugLevel(mind.getDebugLevel() & ~Enums.DEBUG_OPTION_VALUES);
                    System.out.println("Values of vars and funcs showed in logs: " + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_VALUES) == 0 ? "OFF" : "ON"));
                    break;
                case 'S':
                    mind.setDebugLevel(mind.getDebugLevel() | Enums.DEBUG_OPTION_STATUS);
                    System.out.println("Status of domains and trees showed in logs: " + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_STATUS) == 0 ? "OFF" : "ON"));
                    break;
                case 's':
                    mind.setDebugLevel(mind.getDebugLevel() & ~Enums.DEBUG_OPTION_STATUS);
                    System.out.println("Status of domains and trees showed in logs: " + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_STATUS) == 0 ? "OFF" : "ON"));
                    break;
                case 'L':
                    mind.setDebugLevel(mind.getDebugLevel() | Enums.DEBUG_OPTION_RTLOGS);
                    System.out.println("Log showing runtime: " + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_RTLOGS) == 0 ? "OFF" : "ON"));
                    break;
                case 'l':
                    mind.setDebugLevel(mind.getDebugLevel() & ~Enums.DEBUG_OPTION_RTLOGS);
                    System.out.println("Log showing runtime: " + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_RTLOGS) == 0 ? "OFF" : "ON"));
                    break;
                case 't':
                case 'T':
                    KangerTest.test(mind, "set_" + (line.length() > 3 ? line.substring(3) : ""));
                    break;
                case 'm':
                case 'M':
                    System.out.println("Memory status:");
                    System.out.println();

                    System.out.println("Total memory: " + (Runtime.getRuntime().maxMemory() / 1024 / 1024) + " mb");
                    System.out.println("Used memory: " + ((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024) + " mb");
                    System.out.println();

                    if (!user.isClosed()) {
                        System.out.println("Cache size: " + user.getMaxCacheSize());
                        System.out.println("Cache used: " + user.getUsedCacheSize());
                        System.out.println();
                    }
//                                        System.out.println("Database: " + mind.getRights().storedSize());
                    System.out.println("Dictionary: " + mind.getTerms().size());
                    System.out.println("Domains: " + mind.getDomains().size());
                    System.out.println("Functions: " + mind.getFunctions().size());
                    System.out.println("FValues: " + mind.getFValues().size());
                    System.out.println("Predicates: " + mind.getPredicates().size());
                    System.out.println("Rights: " + mind.getRights().size());
                    System.out.println("TValues: " + mind.getTValues().size());
                    System.out.println("TVariables: " + mind.getTValues().size());
                    System.out.println();
                    System.out.println("Hypothesis: " + mind.getHypothesisStore().size());
                    System.out.println("Solutions: " + mind.getSolutions().size());
                    System.out.println("Values: " + mind.getValues().size());
                    break;
            }
        }
    }

    private static void packDatabase(IUser user, Mind mind, Scanner sc) throws Exception {
        if (!user.isClosed()) {
            System.out.printf("Are you sure to pack database " + user.getStorageName() + "? [y/N]? ");
            String s = sc.nextLine().toUpperCase();
            if (!s.isEmpty() && s.charAt(0) == 'Y') {
                user.reindex(new IReactor() {
                    @Override
                    public Object run(Object o) {
                        System.out.println("Processing " + o);
                        return null;
                    }
                });
                System.out.println("Database packed and reindexed");
            }
        } else {
            System.out.println("No database used");
        }
    }

    private static void saveSource(String line, IUser user, Mind mind, Scanner sc) throws Exception {
        if (line.length() == 1) {
            if (line.charAt(0) == 'M') {
                //TODO: Save file
            } else {
                System.out.println(mind.getSourceCode());
            }
        } else {
            Long id = Long.parseLong(line.substring(1).trim());
            System.out.println(formatRightWithComments(mind, id));
            if (line.charAt(0) == 'M') {
                System.out.println("Enter the new comment for ID " + id + ". Two ENTERs ends input:");

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

                mind.getComments().add(id, out.trim());
                System.out.println(formatRightWithComments(mind, id));
            }
        }
    }

    private static void dropDatabase(IUser user, Mind mind, Scanner sc) throws Exception {
        if (!user.isClosed()) {
            System.out.printf("Are you sure to drop database " + user.getStorageName() + "? [y/N]? ");
            String s = sc.nextLine().toUpperCase();
            if (!s.isEmpty() && s.charAt(0) == 'Y') {
                user.remove();
                System.out.println("Database files removed");
            }
        } else {
            System.out.println("No database used");
        }
    }

    private static void useDatabase(String line, IUser user, Mind mind, Scanner sc) throws Exception {
        if (line.split(" ").length == 2) {
            String name = line.split("\\ ")[1].replace(".", Enums.FILE_SEPARATOR);
            user.use(mind, name);
            if (!user.isClosed()) {
                System.out.println("Database used: " + user.getStorageName().replace(Enums.FILE_SEPARATOR, "."));
            } else {
                System.out.println("No database used");
            }
        } else if (!user.isClosed()) {
            System.out.println("Used database " +
                    user.getStorageName()
                            .replace("/", ".")
                            .replace("\\", "."));
        } else {
            System.out.println("No database used");
        }
    }

    private static void closeDatabase(IUser user, Mind mind, Scanner sc) throws Exception {
        if (!user.isClosed()) {
            System.out.printf("Are you sure to close database " + user.getStorageName() + "? [y/N]? ");
            String s = sc.nextLine().toUpperCase();
            if (!s.isEmpty() && s.charAt(0) == 'Y') {
                user.close();
                mind.clear();
            }
        } else {
            System.out.println("No database used");
        }
    }

    private static void clearWorkspace(IUser user, Mind mind, Scanner sc) throws Exception {
        System.out.printf("Are you sure to erase workspace? [y/N]? ");
        String s = sc.nextLine().toUpperCase();
        if (!s.isEmpty() && s.charAt(0) == 'Y') {
            user.clear(mind);
//                                mind.release();
        }
    }

    private static void showSolutions(Mind mind, String line) throws Exception {
        if (line.charAt(0) != 's') {
            if (!mind.getSolutions().isEmpty()) {
                String posStr = line.trim().contains(" ") ? line.split(" ")[1] : null;
                int pos = posStr == null ? -1 : Integer.parseInt(posStr);
                int i = 0;
                for (Right log : mind.getSolutions().getRoot()) {
                    if (++i == pos || pos == -1) {
                        System.out.println(String.format("\tSolution %03d: %s", log.getId(), log.toString()));
                        if (!log.getCauses().isEmpty()) {
                            showCauses(mind, log.getCauses(), 0);
                            System.out.println();
                        }
                        if (pos != -1) {
                            break;
                        }
                    }
                }
            }
        } else {
            showLog(mind, LogMode.SOLVES, false);
        }
    }

    private static void showOptions(Mind mind) {
        System.out.print("Debug level: ");
        switch (mind.getDebugLevel() & 0xFF) {
            case Enums.DEBUG_LEVEL_QUIET:
                System.out.println("QUIET");
                break;
            case Enums.DEBUG_LEVEL_DEBUG:
                System.out.println("DEBUG");
                break;
            case Enums.DEBUG_LEVEL_INFO:
                System.out.println("INFO");
                break;
        }
        System.out.println("Rights showed in logs: " + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_RIGHTS) == 0 ? "OFF" : "ON"));
        System.out.println("Values of vars and funcs showed in logs: " + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_VALUES) == 0 ? "OFF" : "ON"));
        System.out.println("Status of domains and trees showed in logs: " + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_STATUS) == 0 ? "OFF" : "ON"));
        System.out.println("Log showing runtime: " + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_RTLOGS) == 0 ? "OFF" : "ON"));
    }

    public static void showLog(Mind mind, LogMode type, boolean file) {

        if (mind.getLog().size() > 0) {

            BufferedWriter f = null;

            if (file) {
                System.out.print("Save analizer log to file [" + lastLogFile + "]: ");
                String s = new Scanner(System.in).nextLine().toUpperCase();
                if (!s.isEmpty()) {
                    lastLogFile = s;
                }
                try {
                    f = new BufferedWriter(new FileWriter(new File(lastLogFile)));
                } catch (IOException ex) {
                    System.out.printf("ERROR: %s\n", ex);
                    file = false;
                }
            }

            for (LogEntry log : mind.getLog().getRoot()) {
                if (type == LogMode.ALL || log.getType() == type) {
                    if (file) {
                        try {
                            String line = String.format("%s [%8s] %s",
                                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(log.getTime()),
                                    log.getType(),
                                    log.getRecord());
                            f.write(line + "\n");
                        } catch (IOException ex) {
                            System.out.printf("ERROR: %s\n", ex);
                            file = false;
                            System.out.println(log.getRecord());
                        }
                    } else {
                        System.out.println(log.getRecord());
                    }
                }
            }

            if (file) {
                try {
                    f.close();
                    System.out.println("Log to file " + lastLogFile + " saved.");
                } catch (IOException e) {
                }
            }
        }

    }

    public static void showCopyrigt(IUser user) {
        System.out.printf("KANGER III, Version %s\n"
                + "Copyright (C) 1986-%d, Gunn A. Qusnetsov, Dmitry G. Qusnetsov, All rights reserved!\n"
                + "Written by Dmitry G. Qusnetsov. Compiled: %s\n", Version.VERSION_S, Version.YEAR, Version.DATE_S);
    }

    public static void showOptionsHelp() {
        System.out.printf(
                "Available OPTIONS:\n\n"
                        + "   H[ELP]    - Get this message\n"
                        + "\n"
                        + "   R[IGHTS]  - Rights showed in logs\n"
                        + "   V[ALUES]  - Values of vars and funcs showed in logs\n"
                        + "   S[TATUS]  - Status of domains and trees showed in logs\n"
                        + "\n"
                        + "Use UPPERCASE letter for ON and LOWER for OFF.\n"
        );
    }

    public static void showCommonHelp() {
        System.out.printf(
                "Available KEYWORDS:\n\n"
                        + "   H[ELP]    - Get this message\n"
                        + "\n"
                        + "   ?            - Check for Rights Collisions\n"
                        + "   B[ASE]       - View DataBase contents\n"
                        + "   R[IGHTS]     - View compiled-structured Rights list\n"
                        + "   F[UNCS]      - View defined Functions list\n"
                        + "   L[IST]       - View Hypothesis list after last work\n"
                        + "   I[NSERT]     - Insert Hypothesis as right\n"
                        + "   X[PLAIN]     - Show explanation log\n"
                        + "   S[OLVES]     - Show solves list\n"
                        + "   V[ALUES]     - Show values list\n"
                        + "   U[SE] <name> - Create or open existing database\n"
                        + "   C[LOSE]      - Close currently opened database\n"
                        + "   D[ROP]       - Drop currently opened database\n"
                        + "   P[ACK]       - Pack and reindex currently opened database\n"
                        + "   W[IPE]       - Clear workspace and currently opened database\n"
                        + "   O[PTIONS]    - Set workspace options\n"
                        + "\n"
                        + "   G[ET]     - Load Source file from disk\n"
                        + "\n"
                        + "   Q[UIT]    - Quit KANGER\n"
                        + "\n"
                        + "You can use just FIRST letter of keywords.\n"
        );
    }

    public static void showFunctions(Mind mind, boolean showSys) {

        if (!mind.getLibrary().isEmpty()) {
            System.out.printf("Defined functions (%d):\n", mind.getLibrary().size());
            int i = 0;
            for (SysOp op : mind.getLibrary()) {
                if (op.getMode() == LibMode.FUNCTION) {
                    System.out.printf("Function %03d: %s;\n", op.getId(), op.toString());
                    if (showSys && !op.getScripts().isEmpty()) {
                        for (String s : op.asString().split("\n")) {
                            System.out.printf("\t%s\n", s);
                        }
                        System.out.printf("\n");
                    }
                }
            }
        }
    }

    public static void showCauses(Mind mind, Set<Cause> causes, int level) throws Exception {

        String indent = "";
        for (int i = 0; i < level; ++i) {
            indent += "\t";
        }

        //ПРЕДОХРАНИТЕЛЬ
        if (level > 50) {
            System.out.printf("\t\t%s...\n", indent);
            return;
        }

        boolean rightShowed = false;
        for (Cause c : causes) {
            if (!rightShowed) {
                System.out.printf("\t\t%sRight: %s\n", indent, c.getRight(mind).toString().replaceAll("\n", " ").replaceAll("  ", " "));
            }
            System.out.printf("\t\t%sCause: %s\n", indent, c.getDonor().toString(mind)); //c.getArguments()));
            Right r = mind.getRights().find(c.getDonor());
            if (r != null) {
                showCauses(mind, r.getCauses(), level + 1);
            }
        }
    }

    private static void showPredRecurse(Mind mind, List<TVariable> tvars, int tIndex, Domain d, boolean showCauses) throws Exception {
        if (d.isStored(mind)) {
            Right dest = mind.getRights().find(d);
            if (dest != null) {
                if (showCauses) {
                    System.out.println("\t-------------------------------------------");
                }
                System.out.printf("\t%s\n", d.toString());
                if (showCauses && !dest.getCauses().isEmpty()) {
                    showCauses(mind, dest.getCauses(), 0);
                }
            }
        }
    }

    public static void showPred(Mind mind, Predicate p, boolean showCauses) throws Exception {
        Set<Domain> set = p.getSolves();
        if (!set.isEmpty()) {
            System.out.printf("Predicate %s(%d) :\n", p.getName(), p.getRange());
            for (Domain s : set) {
                showPredRecurse(mind, s.getArguments().getTVariables(mind), 0, s, showCauses);
            }
        }
    }

    public static void showBase(Mind mind, boolean showCauses, String param) throws Exception {
        for (Predicate p : mind.getPredicates()) {
            if (!mind.isSystem(p) && (param == null || param.equals(p.getName()))) {
                showPred(mind, p, showCauses);
                System.out.printf("\n");
            }
        }
    }

    public static void showHypo(Mind mind) {
        int i;
        List<Hypothesis> list = mind.getHypothesisStore().getRoot();
        if (list != null && list.size() > 0) {
            System.out.printf("Hypothesis list:\n");
            for (i = 0; i < list.size(); ++i) {
                System.out.printf("\t%3d:\t%s\n", i + 1, list.toArray(new Hypothesis[]{})[i].toString());
            }
            System.out.printf("Use INSERT command for select Hypothesis\n");
        }
    }

    public static void showTree(Mind mind, Right r) throws Exception {
        List<List<String>> net = LogStore.formatTree(mind, r);
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

    public static void showRights(Mind mind, boolean showTree) throws Exception {
        for (Right r : mind.getRights()) {

            System.out.printf("%sRight %03d%s: %s\n",
                    showTree ? "\n --- " : "",
                    r.getId(),
                    (mind.getDebugLevel() & Enums.DEBUG_OPTION_STATUS) != 0 && (r.isGenerated() || r.isQuery() || r.isStored() || r.isDeleted())
                            ? " " +
                            (r.isGenerated() ? "G" : "") +
                            (r.isStored() ? "B" : "") +
                            (r.isQuery() ? "Q" : "") +
                            (r.isDeleted() ? "D" : "")
                            : "",
                    r.getOrig());
            if (showTree || r.getOrig().isEmpty()) {
                int save = mind.getDebugLevel();
                mind.setDebugLevel(save & ~Enums.DEBUG_OPTION_STATUS);
                showTree(mind, r);
                mind.setDebugLevel(save);
            }
        }
    }

    /* Формирует в line строку гипотезы в качестве правила
     */
    public static void makeHypo(Mind mind, Scanner sc) throws Exception {

        System.out.printf("Enter Hypothesis Number: ");
        int i = Integer.parseInt(sc.nextLine());
        if (--i >= mind.getHypothesisStore().size()) {
            System.out.printf("ERROR: Wrong number\n");
        }
        String temp = mind.getHypothesisStore().get(i).toString();
        String h = String.format("!%s;", temp.replace(String.format("%c", Enums.EOLN), ""));

        if (h != null) {
            System.out.println();
            Boolean res = mind.query(h);
            if (res != null && (mind.getDebugLevel() & Enums.DEBUG_OPTION_RTLOGS) == 0) {
                showLog(mind, LogMode.SOLVES, false);
                showLog(mind, LogMode.VALUES, false);
                System.out.println(mind.getLog().getCurrent(LogMode.ANALIZER).getRecord());
            }
        }

    }

    public static boolean loadSource(Mind mind, String sourceDir) throws Exception {
        Scanner scanner = new Scanner(System.in);
        List<File> list = new ArrayList<>();
        File[] dir = new File(sourceDir).listFiles();
        if (dir != null) {
            for (File f : dir) {
                if (!f.isDirectory() && f.getName().contains(".k")) {
                    list.add(f);
                }
            }
        }

        if (list.size() > 0) {
            System.out.println("Files available:");
            int i = 0;
            int n = 1;
            int cnt = 4;
            for (File f : list) {
                System.out.printf("\t%d: %s", n, f.getName());
                if (++i >= cnt) {
                    System.out.println();
                    i = 0;
                }
                ++n;
            }
        }

        System.out.printf("\nEnter file name %s%s: ", list.isEmpty() ? "" : "or file number", mind.getSourceFileName().isEmpty() ? "" : " (" + mind.getSourceFileName() + ")");
        String line = scanner.nextLine();
        File f = null;
        try {
            int ps = Integer.parseInt(line);
            ps -= 1;
            if (ps < list.size()) {
                f = list.get(ps);
            }
        } catch (Exception ex) {
        }

        if (f == null) {
            f = new File(sourceDir + mind.getSourceFileName());
        }
        return loadSourceFile(mind, f);
    }

    //TODO: Нужна проверка на наличие правила в базе на уровне дерева
    public static boolean loadSourceFile(Mind mind, File f) throws Exception {
        try {
            if (f.exists()) {
                final int length = (int) f.length();
                if (length != 0) {
                    char[] cbuf = new char[length];
                    InputStreamReader isr = new InputStreamReader(new FileInputStream(f), "UTF-8");
                    final int read = isr.read(cbuf);
                    StringBuffer buf = new StringBuffer(new String(cbuf).replace("\r\n", "\r"));
                    isr.close();

                    mind.setSourceFileName(f.getName());
                    boolean res = mind.compile(buf.toString());
                    if ((mind.getDebugLevel() & Enums.DEBUG_OPTION_RTLOGS) == 0) {
                        System.out.println(mind.getLog().getCurrent(LogMode.ANALIZER).getRecord());
                    }
                    if (res) {
                        System.out.printf("File %s loaded\n", f.getName());
                    } else {
                        System.out.printf("Use XPLAIN command for analisys\n");
                    }
                    return res;
                } else {
                    System.out.printf("WARNING: File %s is empty\n", f.getName());
                }
            } else {
                System.out.printf("WARNING: File %s not found\n", f.getName());
            }
        } catch (IOException ex) {
            System.out.printf("ERROR: %s\n", ex);
        }
        return false;
    }


    public static String formatRightWithComments(Mind mind, long id) throws Exception {
        String str = String.format(" -- Right %03d: ", id);
        str += Enums.LINE_SEPARATOR;
        Comment c = mind.getComments().get(id);
        if (c != null) {
            str += Enums.LINE_SEPARATOR;
            for (String s : c.getComment().split("\\R")) {
                str += s + Enums.LINE_SEPARATOR;
            }
        }
        Right r = mind.getRights().load(id);
        if (r != null) {
            for (String s : r.getOrig().toString().split("\\R")) {
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
