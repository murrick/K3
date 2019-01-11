package kanger.calculator;

import kanger.User;
import kanger.compiler.SysOp;
import kanger.enums.Enums;
import kanger.enums.LibMode;
import kanger.enums.LogMode;
import kanger.primitives.Argument;
import kanger.units.Domain;
import kanger.units.Function;
import kanger.units.Predicate;

/**
 * Created by murray on 27.05.15.
 */
public class Calculator {

    private User user = null;
    private Functions functions;
    private Predicates predicates;

    public Calculator(User user) {
        this.user = user;
        predicates = new Predicates(user);
        functions = new Functions(user);
    }


    /**
     * Вычисление значения функции
     *
     * @param fu
     * @return
     */
    public boolean calculate(Function fu, boolean logging) /*throws RuntimeErrorException*/ {

        boolean result = false;

        for (int i = 0; i < fu.getRange(); ++i) {
            if (fu.getArguments().get(i).isFSet() && fu.getArguments().get(i).getF().isEmpty()) {
                calculate(fu.getArguments().get(i).getF(), logging);
            }
        }

        int k = execute(fu);
        if (k == 1 || k == 2) {
            if (fu.isEmpty()) {
                user.getMind().getFValues().add(fu);
                result = true;
                if (logging) {
                    user.getMind().getLog().add(LogMode.ANALIZER, "Calculated function:");
                    user.getMind().getLog().add(LogMode.ANALIZER, String.format("\t%s", fu.toString()));
//                    user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
                }
            }
        }

        for (int i = 0; i < fu.getRange(); ++i) {
            if (fu.getArguments().get(i).isFSet() && fu.getArguments().get(i).getF().isEmpty()) {
                calculate(fu.getArguments().get(i).getF(), logging);
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
    public int execute(Domain d) {
        int k = -1;
        Predicate predicate = user.getMind().getPredicates().get(d.getPredicateId());
        String n = predicate.getName() + "(" + predicate.getRange() + ")";
        SysOp op = predicates.getSysOps().get(n) != null ? predicates.getSysOps().get(n) : user.getMind().getLibrary().find(n);
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

    public int execute(Function fu) /*throws RuntimeErrorException*/ {
        int k = -1;
        String n = fu.getName() + "(" + fu.getRange() + ")";
        SysOp op = functions.getSysOps().get(n) != null ? functions.getSysOps().get(n) : user.getMind().getLibrary().find(n);
        if (op != null) {

            if (op.getRange() + 1 > fu.getArguments().size()) {
                fu.getArguments().add(new Argument());
            }

            for (Argument a : fu.getArguments()) {
                if (!a.isEmpty() && (a.getValue().isCVariable() /*|| "$$".equals(a.getValue())*/)) {
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

    public boolean exists(Predicate p) {
        String n = p.getName() + "(" + p.getRange() + ")";
        SysOp op = predicates.getSysOps().get(n) != null ? predicates.getSysOps().get(n) : user.getMind().getLibrary().find(n);
        return op != null && op.getMode() == LibMode.PREDICATE;
    }

    public boolean exists(Function f) {
        String n = f.getName() + "(" + f.getRange() + ")";
        SysOp op = functions.getSysOps().get(n) != null ? functions.getSysOps().get(n) : user.getMind().getLibrary().find(n);
        return op != null && functions.getSysOps().get(n).getMode() == LibMode.FUNCTION;
    }


    private SysOp findOp(String n) {
        if (predicates.getSysOps().containsKey(n))
            return predicates.getSysOps().get(n);
        else if (functions.getSysOps().containsKey(n))
            return functions.getSysOps().get(n);
        else
            return user.getMind().getLibrary().find(n);
    }

    public SysOp find(Object o) {
        if (o instanceof Predicate) {
            String n = ((Predicate) o).getName() + "(" + ((Predicate) o).getRange() + ")";
            return predicates.getSysOps().get(n) != null ? predicates.getSysOps().get(n) : user.getMind().getLibrary().find(n);
        } else if (o instanceof Function) {
            String n = ((Function) o).getName() + "(" + ((Function) o).getRange() + ")";
            return functions.getSysOps().get(n) != null ? functions.getSysOps().get(n) : user.getMind().getLibrary().find(n);
        } else {
            String key = o.toString();
            SysOp op = findOp(key);
            if (op != null) {
                return op;
            } else {
                key = key.trim();
                if (!key.isEmpty() && key.charAt(0) == Enums.ANT || key.charAt(0) == Enums.SUC || key.charAt(0) == Enums.DEL || key.charAt(0) == Enums.INS || key.charAt(0) == Enums.WIPE) {
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

    public boolean unregister(String key) {
        return user.getMind().getLibrary().remove(key);
    }

    public Functions getFunctions() {
        return functions;
    }

    public Predicates getPredicates() {
        return predicates;
    }
}

