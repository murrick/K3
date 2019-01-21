package kanger;

//import jline.ConsoleReader;

import kanger.compiler.Parser;
import kanger.compiler.SysOp;
import kanger.enums.Enums;
import kanger.enums.LibMode;
import kanger.enums.LogMode;
import kanger.enums.Tools;
import kanger.exception.ParseErrorException;
import kanger.exception.RuntimeErrorException;
import kanger.primitives.Cause;
import kanger.primitives.Hypotese;
import kanger.primitives.LogEntry;
import kanger.test.KangerTest;
import kanger.units.*;

import java.io.*;
import java.util.*;

//import java.awt.*;
//import java.awt.datatransfer.Clipboard;
//import java.awt.datatransfer.StringSelection;

/**
 * Created by murray on 28.05.15. $Author: murray $
 */
public class Screen {

    public static boolean LINE_EDITOR_ENABLE
            = System.getProperties().getProperty("kanger.enable.line.editor") != null
            && System.getProperties().getProperty("kanger.enable.line.editor").equals("true");

    public static void session(User user) {
        boolean stop = false;
        boolean again = false;
        Mind mind = user.getMind();
        String lastQuery = "";

//        ConsoleReader reader = null;
//        if (LINE_EDITOR_ENABLE) {
//            try {
//                reader = new ConsoleReader();
//            } catch (IOException e) {
//                LINE_EDITOR_ENABLE = false;
//            }
//        }

//        String ss = "!@x (a(x) || b(x)) -> c(x);";
//        for(int i=0; i<ss.length(); ++i){
//            try {
//                Runtime.getRuntime().exec("input keyevent " + ss.charAt(i));
//            } catch (IOException e) {} 
//        }
        showCopyrigt(mind);

        while (!stop) {
            String line = "";
            try {
//                if (LINE_EDITOR_ENABLE && reader != null) {
//                    try {
//                        Character c = 0;
//                        System.out.printf("\n: ");
//                        line = reader.readLine(c);
//                    } catch (IOException e) {
//                        LINE_EDITOR_ENABLE = false;
//                    }
//                }
//                if (!LINE_EDITOR_ENABLE || reader == null) {
                System.out.printf("\n: ");
                if (again) {
                    line = lastQuery;
                    System.out.printf("%s\n", line);
                    again = false;
                } else {
                    line = new Scanner(System.in).nextLine();
                }
//                }
                if (line == null) {
                    line = "";
                }
                if (line.length() > 0) {
                    switch (line.toUpperCase().charAt(0)) {
                        case 'A':
                            again = true;
                            break;
                        case 'Q':
//                            if (checkChg(mind)) {
                            stop = true;
//                            }
                            break;
                        case 'H':
                            showCommonHelp();
                            break;
                        case 'R': {
                            Mind m = mind;
//                            int pos = 0;
//                            while (line.substring(pos).contains("..")) {
//                                int ps = line.indexOf("..");
//                                line = line.substring(0, ps) + line.substring(ps + 2);
//                                if (m.getParent() != null) {
//                                    m = m.getParent();
//                                }
//                            }
//                            line.replace("/", "");
                            showRights(m, line.charAt(0) != 'r');
                        }
                        break;
                        case 'B': {
                            Mind m = mind;
//                            int pos = 0;
//                            while (line.substring(pos).contains("..")) {
//                                int ps = line.indexOf("..");
//                                line = line.substring(0, ps) + line.substring(ps + 2);
//                                if (m.getParent() != null) {
//                                    m = m.getParent();
//                                }
//                            }
//                            line.replace("/", "");
                            showBase(m, line.charAt(0) != 'b', line.trim().contains(" ") ? line.split(" ")[1] : null);
                        }
                        break;
                        case 'F':
                            showFunctions(mind, line.charAt(0) != 'f');
                            break;
                        case 'L':
                            showHypo(mind);
                            break;
                        case 'V':
                            showLog(mind, LogMode.VALUES);
                            break;
                        case 'S':
                            showLog(mind, LogMode.SOLVES);
                            break;
                        case 'X':
                            showLog(mind, LogMode.ALL);
                            break;
                        case 'T':
                            showTValues(mind);
                            break;
//                    case 'A':
//                        lastQuery = savedQuery;
//                        break;
                        case 'K':
                            killRight(mind);
                            break;
//                        case 'T':
//                            showText(mind);
//                            break;
                        case 'I':
                            String h = makeHypo(mind);
                            if (h != null) {
//                                System.out.println(": " + h);
                                System.out.println();
                                Boolean res = mind.query(h, false);
                                if (res != null && (mind.getDebugLevel() & Enums.DEBUG_OPTION_RTLOGS) == 0) {
                                    showLog(mind, LogMode.SOLVES);
                                    showLog(mind, LogMode.VALUES);
                                    System.out.println(mind.getLog().getCurrent(LogMode.ANALIZER).getRecord());
                                }
//                            lastQuery = savedQuery;
                            }
                            break;
                        case 'C': {
                            System.out.printf("Are you sure to clear workspace? [y/N]? ");
                            String s = new Scanner(System.in).nextLine().toUpperCase();
                            if (!s.isEmpty() && s.charAt(0) == 'Y') {
                                mind.clear();
//                                mind.release();
                            }
                        }
                        break;
//                        case 'E': {
//                            System.out.printf("Are you sure to clear working memory? [y/N]? ");
//                            String s = new Scanner(System.in).nextLine().toUpperCase();
//                            if (!s.isEmpty() && s.charAt(0) == 'Y') {
//                                mind.getText().delete(0, mind.getText().length());
//                                mind.clear();
//                            }
//                        }
//                        break;
                        case 'P':
                            saveSource(mind);
                            break;
                        case 'G':
                            loadSource(mind);
                            break;
//                        case 'Z':
//                            saveCompiled(mind);
//                            break;
                        case 'U':
                            if (line.split(" ").length == 2) {
                                user.use(line.split("\\ ")[1]);
                                System.out.println("Used database " + user.getStorageName());
                            } else if (!user.isClosed()) {
                                System.out.println("Used database " + user.getStorageName());
                            } else {
                                System.out.println("No database used");
                            }
                            break;
                        case 'D':
                            if (!user.isClosed()) {
                                System.out.printf("Are you sure to drop database " + user.getStorageName() + "? [y/N]? ");
                                String s = new Scanner(System.in).nextLine().toUpperCase();
                                if (!s.isEmpty() && s.charAt(0) == 'Y') {
                                    user.remove();
                                    System.out.println("Database files removed");
                                }
                            } else {
                                System.out.println("No database used");
                            }
                            break;

                        case 'O':
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
                                        KangerTest.test(user, "set_" + (line.length() > 3 ? line.substring(3) : ""));
                                        break;
                                }
                            }
                            break;
                        case Enums.SUC:
                            lastQuery = line;
                        case Enums.ANT:
                        case Enums.INS:
                        case Enums.DEL:
                        case Enums.WIPE:
//                            if (!Tools.isComplete(line, 0)) {
//                                incomplete = line;
//                                line = "";
//                            } else {
//                            if (LINE_EDITOR_ENABLE) {
//                                reader.getHistory().getHistoryList().remove(0);
//                                reader.getHistory().addToHistory(line);
//                            } else {
                            // StringSelection selec = new StringSelection(line);
                            // Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                            // clipboard.setContents(selec, selec);
//                            }

                            int pos = 0;
                            Object[] t = null;
                            while ((t = Tools.extractLine(line, pos)) != null) {
                                pos = (int) t[1];
                                String ln = (String) t[0];

                                Boolean res = mind.query(ln, false);
                                if ((mind.getDebugLevel() & Enums.DEBUG_OPTION_RTLOGS) == 0) {
                                    System.out.println(mind.getLog().getCurrent(LogMode.ANALIZER).getRecord());
                                    if (res != null) {
                                        showLog(mind, LogMode.SOLVES);
                                        showLog(mind, LogMode.VALUES);
                                    }
                                    if (res == null) {
                                        showHypo(mind);
                                    }
                                }
                            }
//                            }
                            break;
                        case Enums.FOO:
//                            if (!Tools.isComplete(line, 0)) {
//                                incomplete = line;
//                                line = "";
//                            } else {
                            SysOp op = (SysOp) mind.compileLine(line);
                            System.out.printf("SUCCESS: Library updated: =%s;\n", op.toString());
//                            }
                            break;
                        default:
                            System.out.printf("ERROR: Unknown Instruction\n");
                    }

                } else if (line.isEmpty()) {
                    showCopyrigt(mind);
                }
            } catch (ParseErrorException ex) {
                String x = ex.toString();
                int pos = Integer.parseInt(x.split("@")[0]);
                String msg = x.split("@")[1];
                System.out.println("ERROR: " + msg);
                System.out.println(line);
                while (pos-- > 0) {
                    System.out.print(" ");
                }
                System.out.println("^");
                line = "";
//                incomplete = "";
            } catch (RuntimeErrorException e) {
                System.out.println(e.toString());
                //e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        try {
            user.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("KANGER III Session closed");

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

    public static void showLog(Mind mind, LogMode type) {
        if (mind.getLog().size() > 0) {
            for (LogEntry log : mind.getLog().getRoot()) {
                if (type == LogMode.ALL || log.getType() == type) {
                    System.out.println(log.getRecord());
                }

            }
//            System.out.println();
        }
    }

    //    public static void showSolves(Mind context) {
//        if (context.getSolutions().size() > 0) {
//            System.out.println("Solves:");
//            int i = 0;
//            for (String log : (List<String>) context.getSolutions().getRoot()) {
//                System.out.println(String.format("\tSolve %03d: %s", ++i, log));
//            }
//            System.out.println();
//        }
//    }
//
//    public static void showValues(Mind context) {
//        if (context.getValues().size() > 0) {
//            System.out.println("Values:");
//            int i = 0;
//            for (String log : (List<String>) context.getValues().getRoot()) {
//                System.out.println(String.format("\tSolve %03d: %s", ++i, log));
//
//            }
//            System.out.println();
//        }
//    }
    public static void showCopyrigt(Mind mind) {
        System.out.printf("KANGER III, Version %s\n"
                + "Copyright (C) 1986-%d, Gunn A. Qusnetsov, Dmitry G. Qusnetsov, All rights reserved!\n"
                + "Written by Dmitry G. Qusnetsov. Compiled: %s\n", Version.VERSION_S, Version.YEAR, Version.DATE_S);
//        System.out.printf("Context ID: %s\n", mind.getContextIdString());
    }

    //    public static int fixInsertion(contextAbstract context) {
//        int i = 0;
//        List<String> list = (List<String>) context.getHypotesisStore().getRoot();
//        if (list.size() > 0) {
//            System.out.printf("Predicated added:\n");
//            for (i = 0; i < list.size(); ++i) {
//                context.getText().append(context.getHypotesisStore().createCVar(i));
//                context.getText().append("\r");
//                context.setChanged(true);
//                System.out.printf("  %3d:\t%s\n", i, context.getHypotesisStore().createCVar(i));
//            }
//        }
//        return i;
//    }
    public static int showLine(StringBuffer c, int pos) {
        pos = Parser.skipSpaces(c.toString(), pos);
        while (pos < c.length() && c.charAt(pos) != Enums.EOLN) {
            if (c.charAt(pos) == '\t') {
                System.out.print("    ");
            } else {
                System.out.printf("%c", c.charAt(pos));
            }
            ++pos;
        }
        if (c.charAt(pos) == Enums.EOLN) {
            System.out.print(";");
            ++pos;
        }
        System.out.print("\n");
        return pos;
    }

    public static int skipLine(StringBuffer line, int pos) {
        while (pos < line.length() && line.charAt(pos) != '\n' && line.charAt(pos) != '\r') {
            ++pos;
        }
        return Parser.skipSpaces(line.toString(), pos);
    }

    //    public static void showText(Mind context) {
//        StringBuffer c = context.getText();
//        int pos = 0;
//        int i = 0;
//        while (pos < c.length()) {
//            int p = Parser.skipSpaces(c.toString(), pos);
//            if (p < c.length() && c.charAt(p) != Enums.REM) {
//                System.out.printf("%3d: ", ++i);
//                pos = showLine(c, pos);
////                if (i > 0 && i % 19 == 0) {
////                    System.out.printf("\nPress ENTER to continue\n\n");
////                    new Scanner(System.in).next();
////                }
//            } else {
//                pos = skipLine(c, pos);
//            }
//        }
//    }
//
//    public static boolean checkChg(Mind context) {
//        if (context.isChanged()) {
//            System.out.printf("WARNING: Program text was changed. Are you sure [y/N] ? ");
//            String s = new Scanner(System.in).nextLine();
//            return s.toLowerCase().contains("y");
//        } else {
//            return true;
//        }
//    }
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
                        + "   ?         - Check for Rights Collisions\n"
                        + "   B[ASE]    - View DataBase contents\n"
                        + "   R[IGHTS]  - View compiled-structured Rights list\n"
                        + "   F[UNCS]   - View defined Functions list\n"
                        + "   K[ILL]    - Remove right\n"
                        + "   L[IST]    - View Hypothesis list after last work\n"
                        + "   I[NSERT]  - Insert Hypothesis as right\n"
                        + "   A[GAIN]   - Repeat last question\n"
                        + "   X[PLAIN]  - Show explanation log\n"
                        + "   S[OLVES]  - Show solves list\n"
                        + "   V[ALUES]  - Show values list\n"
                        //                        + "   TEXT    - Show source text\n"
                        + "   C[LEAR]   - Clear workspace\n"
                        + "   O[PTIONS] - Set workspace options\n"
                        //                        + "   ERASE   - Clear all working memory\n"
                        + "\n"
                        //                        + "   PUT     - Save Source file\n"
                        + "   G[ET]     - Load Source file from disk\n"
                        + "   Z[IP]     - Save compiled code\n"
                        + "   U[NZIP]   - Load compiled code from file\n"
                        + "\n"
                        + "   Q[UIT]    - Quit KANGER\n"
                        + "\n"
                        + "You can use just FIRST letter of keywords.\n"
        );
    }

    //    public static void showFunc(FArg f) {
