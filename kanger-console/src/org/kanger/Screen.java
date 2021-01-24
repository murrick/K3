package org.kanger;

import org.kanger.enums.Enums;
import org.kanger.enums.LibMode;
import org.kanger.enums.LogMode;
import org.kanger.enums.Tools;
import org.kanger.exception.CommandErrorException;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

/**
 * Created by Dmitry G. Qusnetsov on 28.05.15. $Author: murray $
 */
public class Screen {

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


    public static String getSourceDir(IUser user) throws IOException {
        return user.getProperty("sources.dir");
//        String sourcesDir = user.getProperty("user.dir") + "SRC";
//        if (user.containsKey("sources.dir")) {
//            sourcesDir = user.getProperty("sources.dir");
//        }
//        if (!sourcesDir.isEmpty() && !sourcesDir.endsWith("/") && !sourcesDir.endsWith("\\")) {
//            sourcesDir += Enums.FILE_SEPARATOR;
//            Files.createDirectories(Paths.get(sourcesDir));
//        }
//        return sourcesDir;
    }

    public static void session(Mind mind) throws Exception, ClassNotFoundException, RuntimeErrorException {
        boolean stop = false;


        String lastQuery = "";

        sc = new Scanner(System.in);

//        try {
//            Global.getUdf();
//            System.out.println("UDF module loaded");
//        } catch (RuntimeErrorException e) {
//        }
//
//        try {
//            user.getData();
//            System.out.println("DB module loaded: " + user.getData().getDescription());
//        } catch (RuntimeErrorException e) {
//        }

//        showCopyrigt();
//        System.out.print("login: ");
//        String login = sc.nextLine();
//        String password = new String(System.console().readPassword("password: "));
//
//        IUser user = new User(login, password);


        while (!stop) {
            String line = "";
            try {
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
                        case 'Q':   // QUIT
                            stop = true;
                            break;
                        case 'H':   // HELP
                            showCommonHelp();
                            break;
                        case 'R':   // RULES
                            showRules(mind, line);
                            break;
                        case 'B':   // BASES
                            showBase(mind, line);
                            break;
                        case 'F':   // FUNCTIONS
                            showFunctions(mind, line);
                            break;
                        case 'L':   // LIST
                            showHypo(mind);
                            break;
                        case 'A':   // append
                            makeHypo(mind, line, sc);
                            break;
                        case 'V':   // VALUES
                            showLog(mind, LogMode.VALUES, null, sc);
                            break;
                        case 'S':   // SOLUTIONS
                            showSolutions(mind, line);
                            break;
                        case 'X':   // XPLAIN
                            showExplanation(mind, LogMode.ALL, line, sc);
                            break;
                        case 'E':   // ERASE
                            clearWorkspace(mind, sc);
                            break;
                        case 'G':   // GET
                            loadSourceFile(mind, loadSource(line, mind, sc));
                            break;
                        case 'P':   // PUT
                            saveSource(line, mind, sc);
                            break;
                        case 'C':   // CLOSE
                            closeDatabase(mind, sc);
                            break;
                        case 'U':   // USE
                            useDatabase(line, mind, sc);
                            break;
                        case 'D':   // DROP
                            dropDatabase(mind, sc);
                            break;
                        case 'I':   // INDEX
                            packDatabase(mind, sc);
                            break;
                        case 'O':   // OPTIONS
                            options(line, mind, sc);
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
                    showCopyrigt();
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
            } catch (CommandErrorException ex) {
                System.err.println(ex.toString());
            } catch (RuntimeErrorException ex) {
                System.err.println(ex.toString());
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }

        }
        try {
            mind.getUser().close();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        System.out.println("KANGER III Session closed");

    }

    private static void processFunction(String line, Mind mind) throws Exception {
        mind.setCompliedLine(line);
        SysOp op = (SysOp) mind.compileLine(line, false, null);
        if (!op.isDeleted()) {
            System.out.printf("SUCCESS: Updated function: %s;\n", op.toString());
        } else {
            System.out.printf("SUCCESS: Deleted function: %s;\n", op.toString());
        }
    }

    private static void processQuery(String line, Mind mind) throws Exception {
        int pos = 0;
        Object[] t = null;
        while ((t = Tools.extractLine(line, pos)) != null) {
            pos = (int) t[1];
            String ln = (String) t[0];

            Boolean res = mind.query(ln);
            if (!lastComments.isEmpty() && mind.getAcceptedRule() != null) {
                mind.getComments().add(mind.getAcceptedRule().getId(), lastComments);
                lastComments = "";
            }
            if ((mind.getDebugLevel() & Enums.DEBUG_OPTION_RTLOGS) == 0) {
                System.out.println(mind.getLog().getCurrent(LogMode.ANALYZER).getRecord());
                if (res != null) {
                    showLog(mind, LogMode.SOLVES, null, null);
                    showLog(mind, LogMode.VALUES, null, null);
                }
                if (res == null && line.trim().charAt(0) == Enums.SUC) {
                    showHypo(mind);
                }
            }
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
        }
        System.out.println("Values of vars and funcs showed in logs: " + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_VALUES) == 0 ? "NO" : "YES"));
        System.out.println("Status of domains and trees showed in logs: " + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_STATUS) == 0 ? "NO" : "YES"));
        System.out.println("Log showing runtime: " + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_RTLOGS) == 0 ? "NO" : "YES"));
    }


    private static void options(String line, Mind mind, Scanner sc) throws Exception {
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

                    if (!mind.getUser().isClosed()) {
                        System.out.println("Cache size: " + mind.getUser().getMaxCacheSize());
                        System.out.println("Cache used: " + mind.getUser().getUsedCacheSize());
                        System.out.println();
                    }
//                                        System.out.println("Database: " + mind.getRights().storedSize());
                    System.out.println("Dictionary: " + mind.getTerms().size());
                    System.out.println("Domains: " + mind.getDomains().size());
                    System.out.println("Functions: " + mind.getFunctions().size());
                    System.out.println("FValues: " + mind.getFValues().size());
                    System.out.println("Predicates: " + mind.getPredicates().size());
                    System.out.println("Rules: " + mind.getRules().size());
                    System.out.println("TValues: " + mind.getTValues().size());
                    System.out.println("TVariables: " + mind.getTValues().size());
                    System.out.println();
                    System.out.println("Hypothesis: " + mind.getHypothesisStore().size());
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
                    System.out.println("Status of domains and trees showed in logs: " + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_STATUS) == 0 ? "NO" : "YES"));
                    break;
                case 'L':
                    if (line.split(" ").length > 2) {
                        mind.setDebugLevel(line.split(" ")[2].toUpperCase().charAt(0) == 'Y'
                                ? mind.getDebugLevel() | Enums.DEBUG_OPTION_RTLOGS
                                : mind.getDebugLevel() & ~Enums.DEBUG_OPTION_RTLOGS);
                    }
                    System.out.println("Log showing runtime: " + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_RTLOGS) == 0 ? "NO" : "YES"));
                    break;
                case 'T':
                    KangerTest.test(mind, "set_" + (line.length() > 3 ? line.substring(3) : ""));
                    break;
            }
        }
    }

    private static void saveSource(String line, Mind mind, Scanner sc) throws Exception {
        String fname = null;
        if (line.split(" ").length == 1) {
            System.out.println(mind.getSourceCode());

            System.out.printf("Save source code to file? [Y/n]? ");
            String s = sc.nextLine().toUpperCase();
            if (s.isEmpty() || s.charAt(0) == 'Y') {
                fname = mind.getSourceFileName();
                System.out.print("Enter file name. Space for cancel (" + mind.getSourceFileName() + "): ");
                s = sc.nextLine();
                if (!s.isEmpty()) {
                    if (s.trim().isEmpty()) {
                        fname = null;
                    } else {
                        fname = s;
                    }
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
                    mind.getComments().add(id, out.trim());
                    System.out.println(formatRightWithComments(mind, id));
                }
            } catch (Exception ex) {
                fname = line.split(" ")[1].trim();
            }
        }

        if (fname != null && !fname.isEmpty()) {
            File f = new File(getSourceDir(mind.getUser()) + fname);
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(f))) {
                bw.write(mind.getSourceCode());
                mind.setSourceFileName(fname);
                System.out.println("Source file " + fname + " saved.");
            }
        }

    }

    private static void packDatabase(Mind mind, Scanner sc) throws Exception {
        if (!mind.getUser().isClosed()) {
            System.out.printf("Are you sure to pack database " + mind.getUser().getStorageName() + "? [y/N]? ");
            String s = sc.nextLine().toUpperCase();
            if (!s.isEmpty() && s.charAt(0) == 'Y') {
                mind.getUser().reindex(new IReactor() {
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

    private static void dropDatabase(Mind mind, Scanner sc) throws Exception {
        if (!mind.getUser().isClosed()) {
            System.out.printf("Are you sure to drop database " + mind.getUser().getStorageName() + "? [y/N]? ");
            String s = sc.nextLine().toUpperCase();
            if (!s.isEmpty() && s.charAt(0) == 'Y') {
                mind.getUser().remove();
                System.out.println("Database files removed");
            }
        } else {
            System.out.println("No database used");
        }
    }

    private static void useDatabase(String line, Mind mind, Scanner sc) throws Exception {
        if (line.split(" ").length == 2) {
            String name = line.split("\\ ")[1].replace(".", Enums.FILE_SEPARATOR);
            mind.getUser().use(mind, name);
            if (!mind.getUser().isClosed()) {
                System.out.println("Database used: " + mind.getUser().getStorageName().replace(Enums.FILE_SEPARATOR, "."));
                System.out.println("Rules: " + mind.getRules().size() + ", Predicates: " + mind.getPredicates().size() + ", Dictionary: " + mind.getTerms().size() + ", UDF: " + mind.getFunctions().size());
            } else {
                System.out.println("No database used");
            }
        } else if (!mind.getUser().isClosed()) {
            System.out.println("Used database " + mind.getUser().getStorageName().replace(Enums.FILE_SEPARATOR, "."));
            System.out.println("Rules: " + mind.getRules().size() + ", Predicates: " + mind.getPredicates().size() + ", Dictionary: " + mind.getTerms().size() + ", UDF: " + mind.getFunctions().size());
        } else {
            List<String> list = (List<String>) mind.getUser().getStoragesList();
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
                line = sc.nextLine();
                try {
                    int ps = Integer.parseInt(line);
                    ps -= 1;
                    if (ps < list.size()) {
                        line = list.get(ps);
                    }
                } catch (Exception ex) {
                }
                if (!line.isEmpty()) {
                    line = line.replace(".", Enums.FILE_SEPARATOR);
                    mind.getUser().use(mind, line);
                    if (!mind.getUser().isClosed()) {
                        System.out.println("Database used: " + mind.getUser().getStorageName().replace(Enums.FILE_SEPARATOR, "."));
                        System.out.println("Rules: " + mind.getRules().size() + ", Predicates: " + mind.getPredicates().size() + ", Dictionary: " + mind.getTerms().size() + ", UDF: " + mind.getFunctions().size());
                    } else {
                        System.out.println("No database used");
                    }
                } else {
                    System.out.println("No database used");
                }
            } else {
                System.out.println("No database used");
            }
        }
    }

    private static void closeDatabase(Mind mind, Scanner sc) throws Exception {
        if (!mind.getUser().isClosed()) {
            System.out.printf("Are you sure to close database " + mind.getUser().getStorageName() + "? [y/N]? ");
            String s = sc.nextLine().toUpperCase();
            if (!s.isEmpty() && s.charAt(0) == 'Y') {
                mind.getUser().close();
                mind.clear();
            }
        } else {
            System.out.println("No database used");
        }
    }

    private static void clearWorkspace(Mind mind, Scanner sc) throws Exception {
        System.out.printf("Are you sure to erase workspace? [y/N]? ");
        String s = sc.nextLine().toUpperCase();
        if (!s.isEmpty() && s.charAt(0) == 'Y') {
            mind.getUser().clear(mind);
//                                mind.release();
        }
    }

    private static void showSolutions(Mind mind, String line) throws Exception {
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
                for (Rule log : mind.getSolutions().getRoot()) {
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

    public static void showLog(Mind mind, LogMode type, File fi, Scanner sc) throws IOException {

        if (mind.getLog().size() > 0) {
            BufferedWriter f = null;

            try {
                if (fi != null) {
                    f = new BufferedWriter(new FileWriter(fi));
                }

                for (LogEntry log : mind.getLog().getRoot()) {
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

    public static void showExplanation(Mind mind, LogMode type, String line, Scanner sc) throws IOException {

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
                    f = new File(mind.getUser().getProperty("user.dir") + fname);
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
                + "Copyright (C) 1986-%d, Dmitry G. Qusnetsov, All rights reserved!\n"
                + "Compiled: %s\n", Version.VERSION_S, Version.YEAR, Version.DATE_S);
    }

    public static void showOptionsHelp() {
        System.out.printf(
                "Available options:\n\n"
                        + "   options help             - Get this message\n"
                        + "\n"
                        + "   options debug [yes|no]   - Show debug information in logs\n"
                        + "   options values [yes|no]  - Values of vars and funcs showed in logs\n"
                        + "   options status [yes|no]  - Status of domains and trees showed in logs\n"
                        + "   options log [yes|no]     - Show runtime log during analysis\n"
                        + "   options memory           - Show memory status\n"
                        + "\n"
                        + "You can use just first letters of keywords.\n");
    }

    public static void showCommonHelp() {
        System.out.printf(
                "Available keywords:\n\n"
                        + "INFORMATION:\n"
                        + "   help                    - Get this message\n"
                        + "   rules                   - View rules list\n"
                        + "      rile <n>               rule with ID = n\n"
                        + "      rules tree             rules list with compiled trees\n"
                        + "      rule tree <n>          rule with compiled tree for rule with ID = n\n"
                        + "   base                    - Show predicate-split statements list\n"
                        + "      base predicates        predicates only list with IDs\n"
                        + "      base <n>               statements list for predicate with ID = n\n"
                        + "      base tree              statements list with inference tree\n"
                        + "      base tree <n>          inference tree for statement with ID = n\n"
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
                        + "      append <n> [yes]       hypothesis with index = n into antecedent\n"
                        + "      append <n> no          hypothesis with index = n into succedent\n"
                        + "\n"
                        + "SOURCE FILES:\n"
                        + "   get [<fn>]              - Load source file with name fn from disk\n"
                        + "   put                     - Show and save source file to disk\n"
                        + "      put <n>                set comment for rule with ID = n\n"
                        + "      put <fn>               save source file with name fn\n"
                        + "\n"
                        + "DATABASE:\n"
                        + "   use [<bn>]              - Create, open database with name bn or show name of currently opened\n"
                        + "   close                   - Close currently opened database\n"
                        + "   drop                    - Drop currently opened database\n"
                        + "   index                   - Pack and reindex currently opened database\n"
                        + "\n"
                        + "SYSTEM:\n"
                        + "   ?                       - Check program for collisions\n"
                        + "   options [<options>]     - Show or change workspace options. Use \"options help\" for details\n"
                        + "   erase                   - Erase workspace\n"
                        + "   quit                    - Quit KANGER console\n"
                        + "\n"
                        + "You can use just first letters of keywords.\n");
    }

    public static void showFunctions(Mind mind, String line) throws CommandErrorException {
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
            for (SysOp op : mind.getLibrary()) {
                if (!op.isDeleted() && op.getMode() == LibMode.FUNCTION && (id == -1 || id == op.getId())) {
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

    public static void showCauses(Mind mind, Set<Cause> causes, int level) throws Exception {

        String indent = "";
        for (int i = 0; i <= level; ++i) {
            indent += "\t";
        }

        //ПРЕДОХРАНИТЕЛЬ
        if (level > 50) {
            System.out.printf("\t%s...\n", indent);
            return;
        }

        boolean ruleShowed = false;
        for (Cause c : causes) {
            if (!ruleShowed) {
                System.out.printf("\t%sRule:  %s\n", indent, c.getRule(mind).toString().replaceAll("\n", " ").replaceAll("  ", " "));
            }
            System.out.printf("\t%sCause: %s\n", indent, c.getDonor().toString(mind)); //c.getArguments()));
            Rule r = mind.getRules().find(c.getDonor());
            if (r != null) {
                showCauses(mind, r.getCauses(), level + 1);
            }
        }
    }

    private static void showPredRecurse(Mind mind, Domain d, boolean showCauses) throws Exception {
        if (d.isStored(mind)) {
            Rule dest = mind.getRules().find(d);
            if (dest != null) {
                if (showCauses) {
                    System.out.println("\t-------------------------------------------");
                }
                System.out.printf("\t%03d: %s\n", dest.getId(), dest.toString());
                if (showCauses && !dest.getCauses().isEmpty()) {
                    showCauses(mind, dest.getCauses(), 0);
                }
            }
        }
    }

    public static void showPred(Mind mind, Predicate p, boolean showCauses) throws Exception {
//        Set<Domain> set = p.getSolves();
//        if (!set.isEmpty()) {
        System.out.printf("Predicate %s(%d) :\n", p.getName(), p.getRange());
        for (Domain s : p.getSolves()) {
            showPredRecurse(mind, s, showCauses);
        }
//        }
    }

    public static void showBase(Mind mind, String line) throws Exception {
        long id = -1;
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
                            throw new CommandErrorException();
                        }
                }
            }
        }
        if (id != -1) {
            if (!tree) {
                Predicate p = mind.getPredicates().load(id);
                if (p != null) {
                    if (preds) {
                        System.out.printf("Predicate %03d: %s", p.getId(), p.toString());
                    } else {
                        showPred(mind, p, tree);
                    }
                }
            } else {
                Rule dest = mind.getRules().load(id);
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
            for (Predicate p : mind.getPredicates()) {
                if (!p.isDeleted() && !mind.isSystem(p) && !p.getSolves().isEmpty()) {
                    if (preds) {
                        found = true;
                        System.out.printf("Predicate %03d: %s;\n", p.getId(), p.toString());
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

    public static void showHypo(Mind mind) {
        int i;
        List<Hypothesis> list = mind.getHypothesisStore().getRoot();
        if (list != null && list.size() > 0) {
            System.out.printf("Hypothesis list:\n");
            for (i = 0; i < list.size(); ++i) {
                System.out.printf("\t%03d:\t%s\n", i + 1, list.toArray(new Hypothesis[]{})[i].toString());
            }
//            System.out.printf("Use APPEND command for select Hypothesis\n");
        } else {
            System.out.printf("No hypothesis found\n");
        }
    }

    public static void showTree(Mind mind, Rule r) throws Exception {
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

    public static void showRules(Mind mind, String line) throws Exception {

        long id = -1;
        boolean tree = false;
        for (String s : line.split(" ")) {
            if (!s.trim().isEmpty()) {
                switch (s.trim().toUpperCase().charAt(0)) {
                    case 'R':
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

        boolean found = false;
        for (Rule r : mind.getRules()) {
            if (!r.isDeleted() && (id == -1 || r.getId() == id)) {
                found = true;
                System.out.printf("%sRule %03d%s: %s\n",
                        tree ? " --- " : "",
                        r.getId(),
                        (mind.getDebugLevel() & Enums.DEBUG_OPTION_STATUS) != 0 && (r.isGenerated() || r.isQuery() || r.isStored() || r.isDeleted())
                                ? " " +
                                (r.isGenerated() ? "G" : "") +
                                (r.isStored() ? "B" : "") +
                                (r.isQuery() ? "Q" : "") +
                                (r.isDeleted() ? "D" : "")
                                : "",
                        r.getOrig());
                if (tree || r.getOrig().isEmpty()) {
                    int save = mind.getDebugLevel();
                    mind.setDebugLevel(save & ~Enums.DEBUG_OPTION_STATUS);
                    showTree(mind, r);
                    mind.setDebugLevel(save);
                    System.out.printf("\n");
                }

                if (id != -1) {
                    break;
                }
            }
        }
        if (!found) {
            System.out.printf("No rules selected\n");
        }
    }

    /* Формирует в line строку гипотезы в качестве правила
     */
    public static void makeHypo(Mind mind, String line, Scanner sc) throws Exception {
        int i = -1;
        boolean antc = true;
        if (line.split(" ").length >= 2) {
            try {
                i = Integer.parseInt(line.split(" ")[1]);
                if (line.split(" ").length > 2) {
                    antc = line.split(" ")[2].trim().toUpperCase().charAt(0) == 'Y';
                }
            } catch (Exception e) {
                throw new CommandErrorException();
            }
        }

        if(i == -1) {
            System.out.printf("Enter Hypothesis Number: ");
            String n = sc.nextLine();
            try {
                i = Integer.parseInt(n);
            } catch (Exception e) {
                throw new CommandErrorException();
            }
            System.out.printf("Statement is true or false [yes/no]? ");
            n = sc.nextLine();
            antc = n.trim().toUpperCase().charAt(0) == 'Y';

        }
        --i;
        try {
            mind.getHypothesisStore().get(i).setAntc(antc);
            String temp = mind.getHypothesisStore().get(i).toString();
            String h = String.format("!%s;", temp.replace(String.format("%c", Enums.EOLN), ""));

            if (h != null) {
                System.out.println("Statement: " + h);
                Boolean res = mind.query(h);
                if (res != null && (mind.getDebugLevel() & Enums.DEBUG_OPTION_RTLOGS) == 0) {
                    showLog(mind, LogMode.SOLVES, null, null);
                    showLog(mind, LogMode.VALUES, null, null);
                    System.out.println(mind.getLog().getCurrent(LogMode.ANALYZER).getRecord());
                }
            }
        } catch (Exception e) {
            throw new CommandErrorException();
        }
    }

    public static File loadSource(String line, Mind mind, Scanner sc) throws Exception {
        File f = null;
        if (line.split(" ").length == 1) {
            List<File> list = new ArrayList<>();
            File[] dir = new File(getSourceDir(mind.getUser())).listFiles();
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

            System.out.printf("\nEnter file name %s%s: ", list.isEmpty() ? "" : "or file number", mind.getSourceFileName().isEmpty() ? "" : " (" + mind.getSourceFileName() + ")");
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
            f = new File(getSourceDir(mind.getUser()) + line.split(" ")[1]);
        }

        return f;
    }

    //TODO: Нужна проверка на наличие правила в базе на уровне дерева
    public static boolean loadSourceFile(Mind mind, File f) throws Exception {
        try {

            if (f == null) {
                f = new File(getSourceDir(mind.getUser()) + mind.getSourceFileName());
            }

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
                        System.out.println(mind.getLog().getCurrent(LogMode.ANALYZER).getRecord());
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
        if (c != null && !c.getComment().isEmpty()) {
            for (String s : c.getComment().split("\\R")) {
                str += s + Enums.LINE_SEPARATOR;
            }
        }
        Rule r = mind.getRules().load(id);
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
