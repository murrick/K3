package kanger.compiler;

import kanger.Mind;
import kanger.enums.Enums;
import kanger.enums.ParseError;
import kanger.exception.ParseErrorException;
import kanger.exception.RuntimeErrorException;
import kanger.primitives.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by murray on 25.05.15.
 */
public class Compiler {

    private Mind mind;

    public Compiler(Mind mind) {
        this.mind = mind;
    }

    public Right compileLine(PTree root, boolean antc) throws ParseErrorException, RuntimeErrorException {

        //mind.getReplacements().reset();
        mind.clearSavedResults();
        Right r = mind.getRights().add();

//        NodeFactory n = new NodeFactory();
//        construct(n, root, antc, Node.STILL);
//        t = recurseTree(n.getRoot());
        Tree t = mind.getTrees().add();
        r.getTree().add(t);
        construct(r, t, root, antc, new HashMap<String, Argument>(), new ArrayList<Tree>());
        mind.clearSavedResults();

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
//                List<Argument> arg = new ArrayList<>();
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
        switch (root.getName().charAt(0)) {
            case Enums.NOT:
                construct(r, t, root.getLeft(), !antc, replacements, list);
                break;

            case Enums.AQN:
            case Enums.PQN: {
                compileQuantor(root, antc, replacements);
                construct(r, t, root.getRight(), antc, replacements, list);
            }
            break;

            case Enums.COMMA: {
                Tree x = r.cloneTree(t, false);
                list.add(x);
                construct(r, t, root.getLeft(), antc, replacements, list);
                construct(r, x, root.getRight(), antc, replacements, list);
            }
            break;

            case Enums.CON: {
                if (antc) {
                    Tree x = r.cloneTree(t, true);
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
                Tree x = r.cloneTree(t, !antc);
                list.add(x);
                construct(r, t, root.getLeft(), antc, replacements, list);
                construct(r, x, root.getRight(), antc, replacements, list);
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
                    Tree x = r.cloneTree(t, true);
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
                    compilePredicate(t, root, antc, replacements);
                }
            }
            break;

            default: {
                compilePredicate(t, root, antc, replacements);
            }
        }
        clones.addAll(list);
    }

    private void compileQuantor(PTree root, boolean antc, Map<String, Argument> replacements) throws ParseErrorException {
        if (replacements.containsKey(root.getLeft().getName())) {
            throw new ParseErrorException(root.getPos(), ParseError.AVAR);
        }

        Argument p = null;
        if ((root.getName().charAt(0) == Enums.AQN && antc) || (root.getName().charAt(0) == Enums.PQN && !antc)) {
            p = new Argument(mind.getTVars().add());
            p.getT().setName(root.getLeft().getName());
        } else if ((root.getName().charAt(0) == Enums.AQN && !antc) || (root.getName().charAt(0) == Enums.PQN && antc)) {
            p = new Argument(mind.getTerms().get(root.getLeft().getName()));
        }
        replacements.put(root.getLeft().getName(), p);
    }

    private Tree compilePredicate(Tree t, PTree root, boolean antc, Map<String, Argument> replacements) {
        Domain d = mind.getDomains().add(mind.getRights().getRoot());
        List<Argument> arg = new ArrayList<>();
        Predicate pred = null;
        if (root.isSystem()) {
            // системный предикат
            // ПРОВЕРКА НЛ LB НЕ НУЖНА! Т.К. ОНА ОБРАБАТЫВАЕТСЯ
            parseArgs(d, arg, root, 0, replacements);
            pred = mind.getPredicates().add(root.getName(), arg.size());
        } else if (root.getLeft() == null) {
            pred = mind.getPredicates().add(root.getName(), 0);
        } else {
            parseArgs(d, arg, root.getRight(), 1, replacements);
            pred = mind.getPredicates().add(root.getLeft().getName(), arg.size());
        }
        d.setPredicate(pred);
        d.setAntc(antc);
        d.getArguments().addAll(arg);
        t.getSequence().add(d);
        return t;
    }

    private void parseArgs(Domain d, List<Argument> arg, PTree root, int level, Map<String, Argument> replacements) {
        int s;

        if (root == null) {
        } else if (root.isSystem()) {
            if (level == 0) {
                parseArgs(d, arg, root.getLeft(), level + 1, replacements);
                parseArgs(d, arg, root.getRight(), level + 1, replacements);
            } else {
                // системная функция

                Function f = mind.getFunctions().add(d); //new Function(mind);
                parseArgs(d, f.getArguments(), root.getLeft(), level + 1, replacements);
                parseArgs(d, f.getArguments(), root.getRight(), level + 1, replacements);
                f.setName(mind.getTerms().add(root.getName()));
                f.setRange(f.getArguments().size());
                Argument t = new Argument(f);
                arg.add(t);
            }
        } else if (root.getName().charAt(0) == Enums.COMMA) {
            parseArgs(d, arg, root.getLeft(), level + 1, replacements);
            parseArgs(d, arg, root.getRight(), level + 1, replacements);
        } else if (root.getName().charAt(0) == Enums.LB) {
            // вложенная функция
            Function f = mind.getFunctions().add(d); //new Function(mind);
            parseArgs(d, f.getArguments(), root.getRight(), level + 1, replacements);
            f.setName(mind.getTerms().add(root.getLeft().getName()));
            f.setRange(f.getArguments().size());
            Argument t = new Argument(f);
            arg.add(t);
        } else {
            Argument t;
            if ((t = replacements.get(root.getName())) == null) {
                t = new Argument(mind.getTerms().add(root.getName()));
            }
            arg.add(t);
        }
    }
}