//        System.out.printf(formatFunc(f));
//    }

    public static void showTValues(Mind mind) {
        for (Right r : mind.getRights()) {
            List<TVariable> tvs = r.getTVariables(true);
            if (!tvs.isEmpty()) {
                System.out.printf("Right %03d: %s;\n", r.getId(), r.getOrig());
                for (TVariable tv : tvs) {
                    Iterator<TValue> iterator = mind.getTValues().iterator(tv);
                    if (iterator.hasNext()) {
                        do {
                            System.out.println("\t" + tv.getVarName() + "=" + iterator.next().getValue());
                        } while (iterator.hasNext());
                    }
                }
            }
        }
    }

    public static void showFunctions(Mind mind, boolean showSys) {

        if (mind.getLibrary().getRoot() != null) {
            System.out.printf("Defined functions:\n");
            int i = 0;
            for (SysOp op : mind.getLibrary().getRoot().values()) {
                if (op.getMode() == LibMode.FUNCTION) {
                    System.out.printf("Function %03d: %s;\n", ++i, op.toString());
                    if (showSys && !op.getScripts().isEmpty()) {
                        for (String s : op.asString().split("\n")) {
                            System.out.printf("\t%s\n", s);
                        }
                        System.out.printf("\n");
                    }
                }
            }
        }
//        if (mind.getFunctions().getRoot() != null) {
//            System.out.printf("Defined functions:\n");
//            for (FunctionDescriptor f = (FunctionDescriptor) mind.getFunctions().getRoot(); f != null; f = f.getNext()) {
//                System.out.printf("\t%s(%d);\n", f.getName(), f.getRange());
//            }
//        }
    }

    //    public static void showDomain(Domain d) {
