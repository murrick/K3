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

package org.kanger.compiler;

import org.kanger.Mind;
import org.kanger.User;
import org.kanger.enums.Enums;
import org.kanger.enums.LibMode;
import org.kanger.enums.ParseError;
import org.kanger.enums.Tools;
import org.kanger.exception.ParseErrorException;
import org.kanger.units.Operation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Dmitry G. Quznetsov on 20.05.15.
 */
public class Parser {

    private static final int DIR_LEFT = 0;
    private static final int DIR_RIGHT = 1;
    private static final org.kanger.compiler.Operation[] ops = {

            //Функции

            /*  1 */
            new org.kanger.compiler.Operation("..", "_interval", 1, 2, 0, false, false),
            new org.kanger.compiler.Operation("[", "_set", 1, 0, 0, false, false),

            new org.kanger.compiler.Operation("++", "_inc", 1, 1, 1, false, false),
            new org.kanger.compiler.Operation("--", "_dec", 1, 1, 1, false, false),
            new org.kanger.compiler.Operation("-", "_neg", 1, 1, 1, false, false),
            new org.kanger.compiler.Operation("+", "_val", 1, 1, 1, false, false),
            new org.kanger.compiler.Operation("~~", "_bitnot", 1, 1, 1, false, false),

            /*  2 */
            new org.kanger.compiler.Operation("*", "_mul", 2, 2, 0, false, false),
            new org.kanger.compiler.Operation("/", "_div", 2, 2, 0, false, false),
            new org.kanger.compiler.Operation("%", "_rem", 2, 2, 0, false, false),

            /*  3 */
            new org.kanger.compiler.Operation("+", "_add", 3, 2, 0, false, false),
            new org.kanger.compiler.Operation("-", "_sub", 3, 2, 0, false, false),
            new org.kanger.compiler.Operation("<<", "_bitleft", 3, 2, 0, false, false),
            new org.kanger.compiler.Operation(">>", "_bitright", 3, 2, 0, false, false),
            new org.kanger.compiler.Operation("&", "_bitand", 3, 2, 0, false, false),
            new org.kanger.compiler.Operation("^", "_bitxor", 3, 2, 0, false, false),
            new org.kanger.compiler.Operation("|", "_bitor", 3, 2, 0, false, false),

            //Предикаты

            /* 1 */
            new org.kanger.compiler.Operation("~", "", 4, 1, 1, false, false),

            /* 4 */
            new org.kanger.compiler.Operation(":", "_in", 5, 2, 0, false, false),
            new org.kanger.compiler.Operation(":", "_in", 5, 3, 0, false, false),

            new org.kanger.compiler.Operation("<=", "_le", 5, 2, 0, false, false),
            new org.kanger.compiler.Operation("<", "_lr", 5, 2, 0, false, false),
            new org.kanger.compiler.Operation(">=", "_ge", 5, 2, 0, false, false),
            new org.kanger.compiler.Operation(">", "_gr", 5, 2, 0, false, false),

            /* 5 */
            new org.kanger.compiler.Operation("==", "_eq", 6, 2, 0, false, false),
            new org.kanger.compiler.Operation("=", "_eq", 6, 2, 0, false, false),
            new org.kanger.compiler.Operation("!=", "_ne", 6, 2, 0, false, false),
            new org.kanger.compiler.Operation("<>", "_ne", 6, 2, 0, false, false),

            /* 6 */
            new org.kanger.compiler.Operation(",", "", 7, 2, 0, false, false),
            new org.kanger.compiler.Operation("&&", "&", 7, 2, 0, false, true),

            /* 7 */
            new org.kanger.compiler.Operation("||", "|", 8, 2, 0, false, true),

            /* 8 */
            new org.kanger.compiler.Operation("->", "}", 9, 2, 0, false, true),

            /* 9 */
            new org.kanger.compiler.Operation("@", "", 10, 1, 1, true, false),
            new org.kanger.compiler.Operation("$", "", 10, 1, 1, true, false)

    };

    public static boolean isDelimiter(int ch) {
        return ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n';
    }

