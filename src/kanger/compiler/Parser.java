package kanger.compiler;

import kanger.Mind;
import kanger.enums.Enums;
import kanger.enums.LibMode;
import kanger.enums.ParseError;
import kanger.enums.Tools;
import kanger.exception.ParseErrorException;

/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 */
public class Parser {
//    private static final int MODE_FUNCTION = 3;
//    private static final int MODE_PREDICATE = 4;

    private static final int DIR_LEFT = 0;
    private static final int DIR_RIGHT = 1;
    private static final Operation[] ops = {
            /*  1 */new Operation("++", "_inc", 1, 1, 1, false, false), new Operation("--", "_dec", 1, 1, 1, false, false), new Operation("-", "_neg", 1, 1, 1, false, false), new Operation("+", "_val", 1, 1, 1, false, false), new Operation("~~", "_bitnot", 1, 1, 1, false, false),
            /*  2 */ new Operation("*", "_mul", 2, 2, 0, false, false), new Operation("/", "_div", 2, 2, 0, false, false), new Operation("%", "_rem", 2, 2, 0, false, false),
            /*  3 */ new Operation("+", "_add", 3, 2, 0, false, false), new Operation("-", "_sub", 3, 2, 0, false, false),
            /*  3 */ new Operation("<<", "_bitleft", 3, 2, 0, false, false), new Operation(">>", "_bitright", 3, 2, 0, false, false), new Operation("&", "_bitand", 3, 2, 0, false, false),
            /*  3 */ new Operation("^", "_bitxor", 3, 2, 0, false, false), new Operation("|", "_bitor", 3, 2, 0, false, false),
            /*  6 */ new Operation(",", "", 6, 2, 0, false, false),
            /* 10 */ new Operation("~", "", 1, 1, 1, false, false),
            /* 11 */ new Operation("<", "_lr", 11, 2, 0, false, false), new Operation("<=", "_le", 11, 2, 0, false, false), new Operation(">", "_gr", 11, 2, 0, false, false), new Operation(">=", "_ge", 11, 2, 0, false, false),
            /* 12 */ new Operation("==", "_eq", 12, 2, 0, false, false), new Operation("=", "_eq", 12, 2, 0, false, false), new Operation("!=", "_ne", 12, 2, 0, false, false), new Operation("<>", "_ne", 12, 2, 0, false, false),
            /* 13 */ new Operation("&&", "&", 13, 2, 0, false, true),
            /* 14 */ new Operation("||", "|", 14, 2, 0, false, true),
            /* 15 */ new Operation("->", "}", 15, 2, 0, false, true),
            /* 16 */ new Operation("@", "", 16, 1, 1, true, false), new Operation("$", "", 16, 1, 1, true, false),
            new Operation("", "", 0, 0, 0, false, false),};

    public static boolean isDelimiter(int ch) {
        return ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n';
    }

