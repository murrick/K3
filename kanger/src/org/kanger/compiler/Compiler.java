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
import org.kanger.enums.ArgumentType;
import org.kanger.enums.DataType;
import org.kanger.enums.Enums;
import org.kanger.enums.ParseError;
import org.kanger.exception.ParseErrorException;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.primitives.Argument;
import org.kanger.primitives.ArgumentsList;
import org.kanger.units.*;

import java.util.*;

/**
 * Created by Dmitry G. Quznetsov on 25.05.15.
 */
public class Compiler {

    private final Mind mind;

    public Compiler(Mind mind) {
        this.mind = mind;
    }

    public IRule compileLine(PTree root, boolean antc, String orig, boolean query, Queue<ITerm> externals) throws Exception {

        IRule r = new Rule(mind);
        mind.getRules().register(r);
        ((Rule) r).setOrigin(mind.getTerms().add(orig));
        construct((Rule) r, ((Rule) r).getTree().get(0), root, antc, new HashMap<String, Argument>(), externals);

        long id = r.getId();
        r = mind.getRules().add(r);

        if (r.getId() == id) {
            ((Rule) r).setQuery(query);
            mind.getRules().expand((Rule) r);
        } else {
            ((Rule) r).setSecond(true);
        }
        return r;
    }

    private List<List<Domain>> construct(Rule r, List<Domain> t, PTree root, boolean antc, Map<String, Argument> replacements, Queue<ITerm> externals) throws Exception {
        List<List<Domain>> list = new ArrayList<>();
//        List<List<Domain>> tmp = new ArrayList<>();
        Domain d = null;
        if (root == null) {
            throw new ParseErrorException(0, ParseError.EMPTY);
        }
        switch (root.getName().charAt(0)) {
            case Enums.NOT:
                list.addAll(construct(r, t, root.getLeft(), !antc, replacements, externals));
                break;

            case Enums.AQN:
            case Enums.PQN: {
                antc = compileQuainter(r, root, antc, replacements);
                list.addAll(construct(r, t, root.getRight(), antc, replacements, externals));
            }
            break;
            case Enums.COMMA:
                if ("_in".equals(root.getLeft().getName()) && root.getRight() != null && root.getRight().getRight() == null && root.getRight().getLeft() == null) {
                    PTree left = root.getLeft();
                    root.setLeft(left.getRight());
                    left.setRight(root);
                    root = left;
                    d = compilePredicate(r, root, antc, replacements, externals);
                    t.add(d);
                    break;
                }
            case Enums.CON: {
                if (antc) {
                    List<Domain> x = r.cloneTree(t);
                    list.add(x);
                    list.addAll(construct(r, t, root.getLeft(), antc, replacements, externals));
                    list.addAll(construct(r, x, root.getRight(), antc, replacements, externals));
                } else {
                    list.addAll(construct(r, t, root.getLeft(), antc, replacements, externals));
                    int size = list.size();
                    for (int i = 0; i < size; ++i) {
                        list.addAll(construct(r, list.get(i), root.getRight(), antc, replacements, externals));
                    }
                    list.addAll(construct(r, t, root.getRight(), antc, replacements, externals));
//                    list.addAll(tmp);
                }
            }
            break;

            case Enums.DIS: {
                if (antc) {
                    list.addAll(construct(r, t, root.getLeft(), antc, replacements, externals));
                    int size = list.size();
                    for (int i = 0; i < size; ++i) {
                        list.addAll(construct(r, list.get(i), root.getRight(), antc, replacements, externals));
                    }
                    list.addAll(construct(r, t, root.getRight(), antc, replacements, externals));
//                    list.addAll(tmp);
                } else {
                    List<Domain> x = r.cloneTree(t);
                    list.add(x);
                    list.addAll(construct(r, t, root.getLeft(), antc, replacements, externals));
                    list.addAll(construct(r, x, root.getRight(), antc, replacements, externals));
                }
            }
            break;

            case Enums.IMP: {
                if (antc) {
                    list.addAll(construct(r, t, root.getLeft(), !antc, replacements, externals));
                    int size = list.size();
                    for (int i = 0; i < size; ++i) {
                        list.addAll(construct(r, list.get(i), root.getRight(), antc, replacements, externals));
                    }
                    list.addAll(construct(r, t, root.getRight(), antc, replacements, externals));
//                    list.addAll(tmp);
                } else {
                    List<Domain> x = r.cloneTree(t);
                    list.add(x);
                    list.addAll(construct(r, t, root.getLeft(), !antc, replacements, externals));
                    list.addAll(construct(r, x, root.getRight(), antc, replacements, externals));
                }
            }
            break;

            case Enums.LB: {
                if (root.getLeft() == null) {
                    list.addAll(construct(r, t, root.getRight(), antc, replacements, externals));
                } else {
                    d = compilePredicate(r, root, antc, replacements, externals);
                    t.add(d);
                }
            }
            break;

            default: {
                d = compilePredicate(r, root, antc, replacements, externals);
                t.add(d);
            }
        }
//        clones.addAll(list);
        return list;
    }

