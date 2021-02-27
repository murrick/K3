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
import org.kanger.enums.Enums;
import org.kanger.enums.LibMode;
import org.kanger.enums.LogMode;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IPredicate;
import org.kanger.units.*;

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

//        if(fu.isEmpty()) {
        for (int i = 0; i < fu.getRange(); ++i) {
            if (fu.getArguments().get(i).getType() == ArgumentType.FUNCTION
                    && ((Function) fu.getArguments().get(i).getObject(mind)).isEmpty()) {
                ((Function) fu.getArguments().get(i).getObject(mind)).clear();
                calculate((Function) fu.getArguments().get(i).getObject(mind), logging);
            }
        }
//        }

        if (fu.isEmpty() || !((Term) fu.getResult().getValue(mind)).equalsTo((Term) fu.getValue())) {
            int k = execute(fu);
            if (k == 1 || k == 2) {
                if (fu.isEmpty()) {
                    mind.getFValues().add(fu);
                    result = true;
                    if (logging) {
                        mind.getLog().add(LogMode.ANALYZER, "Calculated function:");
                        mind.getLog().add(LogMode.ANALYZER, String.format("\t%s", fu.toString()));
//                    mind.getLog().add(LogMode.ANALIZER, "-------------------------------------------");
                    }
                }
            }
        }

//        for (int i = 0; i < fu.getRange(); ++i) {
//            if (fu.getArguments().get(i).isFSet() && fu.getArguments().get(i).getF(mind).isEmpty()) {
//                calculate(fu.getArguments().get(i).getF(mind), logging);
//            }
//        }
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
        String n = d.getPredicate().getName() + "(" + d.getRange() + ")";
        Operation op = predicates.getSysOps().get(n) != null
                ? predicates.getSysOps().get(n)
                : mind.getLibrary().find(n);
        if (op != null) {

//            for (Argument a : d.getArguments()) {
//                if (a.isEmpty() && !a.isTVariable() && !a.isFunction()/*&& "$$".equals(a.getValue().toString())*/) {
//                    return -1;
//                }
//            }

            k = (Integer) op.getProc().run(d);

//            if (k == 1 && "_eq".equals(op.getName())) {
//                for(Argument a : d.getArguments()) {
//                    if (a.isFunction()) {
//                        Function f = a.getFunction();
//                        if (calculate(f) <= 0) {
//                            k = -1;
//                        }
//                    }
//                }
//            }
        }
        return k;
    }

    public int execute(Function fu) throws Exception {
        int k = -1;
        String n = fu.getName() + "(" + fu.getRange() + ")";
        Operation op = functions.getSysOps().get(n) != null
                ? functions.getSysOps().get(n)
                : mind.getLibrary().find(n);

        if (op == null) {
            n = fu.getName() + "(0)";
            op = functions.getSysOps().get(n) != null
                    ? functions.getSysOps().get(n)
                    : mind.getLibrary().find(n);
        }

        if (op != null) {

//            if (op.getRange() + 1 > fu.getArguments().size()) {
//                fu.getArguments().add(new Argument());
//            }

            for (IArgument a : fu.getArguments()) {
                if (!a.isEmpty(mind) && a.getValue(mind).isCVariable()) {
//                    fu.setResult(mind.getTerms().add("$$"));
                    return -1;
                }
            }

//            if ("$$".equals(fu.getResult())) {
//                return -1;
//            }

            k = (Integer) op.getProc().run(fu);
//            fu.getArguments().remove(op.getRange());
        }
        return k;
    }

    public boolean exists(IPredicate p) throws Exception {
        String n = p.getName() + "(" + p.getRange() + ")";
        Operation op = predicates.getSysOps().get(n) != null
                ? predicates.getSysOps().get(n)
                : mind.getLibrary().find(n);
        return op != null && op.getMode() == LibMode.PREDICATE;
    }

    public boolean exists(Function f) throws Exception {
        String n = f.getName() + "(" + f.getRange() + ")";
        Operation op = functions.getSysOps().get(n) != null
                ? functions.getSysOps().get(n)
                : mind.getLibrary().find(n);
        return op != null && functions.getSysOps().get(n).getMode() == LibMode.FUNCTION;
    }


    private Operation findOp(String n) throws Exception {
        if (predicates.getSysOps().containsKey(n))
            return predicates.getSysOps().get(n);
        else if (functions.getSysOps().containsKey(n))
            return functions.getSysOps().get(n);
        else
            return mind.getLibrary().find(n);
    }

    public Operation find(Object o) throws Exception {
        if (o instanceof Predicate) {
            String n = ((Predicate) o).getName() + "(" + ((Predicate) o).getRange() + ")";
            return predicates.getSysOps().get(n) != null ? predicates.getSysOps().get(n) : mind.getLibrary().find(n);
        } else if (o instanceof Function) {
            String n = ((Function) o).getName() + "(" + ((Function) o).getRange() + ")";
            return functions.getSysOps().get(n) != null ? functions.getSysOps().get(n) : mind.getLibrary().find(n);
        } else {
            String key = o.toString();
            Operation op = findOp(key);
            if (op != null) {
                return op;
            } else {
                key = key.trim();
                if (!key.isEmpty() && key.charAt(0) == Enums.ANT || key.charAt(0) == Enums.SUC || key.charAt(0) == Enums.DEL || key.charAt(0) == Enums.INS /*|| key.charAt(0) == Enums.WIPE*/) {
                    key = key.substring(1);
                }
                if (!key.isEmpty() && key.charAt(key.length() - 1) == Enums.EOLN) {
                    key = key.substring(0, key.length() - 1);
                }
                op = findOp(key);
                if (op != null) {
                    return op;
                } else if (key.contains("(") && key.contains(")") && key.split("\\(").length > 0) {
                    String n = key.split("\\(")[0];
                    int range = key.split("\\(")[1].split("\\)")[0].split(",").length;
                    if (range == 1 && key.split("\\(")[1].split("\\)")[0].split(",")[0].trim().isEmpty()) {
                        range = 0;
                    }
                    return findOp(n + "(" + range + ")");
                } else {
                    return findOp(key + "(0)");
                }
            }
        }
    }

//    public void register(SysOp op) {
//        mind.getLibrary().add(op);
//    }

//    public boolean unregister(String key) {
//        return mind.getLibrary().remove(key);
//    }

    public Functions getFunctions() {
        return functions;
    }

    public Predicates getPredicates() {
        return predicates;
    }

}

