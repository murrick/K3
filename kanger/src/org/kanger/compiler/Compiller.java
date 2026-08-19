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
import org.kanger.QueryReplayContext;
import org.kanger.enums.FunctionBinding;
import org.kanger.exception.ParseErrorException;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.primitives.Argument;
import org.kanger.primitives.ArgumentsList;
import org.kanger.units.Domain;
import org.kanger.units.Function;
import org.kanger.units.Rule;
import org.kanger.units.TVariable;
import org.kanger.units.Term;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Семантический транслятор AST KANGER в canonical units одного Mind.
 *
 * <p><strong>Архитектурная роль.</strong> {@code Compiller} принимает дерево
 * {@link Leaf}, построенное {@link Parser}, и через фабрики {@link Mind}
 * создаёт Rule, Domain, Predicate, Function, Term и variable structures. Он
 * является границей между синтаксическим представлением и semantic model, но
 * не исполняет операции, не запускает fixed-point и не определяет истинность
 * запроса. Historical spelling имени класса является compatibility surface и
 * намеренно сохраняется.</p>
 *
 * <p><strong>Rule lifecycle.</strong> {@link #compileLine(Leaf, boolean,
 * String, boolean, Queue)} сначала регистрирует новый Rule и origin Term,
 * строит его дерево, затем передаёт объект {@link org.kanger.factory.RuleFactory}
 * для canonicalization. Если сохранён именно новый ID, выставляется query flag
 * и выполняется Rule expansion; найденный canonical/promoted Rule получает
 * historical second/promotion interpretation. Compiler не подменяет правила
 * фабрики собственным cache.</p>
 *
 * <p><strong>Логическая трансформация.</strong> Рекурсивный {@code construct}
 * переводит отрицание, conjunction, disjunction и implication в Rule domain
 * trees с исторически заданным порядком clone/append. Различия antecedent и
 * consequent определяют, когда ветвь клонируется и когда polarity инвертируется.
 * Этот statement order является semantic kernel и не может быть упрощён как
 * обычная булева AST-нормализация.</p>
 *
 * <p><strong>Кванторы.</strong> {@code @/$} создают либо TVariable, либо
 * child C-variable в зависимости от polarity. Rule помечается substitutable
 * или abstractive, повторное имя variable запрещается, а ранее встреченная
 * C-variable может получить domini marker. Полученные объекты принадлежат
 * соответствующим Mind factories и их transactional lifecycle.</p>
 *
 * <p><strong>Функции и предикаты.</strong> Signature проверяется сначала в
 * infrastructure catalog текущего Calculator, затем в Library. При компиляции
 * Function сохраняется явный {@link FunctionBinding}: built-in operation
 * становится {@code INFRASTRUCTURE}, найденная Library operation —
 * {@code UDF_DYNAMIC}. Predicate и Domain canonicalization выполняется только
 * их фабриками; compiler не исполняет найденную операцию.</p>
 *
 * <p><strong>Аргументы.</strong> Comma-tree разворачивается слева направо.
 * Специальная форма {@code _set} создаёт canonical set Term. Имена переменных
 * разрешаются через map текущего Rule, обычные literals — через Dictionary,
 * а внешние параметры {@code ?...} потребляются из Queue в порядке появления.
 * Недостаток externals, duplicate variable, пустой term или undefined function
 * завершаются {@link ParseErrorException} с source position.</p>
 *
 * <p><strong>Concurrency.</strong> Экземпляр привязан к одному Mind и использует
 * его mutable factories. Одновременная компиляция через один Mind требует
 * внешней сериализации или отдельного child transaction; сам класс не создаёт
 * checkpoint и не владеет rollback.</p>
 *
 * @see Parser
 * @see Mind
 * @see Rule
 */
public class Compiller {

    private final Mind mind;

    public Compiller(Mind mind) {
        this.mind = mind;
    }

    private String functionSignature(Leaf root) {
        return root.getValue() + "(" + root.getRange() + ")";
    }

    private boolean isFunction(Leaf root) throws Exception {
        String name = functionSignature(root);
        return mind.getCalculator().getFunctions().getSysOps().containsKey(name) || mind.getLibrary().find(name) != null;
    }

    private FunctionBinding getFunctionBinding(Leaf root) {
        return mind.getCalculator().getFunctions().getSysOps().containsKey(functionSignature(root))
                ? FunctionBinding.INFRASTRUCTURE
                : FunctionBinding.UDF_DYNAMIC;
    }

    private boolean isPredicate(Leaf root) {
        return mind.getCalculator().getPredicates().getSysOps().containsKey(root.getValue() + "(" + root.getRange() + ")");
    }

    public IRule compileLine(Leaf root, boolean antc, String orig, boolean query, Queue<ITerm> externals) throws Exception {

        QueryReplayContext.observeCompilation(mind, orig, query, externals);

        IRule r = new Rule(mind);
        mind.getRules().register(r);
        ((Rule) r).setOrigin(mind.getTerms().add(orig));
        construct((Rule) r, ((Rule) r).getTree().get(0), root, antc, new HashMap<String, Argument>(), externals);

        long id = r.getId();
        r = mind.getRules().add(r, !query);

        if (r.getId() == id) {
            ((Rule) r).setQuery(query);
            mind.getRules().expand((Rule) r);
        } else if (mind.getRules().isPromotedHere(r) || r.isRestored(mind)) {
            ((Rule) r).setSecond(false);
        } else {
            ((Rule) r).setSecond(true);
        }
        return r;
    }

