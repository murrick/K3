package org.kanger.calculator;

import org.kanger.Mind;
import org.kanger.enums.ArgumentType;
import org.kanger.enums.Enums;
import org.kanger.enums.LibMode;
import org.kanger.enums.LogMode;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.primitives.Argument;
import org.kanger.units.Domain;
import org.kanger.units.Function;
import org.kanger.units.Predicate;
import org.kanger.units.SysOp;

import java.io.IOException;

/**
 * Created by Dmitry G. Qusnetsov on 27.05.15.
 */
public class Calculator {

    private Mind mind = null;
    private Functions functions;
    private Predicates predicates;

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
            if (fu.getArguments().get(i).isFSet() && fu.getArguments().get(i).getF().isEmpty()) {
                fu.getArguments().get(i).getF().clear();
                calculate(fu.getArguments().get(i).getF(), logging);
            }
        }
//        }

        if (fu.isEmpty() || !fu.getResult().getValue().equalsTo(fu.getValue())) {
            int k = execute(fu);
            if (k == 1 || k == 2) {
                if (fu.isEmpty()) {
                    mind.getFValues().add(fu);
                    result = true;
                    if (logging) {
                        mind.getLog().add(LogMode.ANALIZER, "Calculated function:");
                        mind.getLog().add(LogMode.ANALIZER, String.format("\t%s", fu.toString()));
//                    mind.getLog().add(LogMode.ANALIZER, "-------------------------------------------");
                    }
                }
            }
        }

//        for (int i = 0; i < fu.getRange(); ++i) {
//            if (fu.getArguments().get(i).isFSet() && fu.getArguments().get(i).getF().isEmpty()) {
//                calculate(fu.getArguments().get(i).getF(), logging);
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
        SysOp op = predicates.getSysOps().get(n) != null
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
        SysOp op = functions.getSysOps().get(n) != null
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

            for (Argument a : fu.getArguments()) {
                if (!a.isEmpty() && a.getType() == ArgumentType.CVARIABLE) {
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

    public boolean exists(Predicate p) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        String n = p.getName() + "(" + p.getRange() + ")";
        SysOp op = predicates.getSysOps().get(n) != null
                ? predicates.getSysOps().get(n)
                : mind.getLibrary().find(n);
        return op != null && op.getMode() == LibMode.PREDICATE;
    }

    public boolean exists(Function f) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        String n = f.getName() + "(" + f.getRange() + ")";
        SysOp op = functions.getSysOps().get(n) != null
                ? functions.getSysOps().get(n)
                : mind.getLibrary().find(n);
        return op != null && functions.getSysOps().get(n).getMode() == LibMode.FUNCTION;
    }


    private SysOp findOp(String n) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        if (predicates.getSysOps().containsKey(n))
            return predicates.getSysOps().get(n);
        else if (functions.getSysOps().containsKey(n))
            return functions.getSysOps().get(n);
        else
            return mind.getLibrary().find(n);
    }

    public SysOp find(Object o) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        if (o instanceof Predicate) {
            String n = ((Predicate) o).getName() + "(" + ((Predicate) o).getRange() + ")";
            return predicates.getSysOps().get(n) != null ? predicates.getSysOps().get(n) : mind.getLibrary().find(n);
        } else if (o instanceof Function) {
            String n = ((Function) o).getName() + "(" + ((Function) o).getRange() + ")";
            return functions.getSysOps().get(n) != null ? functions.getSysOps().get(n) : mind.getLibrary().find(n);
        } else {
            String key = o.toString();
            SysOp op = findOp(key);
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

