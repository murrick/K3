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
import org.kanger.enums.LibMode;
import org.kanger.exception.ParseErrorException;
import org.kanger.units.Operation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Синтаксический front-end исторического языка KANGER.
 *
 * <p><strong>Архитектурная роль.</strong> {@code Parser} выполняет
 * tokenization, распознаёт operator signatures, строит mutable дерево
 * {@link Leaf} с учётом precedence/arity и нормализует внешние обозначения в
 * внутренние operation names. Он не владеет {@link Mind}, не регистрирует
 * semantic units и не выполняет логический вывод; созданный AST передаётся
 * {@link Compiller}.</p>
 *
 * <p><strong>Грамматика.</strong> Таблица {@code ops} является компактным
 * контрактом языка: она задаёт aliases, внутренние имена, priority, arity,
 * direction, postfix и replacement semantics для arithmetic functions,
 * predicates, conjunction/disjunction, implication и quantifiers. При
 * tokenization действует longest textual match, а overloaded operator
 * уточняется ожидаемой arity. Порядок и численные priorities являются frozen
 * compatibility surface.</p>
 *
 * <p><strong>Tokenization.</strong> {@link #nextToken(String, Token)} сохраняет
 * source positions, пропускает delimiters, различает block/line comments,
 * quoted literals, nested functional blocks, identifiers и numeric forms.
 * Последовательность {@code ..} отделяется от decimal scanning. Незакрытые
 * comments, quotes и brackets завершаются {@link ParseErrorException} с
 * исходной позицией.</p>
 *
 * <p><strong>Построение AST.</strong> Внутренний parser вставляет operator
 * nodes по historical priority rules, обрабатывает quantifier variable,
 * parentheses и set syntax, расширяет arity операции {@code _in} через comma
 * tree и отвергает misplaced term, operation или quantifier. Метод
 * {@code squeeze} удаляет grouping nodes, переносит function name и вычисляет
 * фактический range аргументов. Получившееся дерево отражает именно порядок,
 * который ожидает {@link Compiller#compileLine(Leaf, boolean, String, boolean,
 * java.util.Queue)}.</p>
 *
 * <p><strong>UDF declaration.</strong> {@link #implement(String, Mind, Token)}
 * использует настроенный в {@link User} prototype {@link Operation}, заполняет
 * name, params и script blocks и маркирует результат как function. Parser лишь
 * разбирает declaration; publication и invocation принадлежат Library и
 * Calculator.</p>
 *
 * <p><strong>Comments.</strong> {@link #extractComments(String)} является
 * отдельным lexical utility и возвращает исходные comment fragments без
 * semantic interpretation.</p>
 *
 * <p><strong>Concurrency.</strong> Основные методы статичны и не хранят
 * глобального mutable parse state. Переданный {@link Token}, AST и UDF
 * prototype принадлежат одному вызову; совместное использование mutable Token
 * или Operation между потоками требует внешней сериализации.</p>
 *
 * @see Compiller
 * @see Leaf
 * @see Token
 */
public class Parser {

    private static final Op[] ops = {

            //Функции

            /*  1 */
            new Op("..", "_interval", 2, 2, 0, false, false),
            new Op("[", "_set", 1, 1, 0, false, false),

            new Op("++", "_inc", 1, 1, 1, false, false),
            new Op("--", "_dec", 1, 1, 1, false, false),
            new Op("-", "_neg", 1, 1, 1, false, false),
            new Op("+", "_val", 1, 1, 1, false, false),
            new Op("~~", "_bitnot", 1, 1, 1, false, false),

            /*  2 */
            new Op("*", "_mul", 2, 2, 0, false, false),
            new Op("/", "_div", 2, 2, 0, false, false),
            new Op("%", "_rem", 2, 2, 0, false, false),

            /*  3 */
            new Op("+", "_add", 3, 2, 0, false, false),
            new Op("-", "_sub", 3, 2, 0, false, false),
            new Op("<<", "_bitleft", 3, 2, 0, false, false),
            new Op(">>", "_bitright", 3, 2, 0, false, false),
            new Op("&", "_bitand", 3, 2, 0, false, false),
            new Op("^", "_bitxor", 3, 2, 0, false, false),
            new Op("|", "_bitor", 3, 2, 0, false, false),

            //Предикаты

            /* 1 */
            new Op("~", "", 4, 1, 1, false, false),
            new Op("!", "", 4, 1, 1, false, false),

            /* 4 */
            new Op(":", "_in", 5, 2, 0, false, false),
            new Op(":", "_in", 5, 3, 0, false, false),

            new Op("<=", "_le", 5, 2, 0, false, false),
            new Op("<", "_lr", 5, 2, 0, false, false),
            new Op(">=", "_ge", 5, 2, 0, false, false),
            new Op(">", "_gr", 5, 2, 0, false, false),

            /* 5 */
            new Op("==", "_eq", 6, 2, 0, false, false),
            new Op("=", "_eq", 6, 2, 0, false, false),
            new Op("!=", "_ne", 6, 2, 0, false, false),
            new Op("<>", "_ne", 6, 2, 0, false, false),
            new Op("~=", "_ne", 6, 2, 0, false, false),

            /* 6 */
            new Op(",", "", 7, 2, 0, false, false),
            new Op("&&", "&", 7, 2, 0, false, true),

            /* 7 */
            new Op("||", "|", 8, 2, 0, false, true),

            /* 8 */
            new Op("->", "}", 9, 2, 0, false, true),

            /* 9 */
            new Op("@", "", 10, 1, 1, true, false),
            new Op("$", "", 10, 1, 1, true, false)

    };

    private static Op getOp(String line, int pos, int range) {
        Op found = null;
        for (Op x : ops) {
            if (line.startsWith(x.name, pos)) {
                if (found == null || found.name.length() < x.name.length()) {
                    found = x;
                }
            }
        }
        if (found != null && range > 0 && found.range != range) {
            for (Op x : ops) {
                if (x != found && x.range == range && x.name.equals(found.name)) {
                    found = x;
                }
            }
        }
        return found;
    }

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

    public static Token nextToken(String line, Token current) throws ParseErrorException {
        if (current == null) {
            current = new Token();
        }
        if (current.getPos() + current.getLen() >= line.length()) {
            return null;
        }
        current.setPos(current.getPos() + current.getLen());
        current.setLen(0);

        while (current.getPos() < line.length() && isDelimiter(line.charAt(current.getPos()))) {
            current.setPos(current.getPos() + 1);
        }

        Op op = getOp(line, current.getPos(), 0);
        if (line.startsWith("/*", current.getPos())) {
            while (current.getPos() + current.getLen() < line.length()
                    && !line.startsWith("*/", current.getPos() + current.getLen())) {
                current.setLen(current.getLen() + 1);
            }
            if (current.getPos() + current.getLen() < line.length()) {
                current.setLen(current.getLen() + 2);
            }
            if (current.getPos() + current.getLen() >= line.length()) {
                throw new ParseErrorException(current.getPos() + "@Unclosed comment");
            }
        } else if (line.startsWith("//", current.getPos())) {
            while (current.getPos() + current.getLen() < line.length()
                    && line.charAt(current.getPos() + current.getLen()) != '\r'
                    && line.charAt(current.getPos() + current.getLen()) != '\n') {
                current.setLen(current.getLen() + 1);
            }
            while (current.getPos() + current.getLen() < line.length()
                    && line.charAt(current.getPos() + current.getLen()) == '\r'
                    && line.charAt(current.getPos() + current.getLen()) == '\n') {
                current.setLen(current.getLen() + 1);
            }
        } else if (op != null) {
            current.setLen(op.name.length());
        } else if (line.charAt(current.getPos()) == '\"' || line.charAt(current.getPos()) == '\'') {
            char stop = line.charAt(current.getPos());
            char before;
            do {
                before = 0;
                current.setLen(current.getLen() + 1);
                while (current.getPos() + current.getLen() < line.length()
                        && line.charAt(current.getPos() + current.getLen()) != stop) {
                    before = line.charAt(current.getPos() + current.getLen());
                    current.setLen(current.getLen() + 1);
                }
                if (current.getPos() + current.getLen() >= line.length()) {
                    throw new ParseErrorException(current.getPos() + "@Unclosed quotes");
                }
            } while (before == '\\');
            if (current.getPos() + current.getLen() < line.length()) {
                current.setLen(current.getLen() + 1);
            }
        } else if (line.charAt(current.getPos()) == '{') {
            int counter = 0;
            do {
                while (current.getPos() + current.getLen() < line.length()
                        && line.charAt(current.getPos() + current.getLen()) != '}') {
                    if (line.charAt(current.getPos() + current.getLen()) == '{') {
                        ++counter;
                    }
                    current.setLen(current.getLen() + 1);
                }
                if (current.getPos() + current.getLen() >= line.length()) {
                    throw new ParseErrorException(current.getPos() + "@Unclosed brackets");
                }
                --counter;
                current.setLen(current.getLen() + 1);
            } while (counter > 0);
        } else if (isAlpha(line.charAt(current.getPos()))) {
            while (current.getPos() + current.getLen() < line.length()
                    && (isAlpha(line.charAt(current.getPos() + current.getLen()))
                    || (isNumeric(line.charAt(current.getPos() + current.getLen())) && line.charAt(current.getPos() + current.getLen()) != '.'))) {
                current.setLen(current.getLen() + 1);
            }
        } else if (isNumeric(line.charAt(current.getPos()))) {
            while (current.getPos() + current.getLen() < line.length()
                    && isNumeric(line.charAt(current.getPos() + current.getLen()))
                    && !line.startsWith("..", current.getPos() + current.getLen())) {
                current.setLen(current.getLen() + 1);
            }
        } else {
            current.setLen(current.getLen() + 1);
        }
        return current;
    }

    private static Leaf getParent(Leaf root, Leaf current) {
        if (current != null) {
            Leaf parent = root;
            for (; parent != null && parent.getRight() != current; parent = parent.getRight()) ;
            return parent;
        } else {
            return null;
        }
    }

    private static Leaf parse(String line, Token token) throws ParseErrorException {
        Leaf current = null;
        Leaf root = null;
        while ((token = nextToken(line, token)) != null) {

            if (token.getToken(line).startsWith("//") || token.getToken(line).startsWith("/*")) {
                continue;
            }
            if (token.getToken(line).startsWith("{")) {
                throw new ParseErrorException(current.getPos() + "@Unexpected functional block");
            }
            if (")".equals(token.getToken(line)) || "]".equals(token.getToken(line)) || ";".equals(token.getToken(line))) {
                break;
            }

            Leaf leaf = new Leaf();
            leaf.setValue(token.getToken(line));
            leaf.setPos(token.getPos());

            int range = (current == null || (current.getPriority() > 0 && !"(".equals(current.getValue()))) ? 1 : 2;
            Op op = getOp(line, token.getPos(), range);
            if (op != null) {
                leaf.setRange(op.getRange());
                leaf.setPriority(op.getPrior());
                if (!op.getSubst().isEmpty()) {
                    leaf.setValue(op.getSubst());
                }
            }

            if ("@".equals(leaf.getValue()) || "$".equals(leaf.getValue())) {
                if ((token = nextToken(line, token)) == null) {
                    throw new ParseErrorException(leaf.getPos() + "@No quantifier variable defined");
                }
                Leaf tmp = new Leaf();
                tmp.setValue(token.getToken(line));
                tmp.setPos(token.getPos());
                leaf.setLeft(tmp);
            } else if ("_set".equals(leaf.getValue())) {
                Leaf tmp = new Leaf();
                tmp.setValue("(");
                tmp.setPos(token.getPos());
                tmp.setPriority(1);
                tmp.setRange(2);
                tmp.setLeft(leaf);
                leaf = tmp;
                leaf.setRight(parse(line, token));
                if (!"]".equals(token.getToken(line))) {
                    throw new ParseErrorException(leaf.getPos() + "@Expected closing bracket");
                }
            } else if ("(".equals(leaf.getValue())) {
                leaf.setPriority(1);
                leaf.setRight(parse(line, token));
                if (!")".equals(token.getToken(line))) {
                    throw new ParseErrorException(leaf.getPos() + "@Expected closing bracket");
                }
            }

            if (root == null) {
                root = leaf;
//            } else if (root.getPriority() < leaf.getPriority()) {
//                if(("$".equals(leaf.getValue()) || "@".equals(leaf.getValue())) && root.getRight() == null) {
//                    root.setRight(leaf);
//                } else if (leaf.getLeft() != null) {
//                    throw new ParseErrorException(leaf.getPos() + "@Unexpected operation");
//                } else {
//                    leaf.setLeft(root);
//                    root = leaf;
//                }
            } else if (current.getPriority() < leaf.getPriority()) {

                if (current.getRange() == 1 && ("@".equals(leaf.getValue()) || "$".equals(leaf.getValue()))) {
                    if (current.getRight() == null) {
                        current.setRight(leaf);
                    } else {
                        throw new ParseErrorException(leaf.getPos() + "@Misplaced quantifier");
                    }
                } else {
                    while (current != null && current.getPriority() < leaf.getPriority()) {
                        current = getParent(root, current);
                    }
                    if (current == null) {
                        leaf.setLeft(root);
                        root = leaf;

//                    throw new ParseErrorException(leaf.getPos() + "@Syntax error");
                    } else {
                        if (leaf.getLeft() != null) {
                            throw new ParseErrorException(leaf.getPos() + "@Unexpected operation");
                        } else {
                            leaf.setLeft(current.getRight());
                            current.setRight(leaf);
                        }
                    }
                }
            } else if (current.getRight() == null && current.getPriority() > 0) {
                current.setRight(leaf);
            } else {
//                Leaf parent = getParent(root, current);
                Leaf parentOp = current;
                while (parentOp != null && !"_in".equals(parentOp.getValue())) {
                    parentOp = getParent(root, parentOp);
                }
                if (parentOp != null && parentOp.getPriority() > 0) {
                    Leaf tmp = new Leaf();
                    tmp.setValue(",");
                    tmp.setPos(token.getPos());
                    tmp.setPriority(7);
                    tmp.setRange(2);
                    tmp.setLeft(parentOp.getRight());
                    tmp.setRight(leaf);
                    parentOp.setRight(tmp);
                    parentOp.setRange(parentOp.getRange() + 1);
                    leaf = parentOp;
                    if (getOp(parentOp.getValue(), parentOp.getRange()) == null) {
                        throw new ParseErrorException(leaf.getPos() + "@Unexpected parameter");
                    }
                } else {
                    throw new ParseErrorException(leaf.getPos() + "@Misplaced term");
                }
            }
            current = leaf;
        }
        return root;
    }

    public static Op getOp(String name, int range) {
        for (Op op : ops) {
            if ((name.equals(op.name) || name.equals(op.getSubst())) && range == op.getRange()) {
                return op;
            }
        }
        return null;
    }

    private static int getRange(Leaf t) {
        if (t == null) {
            return 0;
        } else {
            int counter = 0;
            if (",".equals(t.getValue())) {
                counter += getRange(t.getLeft());
                counter += getRange(t.getRight());
            } else {
                counter = 1;
            }
            return counter;
        }
    }

    private static Leaf squeeze(Leaf t) {
        if (t == null) {
            return null;
        }
        t.setLeft(squeeze(t.getLeft()));
        t.setRight(squeeze(t.getRight()));
        if ("(".equals(t.getValue())) {
            if (t.getLeft() == null) {
                return squeeze(t.getRight());
            } else {
                t.setValue(t.getLeft().getValue());
                t.setRange(t.getLeft().getRange());
                t.setPriority(t.getLeft().getPriority());
                t.setLeft(null);
                if (t.getRight() != null && ",".equals(t.getRight().getValue())) {
                    t.setLeft(squeeze(t.getRight().getLeft()));
                    t.setRight(squeeze(t.getRight().getRight()));
                }
                int range = getRange(t.getLeft()) + getRange(t.getRight());
                t.setRange(range);
                return t;
            }
        } else {
            return t;
        }
    }

    public static Leaf parse(String line) throws ParseErrorException {
        Leaf tree = parse(line, null);
        return squeeze(tree);
    }

    public static Operation implement(String line, Mind mind, Token token) throws Exception {
        boolean waitParams = false;
        boolean waitScript = false;
        int pos = 1;
        Operation f = ((User) mind.getUser()).getUdf();
        f.setMind(mind);
        while ((token = nextToken(line, token)) != null) {
            String ln = token.getToken(line);
            if (";".equals(ln)) {
                break;
            }
            if ("(".equals(ln)) {
                waitParams = true;
            } else if (")".equals(ln)) {
                waitParams = false;
                waitScript = true;
            } else if (",".equals(ln)) {
            } else if (waitParams) {
                f.getParams().add(ln);
            } else if (waitScript) {
                if (!ln.startsWith("{")) {
                    throw new ParseErrorException(pos + "@Functional block expected");
                }
                f.getScripts().add(ln.substring(0, ln.length() - 1).substring(1));
            } else {
                f.setName(ln);
            }

        }
        f.setMode(LibMode.FUNCTION);
        f.setRange(f.getParams().size());
        f.getParams().add(f.getName());
        return f;
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

    public static class Op {
        private String name;            // Operation name
        private String subst;           // Substitution name
        private int prior;              // Operation pryority
        private int range;              // Number of parameters
        private int dir;                // Direction: L->R or R->L
        private boolean post;           // Allow postfix form
        private boolean repl;           // Must be just replaced w/o making function syntax

        public Op(String name, String subst, int prior, int cp, int dir, boolean post, boolean repl) {
            this.name = name;
            this.subst = subst;
            this.prior = prior;
            this.range = cp;
            this.dir = dir;
            this.post = post;
            this.repl = repl;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSubst() {
            return subst;
        }

        public void setSubst(String subst) {
            this.subst = subst;
        }

        public int getPrior() {
            return prior;
        }

        public void setPrior(int prior) {
            this.prior = prior;
        }

        public int getRange() {
            return range;
        }

        public void setRange(int range) {
            this.range = range;
        }

        public int getDir() {
            return dir;
        }

        public void setDir(int dir) {
            this.dir = dir;
        }

        public boolean isPost() {
            return post;
        }

        public void setPost(boolean post) {
            this.post = post;
        }

        public boolean isRepl() {
            return repl;
        }

        public void setRepl(boolean repl) {
            this.repl = repl;
        }
    }

}
