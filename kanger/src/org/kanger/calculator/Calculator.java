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

package org.kanger.calculator;

import org.kanger.Mind;
import org.kanger.enums.ArgumentType;
import org.kanger.enums.DataType;
import org.kanger.enums.FunctionBinding;
import org.kanger.enums.LibMode;
import org.kanger.enums.LogMode;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IPredicate;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Domain;
import org.kanger.units.Function;
import org.kanger.units.Operation;
import org.kanger.units.Term;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Per-Mind facade выполнения системных и пользовательских операций KANGER.
 *
 * <p><strong>Архитектурная роль.</strong> {@code Calculator} связывает
 * semantic {@link Function}/{@link Domain} с built-in каталогами
 * {@link Functions}/{@link Predicates} и copy-on-write Library текущего
 * {@link Mind}. Он разрешает operation binding, вызывает {@link Operation} и
 * публикует новый function result через FValueFactory. Fixed-point orchestration
 * принадлежит {@link org.kanger.Linker}; Calculator не владеет транзакцией,
 * canonical Function identity или persistent Library.</p>
 *
 * <p><strong>Function calculation.</strong> {@link #calculate(Function,
 * boolean)} сначала рекурсивно рассчитывает вложенные Function arguments,
 * результаты которых ещё пусты. Текущая Function выполняется только если
 * result отсутствует либо отличается от вычисляемого value. Return convention
 * operation сохраняется исторически: {@code 1/2} означает успешный effect или
 * подтверждение, {@code 0} — несовместимость, {@code -1} — operation не найдена
 * либо ещё не может быть выполнена. Новый result добавляется через FValueFactory
 * и поднимает обычный Linker continuation signal.</p>
 *
 * <p><strong>Binding resolution.</strong> Явный {@link FunctionBinding}
 * исключает случайное shadowing: {@code INFRASTRUCTURE} ищет только built-in
 * catalog, {@code UDF_DYNAMIC} — только Library, {@code LEGACY_AUTO} сохраняет
 * старый порядок built-in → Library. Во всех режимах после exact arity может
 * применяться historical {@code name(0)} fallback. Этот порядок является
 * compatibility contract и не должен меняться как обычная lookup-оптимизация.</p>
 *
 * <p><strong>Predicates.</strong> {@link #execute(Domain)} разрешает exact
 * predicate signature сначала в system catalog, затем в Library. Метод
 * возвращает operation convention без дополнительной интерпретации; решение о
 * Rule branch и fixed-point продолжении принимает вызывающий Linker. Метод
 * {@link #exists(IPredicate)} дополнительно требует {@link LibMode#PREDICATE}.</p>
 *
 * <p><strong>Unresolved arguments.</strong> Function с непустым C-variable
 * argument не передаётся operation и возвращает {@code -1}. Это execution
 * boundary, а не ошибка: binding должен быть завершён последующими проходами
 * Linker или признан unresolved Analyzer.</p>
 *
 * <p><strong>Expansion utility.</strong> {@link #expand(ITerm, ITerm,
 * boolean)} материализует interval, recursive set, string/regex captures и
 * blob chunks в canonical Terms текущего Mind. Zero interval step возвращает
 * пустой результат; no-progress по тому же Term ID останавливает цикл. Direction,
 * endpoint inclusion и chunking являются частью существующей семантики.</p>
 *
 * <p><strong>Каталоги операций.</strong> {@link Functions} и
 * {@link Predicates} принадлежат экземпляру Calculator и используют тот же
 * Mind для создания TValue/Term effects. Их карты являются dispatch metadata,
 * а не Library persistence и не semantic object caches.</p>
 *
 * <p><strong>Concurrency.</strong> Calculator, каталоги и вызываемые Operation
 * привязаны к одному mutable Mind. Параллельное выполнение через один Mind
 * требует внешней сериализации; callbacks Library могут иметь собственные
 * thread-safety ограничения.</p>
 *
 * @see org.kanger.Linker
 * @see org.kanger.Analyzer
 * @see Functions
 * @see Predicates
 */
public class Calculator {

    private final transient Mind mind;
    private final Functions functions;
    private final Predicates predicates;

    public Calculator(Mind mind) {
        this.mind = mind;
        predicates = new Predicates(mind);
        functions = new Functions(mind);
    }


    /**
     * Вычисляет значение функции в контексте принадлежащего калькулятору Mind.
     *
     * @param fu      вычисляемая функция
     * @param logging {@code true}, если требуется диагностическое журналирование
     * @return {@code true}, если вычисление функции успешно завершено
     * @throws Exception если выполнение функции или вложенного вычисления завершилось ошибкой
     */
    public boolean calculate(Function fu, boolean logging) throws Exception {

        boolean result = false;

        for (int i = 0; i < fu.getRange(); ++i) {
            if (fu.getArguments().get(i).getType() == ArgumentType.FUNCTION
                    && ((Function) fu.getArguments().get(i).getObject(mind)).isEmpty(mind)) {
                ((Function) fu.getArguments().get(i).getObject(mind)).clear();
                calculate((Function) fu.getArguments().get(i).getObject(mind), logging);
            }
        }

        if (fu.isEmpty(mind) || !((Term) fu.getResult().getValue(mind)).equalsTo((Term) fu.getValue(mind))) {
            int k = execute(fu);
            if (k == 1 || k == 2) {
                if (fu.isEmpty(mind)) {
                    mind.getFValues().add(fu);
                    result = true;
                    if (logging) {
                        mind.getLog().add(LogMode.ANALYZER, "Calculated function:");
                        mind.getLog().add(LogMode.ANALYZER, String.format("\t%s", fu.toString()));
                    }
                }
            }
        }

        return result;
    }

    /**
     * *********************************************************
     */

    /* Обработка системных предикатов.
     * Возвращает 1 или 0 если предикат возвращает
     * TRUE или FALSE, либо -1 если предикат не
     * системный
     */
    public int execute(Domain d) throws Exception {
        int k = -1;
        String n = d.getPredicate().getName(mind) + "(" + d.getRange() + ")";
        Operation op = predicates.getSysOps().get(n) != null
                ? predicates.getSysOps().get(n)
                : mind.getLibrary().find(n);
        if (op != null) {

            k = (Integer) op.getProc().run(d);
        }
        return k;
    }

    private Operation findInfrastructure(String name, int range) {
        Operation operation = functions.getSysOps().get(name + "(" + range + ")");
        return operation != null ? operation : functions.getSysOps().get(name + "(0)");
    }

    private Operation findUdf(String name, int range) throws Exception {
        Operation operation = mind.getLibrary().find(name + "(" + range + ")");
        return operation != null ? operation : mind.getLibrary().find(name + "(0)");
    }

    private Operation findLegacy(String name, int range) throws Exception {
        String signature = name + "(" + range + ")";
        Operation operation = functions.getSysOps().get(signature);
        if (operation == null) {
            operation = mind.getLibrary().find(signature);
        }
        if (operation == null) {
            String fallback = name + "(0)";
            operation = functions.getSysOps().get(fallback);
            if (operation == null) {
                operation = mind.getLibrary().find(fallback);
            }
        }
        return operation;
    }

    private Operation resolve(Function function) throws Exception {
        String name = function.getName(mind).toString();
        FunctionBinding binding = function.getBinding();
        switch (binding) {
            case INFRASTRUCTURE:
                return findInfrastructure(name, function.getRange());
            case UDF_DYNAMIC:
                return findUdf(name, function.getRange());
            case LEGACY_AUTO:
            default:
                return findLegacy(name, function.getRange());
        }
    }

    public int execute(Function fu) throws Exception {
        int k = -1;
        Operation op = resolve(fu);

        if (op != null) {
            for (IArgument a : fu.getArguments()) {
                if (!a.isEmpty(mind) && a.getValue(mind).isCVariable()) {
                    return -1;
                }
            }
            k = (Integer) op.getProc().run(fu);
        }
        return k;
    }

    public boolean exists(IPredicate p) throws Exception {
        String n = p.getName(mind) + "(" + p.getRange() + ")";
        Operation op = predicates.getSysOps().get(n) != null
                ? predicates.getSysOps().get(n)
                : mind.getLibrary().find(n);
        return op != null && op.getMode() == LibMode.PREDICATE;
    }

    public Functions getFunctions() {
        return functions;
    }

    public Predicates getPredicates() {
        return predicates;
    }

    public List<ITerm> expand(ITerm source, ITerm step, boolean expandString) throws Exception {
        List<ITerm> list = new ArrayList<>();
        Term top = null;
        if (source.getType() == DataType.INTERVAL) {
            if (step != null
                    && step.getValue() instanceof Number
                    && ((Number) step.getValue()).doubleValue() == 0.0d) {
                return list;
            }

            Term min = (Term) ((Collection) source.getValue()).toArray()[0];
            Term max = (Term) ((Collection) source.getValue()).toArray()[1];
            Term cur = min;
            int rc = min.compareTo(max);
            while (true) {
                list.add(cur);
                if (top == null) top = cur;
                if (rc == 0) {
                    break;
                }
                Term next;
                if (step != null) {
                    next = (Term) (rc < 0
                            ? getFunctions()._add(cur, step)
                            : getFunctions()._sub(cur, step));
                } else {
                    next = (Term) (rc < 0
                            ? getFunctions()._inc(cur)
                            : getFunctions()._dec(cur));
                }
                if (next.getId() == cur.getId()) {
                    list.clear();
                    break;
                } else if (rc < 0 && next.compareTo(max) > 0) {
                    break;
                } else if (rc > 0 && next.compareTo(max) < 0) {
                    break;
                } else {
                    cur = next;
                }
            }
        } else if (source.getType() == DataType.SET) {

            for (Term t : (Collection<Term>) source.getValue()) {
                if (t.getType() == DataType.INTERVAL || t.getType() == DataType.SET) {
                    list.addAll(expand(t, null, expandString));
                } else {
                    list.add(t);
                }
            }
        } else if (source.getType() == DataType.STRING) {
            if (step == null) {
                if (expandString) {
                    for (int k = 0; k < source.getValue().toString().length(); ++k) {
                        Term x = (Term) mind.getTerms().add(source.getValue().toString().charAt(k) + "");
                        list.add(x);
                    }
                } else {
                    list.add(source);
                }
            } else {
                Pattern pt = Pattern.compile(step.getValue().toString());
                Matcher mt = pt.matcher(source.getValue().toString());
                while (mt.find()) {
                    for (int k = 0; k < mt.groupCount(); ++k) {
                        Term t = (Term) mind.getTerms().add(mt.group(k + 1) + "");
                        list.add(t);
                    }
                }
            }
        } else if (source.getType() == DataType.BLOB) {
            if (step == null) {
                if (expandString) {
                    for (int k = 0; k < ((byte[]) source.getValue()).length; ++k) {
                        Term x = (Term) mind.getTerms().add(new byte[]{((byte[]) source.getValue())[k]});
                        list.add(x);
                    }
                } else {
                    list.add(source);
                }
            } else {
                int bytes = ((Double) step.getValue()).intValue();
                byte[] cell = null;
                int pos = 0;
                int len = 0;
                int k = 0;
                while (k < ((byte[]) source.getValue()).length) {
                    if (cell == null) {
                        len = Math.min(bytes, ((byte[]) source.getValue()).length - k);
                        if (len > 0) {
                            cell = new byte[len];
                            pos = 0;
                        } else {
                            break;
                        }
                    }
                    if (pos < len) {
                        cell[pos++] = ((byte[]) source.getValue())[k++];
                    } else {
                        Term x = (Term) mind.getTerms().add(cell);
                        list.add(x);
                        cell = null;
                    }
                }
                if (cell != null) {
                    Term x = (Term) mind.getTerms().add(cell);
                    list.add(x);
                }
            }
        } else {
            list.add(source);
        }

        return list;
    }


}