    public static boolean isAlpha(int ch) {
        return ch == '_' || (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z');
    }

    public static boolean isNumeric(int ch) {
        return (ch >= '0' && ch <= '9') /*|| ch == '-' || ch == '+'*/ || ch == '.';
    }

    public static Object[] getToken(String ln, int pos) throws ParseErrorException {
        int ch, c, i;
        String line = "";

        if (ln.isEmpty() || ln.charAt(0) == 0) {
            return null;
        }
        ch = ln.charAt(pos);
        while (pos < ln.length() && isDelimiter(ch = ln.charAt(pos++))) ;
        if (pos >= ln.length()) {
            return null;
        }
        c = ln.charAt(pos++);

        /*
         * Skip comments
         */
 /*
         if(ch == '/' && c=='*'){
         do{
         while((ch=*_cs++) && ch != '*');
         if(ch == '*' && (ch=*_cs++) == '/') break;
         }
         while(ch);
         if(ch) goto again;
         }
         */

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
        } else {
            line += (char) ch;

            /* double character operators */
            for (i = 0; ops[i].getName().length() > 0; ++i) {
                if (ops[i].getName().length() > 1 && ops[i].getName().charAt(0) == ch && ops[i].getName().charAt(1) == c) {
                    line += (char) c;
                    break;
                }
            }
            if (ops[i].getName().length() == 0) {

                /* single character operations */
                for (i = 0; ops[i].getName().length() > 0; ++i) {
                    if (ops[i].getName().charAt(0) == ch) {
                        --pos;
                        break;
                    }
                }

                if (ops[i].getName().length() == 0) {

                    --pos;
                    if (isAlpha(ch)) {
                        while (isAlpha(ch = ln.charAt(pos++)) || isNumeric(ch)) {
                            line += (char) ch;
                        }
                        --pos;
                    } else if (isNumeric(ch)) {
                        while (isNumeric(ch = ln.charAt(pos++))) {
                            line += (char) ch;
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
    private static PTree parse(String ln, int pos, int mode) throws ParseErrorException {
        String line = "";
        PTree p, q, r, root, wasq;
        int i, term;
        WasWhat was = WasWhat.NOTHING;

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

            if (line.charAt(0) == Enums.RB) {
                if (root != null) {
                    root.setPos(pos);
//                } else if (line.charAt(0) == Enums.EOLN) {
//                    throw new ParseErrorException(ParseError.EMPTY);
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

            for (i = 0; ops[i].getName().length() > 0; ++i) {
                if (line.equals(ops[i].getName())) {

                    if (term == 0 && ops[i].getRange() != 1) {
                        //continue;
                        throw new ParseErrorException(pos, ParseError.EMPTY);
                    }
                    if (term != 0 && ops[i].getRange() == 1) {
                        //Заглушка на унарные операции +/-
                        if (ops[i].getName().equals("-") || ops[i].getName().equals("+")) {
                            continue;
                        }
                        throw new ParseErrorException(pos, ParseError.EMPTY);
                        //continue;
                    }

                    /* WAS QUANTOR flag and pointer. Need for correct
                     * definition non-standard quantor syntax
                     */
                    wasq = ops[i].isPost() ? p : null;

                    p.setPrior(ops[i].getPrior());
                    p.setNext(ops[i].getRange() > 1 && !ops[i].isPost() ? DIR_RIGHT : DIR_LEFT);
                    p.setDir(ops[i].getDir());
                    p.setRange(ops[i].getRange());

                    /* System predicates or functions */
                    if (ops[i].getSubst().length() > 0) {
                        if (!ops[i].isRepl()) {
                            p.setSystem(true); //.setMode(MODE_FUNCTION);
                        }
                        p.setName(ops[i].getSubst());
                    }
                    break;
                }
            }

            /*
             if(!mode && i >= count){
             for(i=0; i<oper_count; ++i){
             if(!strcmp(line,oper_ops[i].name)){
             count = oper_count;
             ops = oper_ops;
             mode = 1;
             goto REPEAT;
             }
             }
             }

             else if(mode && i >= count){
             for(i=0; i<glob_count; ++i){
             if(!strcmp(line,glob_ops[i].name)){
             count = glob_count;
             ops = glob_ops;
             mode = 0;
             goto REPEAT;
             }
             }
             }
             */
            if (p.getName() == null) {
                p.setName(line);
            }

            if (p.getName().charAt(0) == Enums.COMMA && mode == 0) {
                p.setPrior(17);
            }


            /* Check () Calculate
             * recursively. Detect predicate. Define 'term' flag == 1 if this
             * is just a name, and == 0 if this is
             * databased operation.
             */
            if (p.getName().charAt(0) == Enums.LB) {
                if (was == WasWhat.QNT /*|| was == WasWhat.TERM*/) {
                    throw new ParseErrorException(pos, ParseError.LBRACK);
                }
                p.setPrior(0);

                //if(term)
                p.setRight(parse(ln, pos, term + mode));
                if (p.getRight() != null) {
                    pos = p.getRight().getPos();
                } else {
                    ++pos;
                }
//                else if (ln.charAt(pos) == Enums.RB) {
//                    // Функция ранга 0
//                    ++pos;
//                }
                if (ln.charAt(pos - 1) != Enums.RB) {
                    throw new ParseErrorException(pos, ParseError.BRACKET);
                }
                was = WasWhat.TERM;

                /* Set predicate or function for undefined */
//                if (term != 0) {
//                    p.setSystem(true); //.setMode(MODE_FUNCTION);
//                } else {
//                    term = 1;
//                }
                if (term == 0) {
                    term = 1;
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
                    p.setLeft(root);
                    root = p;
                } else {
                    if (wasq == null) {
                        p.setLeft(q);
                    } else {
                        p.setRight(q);
                    }
                    if (r.getNext() == DIR_LEFT) {
                        r.setLeft(p);
                    } else {
                        r.setRight(p);
                    }
                }

                // Обработка интервалов
                if (Enums.INTERVALS.containsKey(p.getName().toLowerCase())
                        && p.getLeft() != null
                        && !p.getLeft().getName().isEmpty()
                        && Tools.isInt(p.getLeft().getName())
                        && p.getRight() == null) {
                    p.setName(p.getLeft().getName() + " " + p.getName());
                    if (p.getLeft().getLeft() != null && Tools.isInterval(p.getLeft().getLeft().getName())) {
                        p.setName(p.getLeft().getLeft().getName() + " " + p.getName());
                    }
                    p.setLeft(null);
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


                /* If node is first in tree -
                 * just place'em as root of tree.
                 */
            } else {
                root = p;
            }

        } while (pos < ln.length());
        return root;
    }

    public static SysOp implement(String ln, Mind mind) throws ParseErrorException {
        String line = "";
        boolean waitParams = false;
        boolean waitScript = false;
        int pos = 1;
        SysOp f = new SysOp(mind);
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

    private static PTree squeze(PTree t) {
        if (t == null) {
            return null;
        }
        t.setLeft(squeze(t.getLeft()));
        t.setRight(squeze(t.getRight()));
        if (t.getName().charAt(0) == Enums.LB && t.getLeft() == null) {
            return squeze(t.getRight());
        } else {
            return t;
        }
    }

    public static PTree parser(String ln) throws ParseErrorException {
        char lastch;

        return squeze(parse(ln, 0, 0));
//        return parse(ln, 0, 0);
    }

    //    String recursePtree(PTree root, int mode) {
//        String str = "";
//        if(root != null){
//            if(root.isSystem() /*->mode == FUNCTION || root->mode == PREDICAT*/){
//                if(root.getName().charAt(0) == Enums.LB){
//                    str += recursePtree(root.getLeft(), mode);
//                    str += "(";
//                    str += recursePtree(root.getRight(), 1);
//                    str += ")";
//                }
//                else{
//                    str += String.format("%s(", root.getName());
//                    str += recursePtree(root.getLeft(), 1);
//                    if(root.getRight() != null){
//                        str += ",";
//                        str += recursePtree(root.getRight(), 1);
//                    }
//                    str += ")";
//                }
//            }
//            else if(root.getName().charAt(0) == Enums.AQN || root.getName().charAt(0) == Enums.PQN){
//                str += root.getName();
//                str += recursePtree(root.getLeft(), mode);
//                str += " ";
//                str += recursePtree(root.getRight(), mode);
//            }
//            else if(root.getRange() == 1){
//                str += root.getName();
//                str += recursePtree(root.getLeft(), mode);
//            }
//            else if(root.getName().charAt(0) == Enums.LB){
//                if(mode == 0 || root.getLeft() != null){
//                    str += recursePtree(root.getLeft(), mode);
//                    str += "(";
//                    str += recursePtree(root.getRight(), mode);
//                    str += ")";
//                }
//                else
//                    str += recursePtree(root.getRight(), mode);
//            }
//            else{
//                if(root.getPrior() == 0 || root.getName().charAt(0) == Enums.COMMA){
//                    str += recursePtree(root.getLeft(), mode);
//                    str += root.getName();
//                    str += recursePtree(root.getRight(), mode);
//                }
//                else{
//                    if(root.getLeft() != null && !root.getLeft().isSystem()){
//                        str += "(";
//                        str += recursePtree(root.getLeft(), mode);
//                        str += ")";
//                    }     else {
//                        str += recursePtree(root.getLeft(), mode);
//                    }
//
//                    str += root.getName();
//
//                    if(root.getRight() != null && !root.getRight().isSystem()){
//                        str += "(";
//                        str += recursePtree(root.getRight(), mode);
//                        str += ")";
//                    }                    else {
//                        str += recursePtree(root.getRight(), mode);
//                    }
//                }
//            }
//        }
//        return str;
//    }
//    String reformatLine(String ln) {
//        ln = skipSpaces(ln);
//        int antc = ln.charAt(0);
//
//        PTree p = parser(ln.substring(1));
//        String line = ((char)antc) + recursePtree(p, 0) + ";";
//        return line;
//    }
    public static Operation getOp(String o) {
        for (Operation op : ops) {
            if (op.getSubst().equals(o)) {
                return op;
            }
        }
        return null;
    }

    public static int skipSpaces(String line, int pos) {
        while (pos < line.length() && isDelimiter(line.charAt(pos))) {
            ++pos;
        }
        return pos;
    }

    private enum WasWhat {
        NOTHING, QNT, TERM, INFIX, NOT, VAR, COMMA
    }

}