//        System.out.printf(formatDomain(d));
//    }
    //    puts_subs(DOMAIN*d) {
//        char s[ MAXLINE];
//
//        sputs_subs(d, s);
//        printf("\t%s\n", s);
//    }
//
//    /*
//     * Returns 0 if all parameters defined
//     * or count of undefined
//     */
//    public static String formatPred(Predicate p, Solution s) {
//        String str = String.format("%c%s(", s.isAntc() ? Enums.ANT : Enums.SUC, p.getName());
//        for (int i = 0; i < p.getRange(); ++i) {
//            if (s.createCVar(i) != null && s.createCVar(i).getTerm().getType() == Enums.T_STRING) {
//                str += "\"";
//            }
//            str += String.format("%s", s.createCVar(i) != null ? s.createCVar(i).getTerm().getName() : "_");
//            if (s.createCVar(i) != null && s.createCVar(i).getTerm().getType() == Enums.T_STRING) {
//                str += "\"";
//            }
//
//            if (i + 1 < p.getRange()) {
//                str += String.format("%c", Enums.COMMA);
//            }
//        }
//        str += ");";
//        return str;
//    }
    //
    public static void showCauses(Mind mind, Domain d, int level) {
        //ПРЕДОХРАНИТЕЛЬ
        if (level > 20) {
            return;
        }

        String indent = "";
        for (int i = 0; i < level; ++i) {
            indent += "\t";
        }

        Record dest = mind.getDatabase().find(d.getPredicate(), d.isAntc(), d.getArguments());
        if (dest != null && !dest.getCauses().isEmpty()) {

            boolean rightShowed = false;
            for (Cause c : dest.getCauses()) {
                Domain dst = mind.getDomains().get(c.getDstId());
                Domain src = mind.getDomains().get(c.getSrcId());
                if(!rightShowed) {
                    System.out.printf("\t\t%sRight: %s\n", indent, dst.getRight().toString().replaceAll("\n", " ").replaceAll("  ", " "));
                }
                System.out.printf("\t\t%sCause: %s\n", indent, src.toString(c.getArguments()));
                showCauses(mind, src, level + 1);
            }
        }
    }


    private static void showPredRecurse(Mind mind, List<TVariable> tvars, int tIndex, Domain d, boolean showCauses) throws RuntimeErrorException {
//        if (tIndex >= tvars.size()) {
        if (d.isStored()) {
//                d.recalculate();
            if (showCauses) {
                System.out.println("\t-------------------------------------------");
            }
            System.out.printf("\t%s\n", d.toString());
            if (showCauses) {
                showCauses(mind, d, 0);
            }
        }
//        } else {
//            TVariable t = tvars.get(tIndex);
//            TValue v = t.rewind();
//            if (v != null) {
//                do {
////                    if (t.getSrcSolve() != null && t.getSrcSolve().getPredicate().getId() != d.getPredicate().getId()) {
////                        mind.getSubstituted().createTVar(t);
////                    if (!d.isDest()) {
//                    mind.getTValues().set(t, v);
//                    showPredRecurse(mind, tvars, tIndex + 1, d, showCauses);
////                    }
//                } while ((v = t.next(v)) != null);
//            } else {
//                showPredRecurse(mind, tvars, tIndex + 1, d, showCauses);
//            }
//        }
    }

    public static void showPred(Mind mind, Predicate p, boolean showCauses) throws RuntimeErrorException {
        System.out.printf("Predicate %s(%d) :\n", p.getName(), p.getRange());
        Set<Domain> set = p.getSolves();
        if (set.isEmpty()) {
            System.out.printf("\tHas not solves\n");
        } else {
            for (Domain s : set) {
//                if (!s.isDestFor()) {
//                    mind.getSubstituted().clear();
                showPredRecurse(mind, s.getArguments().getTVariables(true), 0, s, showCauses);
//                }
            }
        }
    }

    //