    public static boolean isAlpha(int ch) {
        return ch == '_' || (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z');
    }

    public static boolean isNumeric(int ch) {
        return (ch >= '0' && ch <= '9') /*|| ch == '-' || ch == '+'*/ || ch == '.' || ch == 'E' || ch == 'e';
    }

    public static boolean isHex(int ch) {
        return (ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'F') || (ch >= 'a' && ch <= 'f');
    }

    public static String[] extractComments(String text) {
        List<String> list = new ArrayList<>();
        Pattern commentsPattern = Pattern.compile("(//.*?$)|(/\\*.*?\\*/)", Pattern.MULTILINE | Pattern.DOTALL);
        Matcher mt = commentsPattern.matcher(text);
        while (mt.find()) {
            for (int k = 0; k < mt.groupCount(); ++k) {
                String s = mt.group(k + 1);
                if (s != null) {
                    list.add(s);
                }
            }
        }
        String[] ret = new String[list.size()];
        for (int i = 0; i < list.size(); ++i) {
            ret[i] = list.get(i);
        }
        return ret;
    }

    public static Object[] getToken(String ln, int pos) throws ParseErrorException {
        int ch, c, i;
        String line = "";

        if (ln.isEmpty()) {
            return null;
        }

        while (true) {
            if (pos >= ln.length()) {
                return null;
            }
            ch = ln.charAt(pos);
            while (pos < ln.length() && isDelimiter(ch = ln.charAt(pos++))) ;
            if (pos >= ln.length()) {
                if (ch == '?') {
                    line += (char) ch;
                    return new Object[]{line, pos};
                } else {
                    return null;
                }
            }
            c = ln.charAt(pos++);

            if (ch == 0) {
                return null;
            }
            /*
             * Skip comments
             */

            if (ch == '/' && c == '*') {
                do {
                    while (pos < ln.length() && ln.charAt(pos) != '*') ++pos;
                    if (pos < ln.length()) {
                        c = ln.charAt(++pos);
                    } else {
                        throw new ParseErrorException(pos, ParseError.COMMENT);
                    }
                } while (c != '/');
                ++pos;
            } else if (ch == '/' && c == '/') {
                while (pos < ln.length() && ln.charAt(pos) != '\n' && ln.charAt(pos) != '\r') ++pos;
                if (pos > ln.length()) {
                    return null;
                }
            } else {
                break;
            }
        }

        /*
         * Accept string and character expressions
         */
        if (ch == '\"' || ch == '\'') {
            line += (char) ch;
            line += (char) c;
            while (c != ch && pos < ln.length()) {
                for (i = (c == '\\' ? 1 : 0) + 1; i != 0; --i) {
                    if (pos < ln.length()) {
                        c = ln.charAt(pos++);
                        line += (char) c;
                    }
                }
            }
            if (c != ch) {
                throw new ParseErrorException(pos, ParseError.QUOTESR);
            }
        } else if (ch == '{') {
            line += (char) ch;
            line += (char) c;
            int counter = 1;
            while (counter > 0 && pos < ln.length()) {
                c = ln.charAt(pos++);
                line += (char) c;
                if (c == '{') {
                    ++counter;
                } else if (c == '}') {
                    --counter;
                }
            }
            if (counter != 0) {
                throw new ParseErrorException(pos, ParseError.RBRACES);
            }
        } else if (ch == '#' /*&& c != '#'*/) {
            line += (char) ch;
            line += (char) c;
            while (pos < ln.length() && isHex(ch = ln.charAt(pos++))) {
                line += (char) ch;
            }
            --pos;
        } else {
            line += (char) ch;

            /* double character operators */
            for (i = 0; i < ops.length; ++i) {
                if (ops[i].getName().length() == 2 && ops[i].getName().charAt(0) == ch && ops[i].getName().charAt(1) == c) {
                    line += (char) c;
                    break;
                }
            }
            if (i == ops.length) {

                /* single character operations */
                for (i = 0; i < ops.length; ++i) {
                    if (ops[i].getName().length() == 2 && ops[i].getName().charAt(0) == ch) {
                        --pos;
                        break;
                    }
                }

                if (i == ops.length) {

                    --pos;
                    if (isAlpha(ch)) {
                        while (pos < ln.length() && (isAlpha(ch = ln.charAt(pos++)) || (isNumeric(ch) && ch != '.'))) {
                            line += (char) ch;
                        }
                        --pos;
                    } else if (isNumeric(ch)) {
                        int p = ch;
                        while (pos < ln.length() && isNumeric(ch = ln.charAt(pos++))) {
                            //Блок на две точки
                            if (p == ch && p == '.') {
                                --pos;
                                line = line.substring(0, line.length() - 1);
                                break;
                            } else {
                                line += (char) ch;
                                p = ch;
                            }
                        }
                        --pos;
                    }
                }
            }
        }
        return new Object[]{line, pos};
    }

    /*
     * --------------------------------------------------------
     *
     * Parsing expression recursively.
     * Builds expression tree with priority and
     * direction correcting. For example:
     *                         +
     *      a * b + c;	->   /   \
     *                     *       c
     *					 /   \
     *				   a       b
     *
     * Returns root of expression tree.
     *
     * If construction like a(x) found in top level, or
     * operator definition for glob_ops has substitution - this
     * expression marked as PREDICATE. Inside predicate braces this
     * construction or operators oper_ops with substitutions
     * marked as FUNCTION.
     * --------------------------------------------------------
     */
    private static PTree parse(String ln, int pos /*, int mode*/) throws ParseErrorException {
        String line = "";
        PTree p, q, r, root, wasq;
        int i, term;

        term = 0;
        root = wasq = null;
        do {
            Object[] t = getToken(ln, pos);
            if (t == null) {
                if (root != null) {
                    root.setPos(pos);
                } else {
                    throw new ParseErrorException(pos, ParseError.EMPTY);
                }
                return root;
            }

            line = (String) t[0];
            pos = (Integer) t[1];

            if (line.isEmpty()) {
                continue;
            }

            if (line.charAt(0) == Enums.RB || line.charAt(0) == ']') {
                if (root != null) {
                    root.setPos(pos);
                }
                return root;
            }

            /* Save previous node in 'last' and make new node for
             * every token. Finds token in operations database and if
             * presend - fills information fields. If not - set priority
             * for node as 0
             */
            p = new PTree();
            p.setNext(DIR_LEFT);

            for (i = 0; i < ops.length; ++i) {
                if (line.equals(ops[i].getName())) {

                    if (term == 0 && ops[i].getRange() > 1) {
                        throw new ParseErrorException(pos, ParseError.EMPTY);
                    } else if (term != 0 && ops[i].getRange() == 1 && !ops[i].getName().equals("~") && !ops[i].getName().equals("-") && !ops[i].getName().equals("+")) {
                        throw new ParseErrorException(pos, ParseError.EMPTY);
                    } else if (term == 0 || ops[i].getRange() > 1) {

                        /* WAS QUANTOR flag and pointer. Need for correct
                         * definition non-standard quantor syntax
                         */
                        wasq = ops[i].getName().charAt(0) == Enums.PQN || ops[i].getName().charAt(0) == Enums.AQN ? p : null;

                        p.setPrior(ops[i].getPrior());
                        p.setDir(ops[i].getDir());
                        p.setRange(ops[i].getRange());
                        p.setNext(ops[i].getRange() > 1 && !ops[i].isPost() ? DIR_RIGHT : DIR_LEFT);

                        if (term == 0 && ops[i].getName().equals("[")) {
                            p.setNext(DIR_RIGHT);
                        }
                        /* System predicates or functions */
                        if (ops[i].getSubst().length() > 0) {
                            if (!ops[i].isRepl()) {
                                p.setSystem(true);
                            }
                            p.setName(ops[i].getSubst());
                        }
                        break;
                    }
                }
            }
            if (p.getName() == null) {
                p.setName(line);
            }

            /* Check () Calculate
             * recursively. Detect predicate. Define 'term' flag == 1 if this
             * is just a name, and == 0 if this is
             * databased operation.
             */
            if (p.getName().charAt(0) == Enums.LB || p.getName().equals("_set")) {
                if (p.getName().equals("_set")) {
                    PTree x = new PTree();
                    x.setNext(DIR_LEFT);
                    x.setLeft(p);
                    x.setName("(");
                    p = x;
                } else {
                    p.setPrior(0);
                }

                p.setRight(parse(ln.trim(), pos /*, term + mode*/));
                if (p.getRight() != null) {
                    pos = p.getRight().getPos();
                } else {
                    ++pos;
                }
                if (pos + 1 == ln.length() && ln.charAt(pos) != Enums.EOLN) {
                    throw new ParseErrorException(pos, ParseError.EOLN);
                } else if (ln.charAt(pos - 1) != Enums.RB && ln.charAt(pos - 1) != ']') {
                    throw new ParseErrorException(pos, ParseError.BRACKET);
                }

                if (term == 0) {
                    term = 1;
                }

            } else if (p.getName().charAt(0) == Enums.NOT) {
                Object[] nextToken = getToken(ln, pos);
                if (nextToken != null) {
                    String nextLine = (String) nextToken[0];
                    if (!nextLine.isEmpty() && (nextLine.charAt(0) == Enums.PQN || nextLine.charAt(0) == Enums.AQN)) {
                        p.setPrior(17);
                    }
                }
            } else {
                term = p.getPrior() == 0 ? 1 : 0;
            }

            /* Inserting new node (maybe with sub-tree) into
             * main expression tree. Function scan tree on 'right'
             * branch and recognize node with priorirty value
             * <= then inserting node priority. If fount - chech
             * for operation direction. If direction is R->L then
             * skips all node with same priority value.
             * Inserting node after node which found.
             */

            if (root != null) {

                /* Find point for insertion.
                 */
                for (r = q = root; q != null; q = q.getNext() == DIR_LEFT ? q.getLeft() : q.getRight()) {
                    if (q.getPrior() <= p.getPrior()) {
                        break;
                    }
                    r = q;
                }
                if (p.getDir() != 0) {
                    while (q != null && q.getPrior() == p.getPrior()) {
                        r = q;
                        q = q.getNext() == DIR_LEFT ? q.getLeft() : q.getRight();
                    }
                }

                /* Insert new node
                 */
                if (q == root) {
                    if (wasq == null) {
                        p.setLeft(root);
                    } else {
                        p.setRight(root);
                    }
                    root = p;
                } else {
                    if (q != null) {
                        if (wasq == null) {
                            p.setLeft(q);
                        } else {
                            p.setRight(q);
                        }
                    }
                    if (r.getNext() == DIR_LEFT) {
                        r.setLeft(p);
                    } else {
                        r.setRight(p);
                    }
                }

                /* Correction for quantor expression. Just up one level
                 * and switch direction
                 */
                if (term != 0 && wasq != null) {
                    p = wasq;
                    p.setNext(DIR_RIGHT);
                    wasq = null;
                    term = 0;
                }

                // Обработка интервалов
                if (Enums.INTERVALS.containsKey(p.getName().toLowerCase())
                        && p.getLeft() != null
                        && !p.getLeft().getName().isEmpty()
                        && Tools.isInt(p.getLeft().getName())
                        && p.getRight() == null) {
                    p.setName(p.getLeft().getName() + " " + p.getName());
                    if (p.getLeft().getLeft() != null && Tools.isPeriod(p.getLeft().getLeft().getName())) {
                        p.setName(p.getLeft().getLeft().getName() + " " + p.getName());
                    }
                    p.setLeft(null);
                }
                /* If node is first in tree -
                 * just place'em as root of tree.
                 */
            } else {
                root = p;
            }
        } while (pos < ln.length());
        return root;
    }

    public static Operation implement(String ln, Mind mind) throws Exception {
        String line = "";
        boolean waitParams = false;
        boolean waitScript = false;
        int pos = 1;
        Operation f = ((User) mind.getUser()).getUdf();
        f.setMind(mind);
        do {
            Object[] t = getToken(ln, pos);
            if (t == null) {
                break;
            }
            pos = (Integer) t[1];
            line = (String) t[0];

            if (line.isEmpty()) {
                continue;
            }

            if (line.charAt(0) == Enums.EOLN) {
                break;
            }

            if (line.charAt(0) == Enums.LB) {
                waitParams = true;
            } else if (line.charAt(0) == Enums.RB) {
                waitParams = false;
                waitScript = true;
            } else if (line.charAt(0) == Enums.COMMA) {
                //
            } else if (waitParams) {
                f.getParams().add(line);
            } else if (waitScript) {
                if (line.charAt(0) != '{') {
                    throw new ParseErrorException(pos, ParseError.RBRACES);
                }
                f.getScripts().add(line.substring(0, line.length() - 1).substring(1));
            } else {
                f.setName(line);
            }

        } while (pos < ln.length());

        f.setMode(LibMode.FUNCTION);
        f.setRange(f.getParams().size());
        f.getParams().add(f.getName());
        return f;
    }

    private static PTree squeeze(PTree t) {
        if (t == null) {
            return null;
        }
        t.setLeft(squeeze(t.getLeft()));
        t.setRight(squeeze(t.getRight()));
        if (t.getName().charAt(0) == Enums.LB && t.getLeft() == null) {
            return squeeze(t.getRight());
        } else {
            return t;
        }
    }

    public static PTree parser(String ln) throws ParseErrorException {
        if (!ln.isEmpty() && ln.trim().charAt(ln.trim().length() - 1) != Enums.EOLN) {
            throw new ParseErrorException(ln.trim().length() - 1, ParseError.EOLN);
        }
        return squeeze(parse(ln.trim(), 0 /*, 0*/));
    }

    public static org.kanger.compiler.Operation getOp(String o, int range) {
        for (org.kanger.compiler.Operation op : ops) {
            if (op.getSubst().equals(o) && (op.getRange() == 0 || op.getRange() == range)) {
                return op;
            }
        }
        return null;
    }

//    public static int skipSpaces(String line, int pos) {
//        while (pos < line.length() && isDelimiter(line.charAt(pos))) {
//            ++pos;
//        }
//        return pos;
//    }

}