    private List<List<Domain>> construct(Rule r, List<Domain> t, Leaf root, boolean antc, Map<String, Argument> replacements, Queue<ITerm> externals) throws Exception {
        List<List<Domain>> list = new ArrayList<>();
        Domain d;
        if (root == null) {
            throw new ParseErrorException("0@Term expected");
        }
        switch (root.getValue()) {
            case "!":
            case "~":
                list.addAll(construct(r, t, root.getRight(), !antc, replacements, externals));
                break;

            case "@":
            case "$": {
                antc = compileQuainter(r, root, antc, replacements);
                list.addAll(construct(r, t, root.getRight(), antc, replacements, externals));
            }
            break;
            case ",":
            case "&": {
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
                }
            }
            break;

            case "|": {
                if (antc) {
                    list.addAll(construct(r, t, root.getLeft(), antc, replacements, externals));
                    int size = list.size();
                    for (int i = 0; i < size; ++i) {
                        list.addAll(construct(r, list.get(i), root.getRight(), antc, replacements, externals));
                    }
                    list.addAll(construct(r, t, root.getRight(), antc, replacements, externals));
                } else {
                    List<Domain> x = r.cloneTree(t);
                    list.add(x);
                    list.addAll(construct(r, t, root.getLeft(), antc, replacements, externals));
                    list.addAll(construct(r, x, root.getRight(), antc, replacements, externals));
                }
            }
            break;

            case "}": {
                if (antc) {
                    list.addAll(construct(r, t, root.getLeft(), !antc, replacements, externals));
                    int size = list.size();
                    for (int i = 0; i < size; ++i) {
                        list.addAll(construct(r, list.get(i), root.getRight(), antc, replacements, externals));
                    }
                    list.addAll(construct(r, t, root.getRight(), antc, replacements, externals));
                } else {
                    List<Domain> x = r.cloneTree(t);
                    list.add(x);
                    list.addAll(construct(r, t, root.getLeft(), !antc, replacements, externals));
                    list.addAll(construct(r, x, root.getRight(), antc, replacements, externals));
                }
            }
            break;

            default: {
                d = compilePredicate(r, root, antc, replacements, externals);
                t.add(d);
            }
        }
        return list;
    }

    private boolean compileQuainter(Rule r, Leaf root, boolean antc, Map<String, Argument> replacements) throws Exception {
        String varName = root.getLeft().getValue();

        if (replacements.containsKey(varName)) {
            throw new ParseErrorException(root.getPos() + "Variable " + varName + " duplicated");
        }

        Argument p = null;
        if (("@".equals(root.getValue()) && antc) || ("$".equals(root.getValue()) && !antc)) {
            TVariable t = mind.getTVars().createTVar(r, mind.getTerms().add(varName));
            p = new Argument(t);
            r.setSubstitutable(true);

            ITerm c = null;
            for (Argument a : replacements.values()) {
                if (!a.isEmpty(mind) && a.getValue(mind).isCVariable()) {
                    c = a.getValue(mind);
                    ((Term) c).setDomini(true);
                }
            }
        } else if (("@".equals(root.getValue()) && !antc) || ("$".equals(root.getValue()) && antc)) {
            p = new Argument(mind.getTerms().createCVar(r, mind.getTerms().add(varName), null));
            r.setAbstractive(true);
        }
        replacements.put(varName, p);
        return antc;
    }

    private Domain compilePredicate(Rule r, Leaf root, boolean antc, Map<String, Argument> replacements, Queue<ITerm> externals) throws Exception {
        Domain d = new Domain(mind);
        d.setRule(r);
        ArgumentsList arg = new ArgumentsList();
        parseArgs(arg, root.getLeft(), 0, replacements, externals);
        parseArgs(arg, root.getRight(), 0, replacements, externals);
        org.kanger.units.Predicate pred = mind.getPredicates().add(mind.getTerms().add(root.getValue()), arg.size());
        d.setPredicate(pred);
        d.setAntc(antc);
        d.getArguments().addAll(arg);
        d = mind.getDomains().add(d.getPredicate(), d.isAntc(), d.getArguments(), d.getRule());
        return d;
    }

    private void parseArgs(ArgumentsList arg, Leaf root, int level, Map<String, Argument> replacements, Queue<ITerm> externals) throws Exception {
        if (root == null) {
            return;
        } else if (isFunction(root)) {
            ArgumentsList arguments = new ArgumentsList();
            parseArgs(arguments, root.getLeft(), level + 1, replacements, externals);
            parseArgs(arguments, root.getRight(), level + 1, replacements, externals);
            Function f = mind.getFunctions().add(
                    mind.getTerms().add(root.getValue()),
                    arguments,
                    getFunctionBinding(root));
            arg.add(new Argument(f));
        } else if ("_set".equals(root.getValue())) {
            ArgumentsList arguments = new ArgumentsList();
            parseArgs(arguments, root.getLeft(), level + 1, replacements, externals);
            parseArgs(arguments, root.getRight(), level + 1, replacements, externals);
            arg.add(new Argument(mind.getTerms().add(arguments)));
        } else if (",".equals(root.getValue())) {
            parseArgs(arg, root.getLeft(), level + 1, replacements, externals);
            parseArgs(arg, root.getRight(), level + 1, replacements, externals);
        } else if (root.getValue().charAt(0) == '?') {
            if (externals.isEmpty()) {
                throw new ParseErrorException(root.getPos() + "@External parameter value expected");
            } else {
                arg.add(new Argument(externals.poll()));
            }
        } else if (root.getRight() == null && root.getLeft() == null) {
            Argument t = replacements.get(root.getValue());
            if (t == null) {
                t = new Argument(mind.getTerms().add(root.getValue()));
            }
            arg.add(t);
        } else {
            throw new ParseErrorException(root.getPos() + "@Undefined function");
        }
    }
}