//
//    puts_loged_preds() {
//        PRED * p;
//        SOLVE * s;
//
//        for (p = Preds; p; p = p -> next)
//            for (s = p -> solve; s; s = s -> next)
//                if (s -> loged)
//                    puts_pred(p, s);
//    }
//
//
//    puts_loged() {
//        printf("Got from predicates:\n");
//        puts_loged_preds();
//        printf("Using right:\n");
//        view_line(Curr_right -> line);
//        printf("--- STEP ----------------------------------\n");
//        if (getch() == 27) {
//            printf("Explanation mode now is %s\n", "OFF");
//            Puts_log = 0;
//        }
//    }
//
//
//    puts_hone(PRED*p, int*cnt) {
//        SOLVE * s;
//        char line[ MAXLINE];
//
//        printf("Predicat %s(%d) :\n", p -> name, p -> range);
//        for (s = p -> hypo; s; s = s -> next) {
//            if (s -> cuted) continue;
//            make_hone(1, p, s, line);
//            printf("  %3d:\t.%s\n", * cnt, line + 1);
//            ++( * cnt);
//        }
//    }
//
//
    public static void showBase(Mind mind, boolean showCauses, String param) throws RuntimeErrorException {
        for (Predicate p : mind.getPredicates()) {
            if (!p.getSolves().isEmpty() && !mind.isSystem(p) && (param == null || param.equals(p.getName()))) {
                showPred(mind, p, showCauses);
                System.out.printf("\n");
            }
        }
    }

    //
    public static void showHypo(Mind mind) {
        int i;
        List<Hypotese> list = mind.getHypotesisStore().getRoot();
        if (list != null && list.size() > 0) {
            System.out.printf("Hypothesis list:\n");
            for (i = 0; i < list.size(); ++i) {
                System.out.printf("\t%3d:\t%s\n", i + 1, list.toArray(new Hypotese[]{})[i].toString());
            }
            System.out.printf("Use INSERT command for select Hypotesis\n");
        }
    }

    //
