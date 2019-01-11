package kanger.compiler;

import kanger.User;
import kanger.enums.Enums;
import kanger.enums.ParseError;
import kanger.exception.ParseErrorException;
import kanger.exception.RuntimeErrorException;
import kanger.primitives.ArgList;
import kanger.primitives.Argument;
import kanger.units.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by murray on 25.05.15.
 */
public class Compiler {

    private User user;

    public Compiler(User user) {
        this.user = user;
    }

    public Right compileLine(PTree root, boolean antc) throws ParseErrorException, RuntimeErrorException {

        //mind.getReplacements().reset();
//        user.getMind().getSolutions().clear();
//        user.getMind().getValues().clear();
        Right r = user.getMind().getRights().add();

//        NodeFactory n = new NodeFactory();
//        construct(n, root, antc, Node.STILL);
//        t = recurseTree(n.getRoot());
        Tree t = user.getMind().getTrees().add(r);
        r.getTree().add(t);
        construct(r, t, root, antc, new HashMap<String, Argument>(), new ArrayList<Tree>());
//        user.getMind().getSolutions().clear();
//        user.getMind().getValues().clear();

//        r.setT(t);

//        mind.getCalculator().recalculate(-13);
        /*
         * Если правило содержит одну ветку -
         * пытаемся завалить в б.д. все полные предикаты
         * из нее.
         */
//        if (t.getRight() == null) {
////            for (Tree t = tree; t != null; t = t.getRight()) {
//            for (Tree v = t; v != null; v = v.getDown()) {
//                ArgList arg = new ArrayList<>();
//                if (mind.getAnalyser().fillSolve(arg, v.getD(), v.getD().isAntc()) != 0) {
//                    mind.getAnalyser().addBase(arg, v.getD(), false /*v.getD().getPredicate(), v.getD().isAntc()*/);
//                }
//            }
////            }
////            mind.getCalculator().recalculate(-13);
//        }

        return r;
    }

    private void construct(Right r, Tree t, PTree root, boolean antc, Map<String, Argument> replacements, List<Tree> clones) throws ParseErrorException {
        List<Tree> list = new ArrayList<>();
        List<Tree> tmp = new ArrayList<>();
        if(root == null) {
            throw new ParseErrorException(0, ParseError.EMPTY);
        }
        switch (root.getName().charAt(0)) {
            case Enums.NOT:
                construct(r, t, root.getLeft(), !antc, replacements, list);
                break;

            case Enums.AQN:
            case Enums.PQN: {
                antc = compileQuantor(r, root, antc, replacements);
                construct(r, t, root.getRight(), antc, replacements, list);
            }
            break;

//            case Enums.COMMA: {
//                Tree x = r.cloneTree(t, false);
//                list.add(x);
//                construct(r, t, root.getLeft(), antc, replacements, list);
//                construct(r, x, root.getRight(), antc, replacements, list);
//            }
//            break;

            case Enums.COMMA:
                if ("_in".equals(root.getLeft().getName())) {
                    PTree left = root.getLeft();
                    root.setLeft(left.getRight());
                    left.setRight(root);
                    root = left;
                    compilePredicate(r, t, root, antc, replacements);
                    break;
                }
            case Enums.CON: {
                if (antc) {
                    Tree x = r.cloneTree(t);
                    list.add(x);
                    construct(r, t, root.getLeft(), antc, replacements, list);
                    construct(r, x, root.getRight(), antc, replacements, list);
                } else {
                    construct(r, t, root.getLeft(), antc, replacements, list);
                    for (Tree x : list) {
                        construct(r, x, root.getRight(), antc, replacements, tmp);
                    }
                    construct(r, t, root.getRight(), antc, replacements, tmp);
                    list.addAll(tmp);
                }
            }
            break;

            case Enums.DIS: {
                if (antc) {
                    construct(r, t, root.getLeft(), antc, replacements, list);
                    for (Tree x : list) {
                        construct(r, x, root.getRight(), antc, replacements, tmp);
                    }
                    construct(r, t, root.getRight(), antc, replacements, tmp);
                    list.addAll(tmp);
                } else {
                    Tree x = r.cloneTree(t);
                    list.add(x);
                    construct(r, t, root.getLeft(), antc, replacements, list);
                    construct(r, x, root.getRight(), antc, replacements, list);
                }
            }
            break;

            case Enums.IMP: {
                if (antc) {
                    construct(r, t, root.getLeft(), !antc, replacements, list);
                    for (Tree z : list) {
                        construct(r, z, root.getRight(), antc, replacements, tmp);
                    }
                    construct(r, t, root.getRight(), antc, replacements, tmp);
                    list.addAll(tmp);
                } else {
                    Tree x = r.cloneTree(t);
                    list.add(x);
                    construct(r, t, root.getLeft(), !antc, replacements, list);
                    construct(r, x, root.getRight(), antc, replacements, list);
                }
            }
            break;

            case Enums.LB: {
                if (root.getLeft() == null) {
                    construct(r, t, root.getRight(), antc, replacements, list);
                } else {
                    compilePredicate(r, t, root, antc, replacements);
                }
            }
            break;

            default: {
                compilePredicate(r, t, root, antc, replacements);
            }
        }
        clones.addAll(list);
    }