    private boolean compileQuainter(Rule r, PTree root, boolean antc, Map<String, Argument> replacements) throws Exception {
        String varName = root.getLeft().getName();

        if (replacements.containsKey(varName)) {
            throw new ParseErrorException(root.getPos(), ParseError.AVAR);
        }

        Argument p = null;
        if ((root.getName().charAt(0) == Enums.AQN && antc) || (root.getName().charAt(0) == Enums.PQN && !antc)) {
            TVariable t = mind.getTVars().createTVar(r, mind.getTerms().add(varName));
            p = new Argument(t);
            r.setSubstitutable(true);

            /* Формирование списка подчиненных t-переменных для последней появившейся ранее c-переменной.
             */
            ITerm c = null;
            for (Argument a : replacements.values()) {
                if (!a.isEmpty(mind) && a.getValue(mind).isCVariable() /*&& (c == null || ((Term) c).getIndex() < ((Term) a.getValue(mind)).getIndex())*/) {
                    c = a.getValue(mind);
                    ((Term) c).setDomini(true);
                }
            }
//            if (c != null) {
//                ((Term) c).setDomini(true);
//            }
        } else if ((root.getName().charAt(0) == Enums.AQN && !antc) || (root.getName().charAt(0) == Enums.PQN && antc)) {
            p = new Argument(mind.getTerms().createCVar(r, mind.getTerms().add(varName), null));
            r.setAbstractive(true);
        }
        replacements.put(varName, p);
        return antc;
    }

    private Domain compilePredicate(Rule r, /*List<Domain> t, */PTree root, boolean antc, Map<String, Argument> replacements, Queue<ITerm> externals) throws Exception {
        Domain d = new Domain(mind);
        d.setRule(r);

        ArgumentsList arg = new ArgumentsList();
        Predicate pred = null;
        if (root.isSystem()) {
            // системный предикат
            // ПРОВЕРКА НЛ LB НЕ НУЖНА! Т.К. ОНА ОБРАБАТЫВАЕТСЯ
            if ("_in".equals(root.getName())) {
                if (root.getLeft() != null && root.getLeft().getName().charAt(0) == Enums.NOT) {
                    root.setLeft(root.getLeft().getLeft());
                    antc = !antc;
                }
                if (root.getRight() != null && "_neg".equals(root.getRight().getName()) && root.getRight().getLeft() != null && !"_interval".equals(root.getRight().getLeft().getName())) {
                    root.setRight(root.getRight().getLeft());
                    root.getRight().setName("-" + root.getRight().getName());
                }
                if (root.getLeft() != null && "_neg".equals(root.getLeft().getName()) && root.getLeft().getLeft().getName().contains("..")) {
                    root.setLeft(root.getLeft().getLeft());
                    root.getLeft().setName("-" + root.getLeft().getName());
                }
            }
            parseArgs(arg, root, 0, replacements, externals);
            pred = mind.getPredicates().add(mind.getTerms().add(root.getName()), arg.size());
        } else if (root.getLeft() == null) {
            pred = mind.getPredicates().add(mind.getTerms().add(root.getName()), 0);
        } else {
            parseArgs(arg, root.getRight(), 1, replacements, externals);
            pred = mind.getPredicates().add(mind.getTerms().add(root.getLeft().getName()), arg.size());
        }
        d.setPredicate(pred);
        d.setAntc(antc);
        d.getArguments().addAll(arg);

        d = mind.getDomains().add(d.getPredicate(), d.isAntc(), d.getArguments(), d.getRule());
//        t.add(d);

        return d;
    }

    private void parseArgs(ArgumentsList arg, PTree root, int level, Map<String, Argument> replacements, Queue<ITerm> externals) throws Exception {
        if (root == null) {
        } else if (root.isSystem()) {
            if (level == 0) {
                parseArgs(arg, root.getLeft(), level + 1, replacements, externals);
                parseArgs(arg, root.getRight(), level + 1, replacements, externals);
            } else {
                // системная функция
                ArgumentsList arguments = new ArgumentsList();
                parseArgs(arguments, root.getLeft(), level + 1, replacements, externals);
                parseArgs(arguments, root.getRight(), level + 1, replacements, externals);
                if (root.getName().equals("_neg")
                        && arguments.size() == 1
                        && !arguments.get(0).isEmpty(mind)
                        && arguments.get(0).getValue(mind).getType() != DataType.NUMERIC
                        && arguments.get(0).getType() != ArgumentType.FUNCTION) {
                    throw new ParseErrorException(root.getPos(), ParseError.ENEG);
                }
                Function f = mind.getFunctions().add(mind.getTerms().add(root.getName()), arguments);
                Argument t = new Argument(f);
                arg.add(t);
            }
        } else if (root.getName().charAt(0) == Enums.COMMA) {
            parseArgs(arg, root.getLeft(), level + 1, replacements, externals);
            parseArgs(arg, root.getRight(), level + 1, replacements, externals);
        } else if (root.getName().charAt(0) == Enums.LB) {
            // вложенная функция
            ArgumentsList arguments = new ArgumentsList();
            parseArgs(arguments, root.getRight(), level + 1, replacements, externals);
            Function f = mind.getFunctions().add(mind.getTerms().add(root.getLeft().getName()), arguments);
            Argument t = new Argument(f);
            arg.add(t);
        } else if (root.getName().equals("..")) {
            Argument t = new Argument(mind.getTerms().add(root));
            arg.add(t);
        } else if (root.getName().charAt(0) == '{' && root.getName().charAt(root.getName().length() - 1) == '}') {
            String str = root.getName().substring(1, root.getName().length() - 1);
            Argument t;
            if (root.getName().contains("..")) {
                t = new Argument(mind.getTerms().add(str));
            } else {
                ArgumentsList list = new ArgumentsList();
                for (String s : str.split(",")) {
                    if (!s.trim().isEmpty()) {
                        list.add(new Argument(mind.getTerms().add(s)));
                    }
                }
                t = new Argument(mind.getTerms().add(list));
            }
            arg.add(t);
        } else if (root.getName().charAt(0) == '?') {
            if (externals.isEmpty()) {
                throw new ParseErrorException(root.getPos(), ParseError.EPARAM);
            } else {
                arg.add(new Argument(externals.poll()));
            }
        } else {
            Argument t;
            if ((t = replacements.get(root.getName())) == null) {
                t = new Argument(mind.getTerms().add(root.getName()));
            }
            arg.add(t);
        }
    }
}