//
    public static List<List<String>> formatTree(Mind mind, Right r) {
//        int save = mind.getDebugLevel();
//        mind.setDebugLevel(mind.getDebugLevel() & ~Enums.DEBUG_OPTION_VALUES);
        List<List<String>> list = new ArrayList<>();
        int depth = 0;
        for (Tree t : r.getTree()) {
            List<String> v = new ArrayList<>();
            v.add((t.getRight().isGenerated() ? "G" : "") + (t.isClosed() ? "C" : "") + (t.isUsed() ? "U" : "") + (t.isReady() ? "R" : ""));
            list.add(v);
            int len = 0;
            for (Domain d : t.getSequence()) {
                String s = d.toString(); // + (d.isUsed() ? " *" : "");
                len = Math.max(len, s.length());
                v.add(s);
            }
            depth = Math.max(depth, v.size());
            for (int i = 0; i < v.size(); ++i) {
                String s = v.get(i);
                while (s.length() < len) {
                    s += " ";
                }
                v.set(i, s);
            }
        }
        for (List<String> v : list) {
//            if(!v.isEmpty()) {
            int len = v.get(0).length();
            String s = " ";
            while (s.length() < len) {
                s += " ";
            }
            while (v.size() < depth) {
                v.add(s);
            }
//            }
        }
//        mind.setDebugLevel(save);
        return list;
    }

    public static void showTree(Mind mind, Right r) {
        List<List<String>> net = formatTree(mind, r);
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

    public static void showTreeWithValues(Mind mind, Right r, SortedSet<TVariable> tset) {
        if (tset.isEmpty()) {
            showTree(mind, r);
        } else {
            TVariable t = tset.last(); //.get(tIndex);
            Iterator<TValue> iterator = mind.getTValues().iterator(t);
            if (iterator.hasNext()) {
                do {
                    TValue v = iterator.next();
                    mind.getTValues().set(t, v);
                    showTreeWithValues(mind, r, tset.headSet(t));
                } while (iterator.hasNext());
            } else {
                showTreeWithValues(mind, r, tset.headSet(t));
            }
        }
    }

    public static void showRights(Mind mind, boolean showTree) {
//        int i = 0;
        for (Right r : mind.getRights()) {
            System.out.printf("%sRight %03d%s: %s\n",
                    showTree ? "\n --- " : "",
                    r.getId(),
                    r.isGenerated() || r.isQuery() ? " " +
                            (r.isGenerated() ? "G" : "") +
                            (r.isQuery() ? "Q" : "") : "",
                    r.getOrig());
            if (showTree || r.getOrig().isEmpty()) {
                if ((mind.getDebugLevel() & Enums.DEBUG_OPTION_RVALUES) == 0) {
                    int save = mind.getDebugLevel();
                    mind.setDebugLevel(save & ~Enums.DEBUG_OPTION_STATUS);
                    showTree(mind, r);
                    mind.setDebugLevel(save);
                } else {
                    SortedSet<TVariable> tset = new TreeSet<>();
                    tset.addAll(r.getTVariables(true));
                    showTreeWithValues(mind, r, tset);
                }
            }
        }
    }
//
//
//    puts_ptr(pos)
//
//    int pos;
//
//    {
//        while (pos) {
//            putchar(' ');
//            --pos;
//        }
//        printf("^\n");
//    }
//
//
//    puts_err(line)
//
//    char*line;
//
//    {
//        char*s;
//
//        printf("\n");
//        for (s = line;*s &&*s != EOLN;
//        ++s)
//        putchar( * s);
//        if (*s == EOLN)
//        putchar(';');
//        printf("\n");
//        puts_ptr(_err_pos - line);
//        switch (_err_code) {
//            case 0:
//                s = "Success";
//                break;
//            case 1:
//                s = "Right brackets mismatch";
//                break;
//            case 2:
//                s = "Must be ! or ? symbol";
//                break;
//            case 3:
//                s = "Semicolon required";
//                break;
//            case 4:
//                s = "Misplaced ~ symbol";
//                break;
//            case 5:
//                s = "Misplaced left bracket";
//                break;
//            case 6:
//                s = "Misplaced quantor symbol";
//                break;
//            case 7:
//                s = "Misplaced infix symbol";
//                break;
//            case 8:
//                s = "Empty term";
//                break;
//            case 9:
//                s = "Quantor variable mismatch";
//                break;
//            case 10:
//                s = "Symbol inside predicat";
//                break;
//            case 11:
//                s = "Misplaced comma";
//                break;
//            case 12:
//                s = "Misplaced term";
//                break;
//            case 13:
//                s = "Ivalid predicat name";
//                break;
//            default:
//                s = "System error";
//        }
//        printf("Syntax ERROR %d : %s\n", _err_code, s);
//    }

//    public static int skipSpaces(StringBuffer line, int pos) {
//        if (pos < line.length()) {
//            while (pos < line.length() && line.charAt(pos) <= ' ') {
//                ++pos;
//            }
//        }
//        return pos;
//    }

    /* Формирует в line строку гипотезы в качестве правила
     */
    public static String makeHypo(Mind mind) {
//        PRED *p;
//        SOLVE *s;
//        int antc;
//        int i;
//        char temp[MAXLINE];

        System.out.printf("Enter Hypothesis Number: ");
        int i = Integer.parseInt(new Scanner(System.in).nextLine());
        if (--i >= mind.getHypotesisStore().size()) {
            System.out.printf("ERROR: Wrong number\n");
            return null;
        }
        String temp = mind.getHypotesisStore().get(i).toString();
        return String.format("!%s;", temp.replace(String.format("%c", Enums.EOLN), ""));
    }

    public static void killRight(Mind mind) {
        System.out.printf("Enter Right Number: ");
        int id = Integer.parseInt(new Scanner(System.in).nextLine());
        Right r = mind.getRights().get(id);
        if (r == null) {
            System.out.printf("ERROR: Wrong number\n");
            return;
        }
        System.out.println(r.getOrig());
        System.out.printf("Are you sure to remove right " + id + " [y/N]? ");
        String s = new Scanner(System.in).nextLine();
        if (s.charAt(0) == 'Y' || s.charAt(0) == 'y') {
//TODO: Нужно реализовать удаление правила
//            mind.removeInsertionRight(r);
            mind.setChanged(true);
        }
    }

    public static boolean saveSource(Mind context) {
        Scanner scanner = new Scanner(System.in);
        if (context.getSourceFileName().isEmpty()) {
            context.setSourceFileName("context.k");
        }


        System.out.printf("Enter file name for save (%s): ", context.getSourceFileName());
        String line = scanner.nextLine();
        if (!line.isEmpty()) {
            context.setSourceFileName(line);
        } else {
            line = context.getSourceFileName();
        }

        if (new File(line).exists()) {
            System.out.print("WARNING: File already exists. Overwrite [y/N] ? ");
            String ch = scanner.nextLine();
            if (!(ch.startsWith("y") || ch.startsWith("Y"))) {
                return false;
            }
        }

        try {
            BufferedWriter f = new BufferedWriter(new FileWriter(new File(line)));
            f.write("test");
            f.flush();
            f.close();
            context.setChanged(false);

            System.out.printf("File %s saved\n", line);
            return true;
        } catch (IOException ex) {
            System.out.printf("ERROR: %s\n", ex);
            return false;
        }
    }

    public static boolean loadSource(Mind mind) throws ParseErrorException, RuntimeErrorException {
        Scanner scanner = new Scanner(System.in);
//        if (checkChg(mind)) {
        List<String> list = new ArrayList<>();
        File[] dir = new File(System.getProperty("user.dir")).listFiles();
        if (dir != null) {
            for (File f : dir) {
                if (!f.isDirectory() && f.getName().contains(".k")) {
                    list.add(f.getName());
                }
            }
        }

        if (list.size() > 0) {
            System.out.println("Files available:");
            int i = 0;
            int n = 1;
            int cnt = 4;
            for (String name : list) {
                System.out.printf("\t%d: %s", n, name);
                if (++i >= cnt) {
                    System.out.println();
                    i = 0;
                }
                ++n;
            }
        }

//                if (System.getProperty("os.name").toLowerCase().indexOf("win") >= 0) {
//                    Runtime.getRuntime().exec("dir /p *.k");
//                } else {
//                    Runtime.getRuntime().exec("ls *.k");
//                }
        System.out.printf("\nEnter file name %s%s: ", list.isEmpty() ? "" : "or file number", mind.getSourceFileName().isEmpty() ? "" : " (" + mind.getSourceFileName() + ")");
        String line = scanner.nextLine();

        try {
            int ps = Integer.parseInt(line);
            ps -= 1;
            if (ps < list.size()) {
                line = list.get(ps);
            }
        } catch (Exception ex) {
        }

        if (line.trim().isEmpty()) {
            line = mind.getSourceFileName();
        }
        return loadSourceFile(mind, line);
//        }
//        return false;
    }

    //TODO: Нужна проверка на наличие правила в базе на уровне дерева
    public static boolean loadSourceFile(Mind mind, String line) throws ParseErrorException, RuntimeErrorException {
        try {
            File f = new File(line);
            if (f.exists()) {
                final int length = (int) f.length();
                if (length != 0) {
                    char[] cbuf = new char[length];
                    InputStreamReader isr = new InputStreamReader(new FileInputStream(f), "UTF-8");
                    final int read = isr.read(cbuf);
                    StringBuffer buf = new StringBuffer(new String(cbuf).replace("\r\n", "\r"));
                    isr.close();

                    mind.setSourceFileName(line);
                    boolean res = mind.compile(buf.toString());
                    if ((mind.getDebugLevel() & Enums.DEBUG_OPTION_RTLOGS) == 0) {
                        System.out.println(mind.getLog().getCurrent(LogMode.ANALIZER).getRecord());
                    }
                    if (res) {
                        System.out.printf("File %s loaded\n", line);
                    } else {
                        System.out.printf("Use XPLAIN command for analisys\n");
                    }
                    return res;
                } else {
                    System.out.printf("WARNING: File %s is empty\n", line);
                }
            } else {
                System.out.printf("WARNING: File %s not found\n", line);
            }
        } catch (IOException ex) {
            System.out.printf("ERROR: %s\n", ex);
        }
        return false;
    }

}