    private boolean compileQuantor(Right r, PTree root, boolean antc, Map<String, Argument> replacements) throws ParseErrorException {
        String varName = root.getLeft().getName();

        if (replacements.containsKey(varName)) {
            throw new ParseErrorException(root.getPos(), ParseError.AVAR);
        }

        Argument p = null;
        if ((root.getName().charAt(0) == Enums.AQN && antc) || (root.getName().charAt(0) == Enums.PQN && !antc)) {
            p = new Argument(user.getMind().getTVars().createTVar(r));
            p.getT().setName(user.getMind().getTerms().add(varName));
        } else if ((root.getName().charAt(0) == Enums.AQN && !antc) || (root.getName().charAt(0) == Enums.PQN && antc)) {
            p = new Argument(user.getMind().getTerms().createCVar(r, varName));
        }
        replacements.put(varName, p);
        return antc;
    }

    private Tree compilePredicate(Right r, Tree t, PTree root, boolean antc, Map<String, Argument> replacements) {
//        Domain d = user.getMind().getDomains().add(user.getMind().getRights().getRoot());

        Domain d = new Domain(user);
        d.setRightId(r.getId());

        ArgList arg = new ArgList();
        Predicate pred = null;
        if (root.isSystem()) {
            // системный предикат
            // ПРОВЕРКА НЛ LB НЕ НУЖНА! Т.К. ОНА ОБРАБАТЫВАЕТСЯ
            if ("_in".equals(root.getName())) {
                if(root.getLeft() != null && root.getLeft().getName().charAt(0) == Enums.NOT) {
                    root.setLeft(root.getLeft().getLeft());
                    antc = !antc;
                }
                if (root.getRight() != null && "_neg".equals(root.getRight().getName())) {
                    root.setRight(root.getRight().getLeft());
                    root.getRight().setName("-" + root.getRight().getName());
                }
                if (root.getLeft() != null && "_neg".equals(root.getLeft().getName()) && root.getLeft().getLeft().getName().contains("..")) {
                    root.setLeft(root.getLeft().getLeft());
                    root.getLeft().setName("-" + root.getLeft().getName());
                }
            }
            parseArgs(d, arg, root, 0, replacements);

//            if (arg.size() > 2 && ("_in".equals(root.getName()) || "_eq".equals(root.getName()))) {
//                ArgList tmp = arg;
//                arg = new ArgList();
//                if(tmp.get(tmp.size()-1).isTSet()) {
//                    arg.add(tmp.get(tmp.size()-1));
//                    tmp.remove(tmp.size()-1);
//                    arg.add(0, new Argument(user.getMind().getTerms().add(tmp)));
//                } else {
//                    arg.add(tmp.get(0));
//                    tmp.remove(0);
//                    arg.add(new Argument(user.getMind().getTerms().add(tmp)));
//                }
//            }
            pred = user.getMind().getPredicates().add(user.getMind().getTerms().add(root.getName()), arg.size());
        } else if (root.getLeft() == null) {
            pred = user.getMind().getPredicates().add(user.getMind().getTerms().add(root.getName()), 0);
        } else {
            parseArgs(d, arg, root.getRight(), 1, replacements);
            pred = user.getMind().getPredicates().add(user.getMind().getTerms().add(root.getLeft().getName()), arg.size());
        }
        d.setPredicateId(pred.getId());
        d.setAntc(antc);
        d.getArguments().addAll(arg);

        d = user.getMind().getDomains().add(d.getPredicateId(), d.isAntc(), d.getArguments(), d.getRightId());
        t.getSequence().add(d);
        return t;
    }

    private void parseArgs(Domain d, ArgList arg, PTree root, int level, Map<String, Argument> replacements) {
//        int s;

        if (root == null) {
        } else if (root.isSystem()) {
            if (level == 0) {
                parseArgs(d, arg, root.getLeft(), level + 1, replacements);
                parseArgs(d, arg, root.getRight(), level + 1, replacements);
            } else {
                // системная функция
                Function f = user.getMind().getFunctions().add(); //new Function(mind);
                parseArgs(d, f.getArguments(), root.getLeft(), level + 1, replacements);
                parseArgs(d, f.getArguments(), root.getRight(), level + 1, replacements);
                f.setNameId(user.getMind().getTerms().add(root.getName()).getId());
                f.setRange(f.getArguments().size());
                Argument t = new Argument(f);
                arg.add(t);
            }
        } else if (root.getName().charAt(0) == Enums.COMMA) {
            parseArgs(d, arg, root.getLeft(), level + 1, replacements);
            parseArgs(d, arg, root.getRight(), level + 1, replacements);
        } else if (root.getName().charAt(0) == Enums.LB) {
            // вложенная функция
            Function f = user.getMind().getFunctions().add(); //new Function(mind);
            parseArgs(d, f.getArguments(), root.getRight(), level + 1, replacements);
            f.setNameId(user.getMind().getTerms().add(root.getLeft().getName()).getId());
            f.setRange(f.getArguments().size());
            Argument t = new Argument(f);
            arg.add(t);
        } else if (root.getName().equals("..")) {
            Argument t = new Argument(user.getMind().getTerms().add(root));
            arg.add(t);
        } else if (root.getName().charAt(0)== '{' && root.getName().charAt(root.getName().length()-1)=='}') {
            String str = root.getName().substring(1, root.getName().length() - 1);
            Argument t;
            if (root.getName().contains("..")) {
                t = new Argument(user.getMind().getTerms().add(str));
            } else {
                ArgList list = new ArgList();
                for (String s : str.split(",")) {
                    if (!s.trim().isEmpty()) {
                        list.add(new Argument(user.getMind().getTerms().add(s)));
                    }
                }
                t = new Argument(user.getMind().getTerms().add(list));
            }
            arg.add(t);
        } else {
            Argument t;
            if ((t = replacements.get(root.getName())) == null) {
                t = new Argument(user.getMind().getTerms().add(root.getName()));
            }
            arg.add(t);
        }
    }
}
