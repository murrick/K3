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
 * Created by Dmitry G. Quznetsov on 27.05.15.
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
     * Вычисление значения функции
     *
     * @param fu
     * @return
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

    public int execute(Function fu) throws Exception {
        int k = -1;
        String n = fu.getName(mind) + "(" + fu.getRange() + ")";
        Operation op = functions.getSysOps().get(n) != null
                ? functions.getSysOps().get(n)
                : mind.getLibrary().find(n);

        if (op == null) {
            n = fu.getName(mind) + "(0)";
            op = functions.getSysOps().get(n) != null
                    ? functions.getSysOps().get(n)
                    : mind.getLibrary().find(n);
        }

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
                    list.add(max);
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

