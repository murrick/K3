package kanger.calculator;

import kanger.Mind;
import kanger.compiler.SysOp;
import kanger.enums.Enums;
import kanger.enums.LibMode;
import kanger.enums.LogMode;
import kanger.exception.RuntimeErrorException;
import kanger.primitives.Argument;
import kanger.primitives.Domain;
import kanger.primitives.Function;
import kanger.primitives.Predicate;

//TODO: Следать пересчет функций централизованно отдельным проходом

/**
 * Created by murray on 27.05.15.
 */
public class Calculator {

    private Mind mind = null;
    private Functions functions;
    private Predicates predicates;

    public Calculator(Mind mind) {
        this.mind = mind;
        predicates = new Predicates();
        functions = new Functions(mind);
    }


    /**
     * Вычисление значения функции
     *
     * @param fu
     * @return
     */
    public int calculate(Function fu) /*throws RuntimeErrorException*/ {

        //FArg fu = func.getF();
        int flag = 0;

        if (fu == null) {
            return 0;
        }
//        List<Argument> arg = new ArrayList<>();

        //fu.getArguments();
        //arg.clear();

        /* Проверка наличия всех параметров
         * и заполнение массива
         */
//        int i;
//        fu.setCalculated(false);
        fu.setBusy(true);

//        for (int i = 0; i <= fu.getRange(); ++i) {
//            if (!fu.createCVar(i).isEmpty()) {
//                if (!fu.createCVar(i).getValue().isCVar()) {
//                    fu.createCVar(i).setValue(fu.createCVar(i).getValue());
//                } else {
//                    fu.createCVar(i).setValue(null);
//                }
//            } else if (fu.createCVar(i).isFSet()) {
//                fu.createCVar(i).setValue(fu.createCVar(i).getF().getResult());
//            } else if (fu.createCVar(i).isTSet() && fu.createCVar(i).getT().getOwner() != 0) {
//                fu.createCVar(i).setValue(fu.createCVar(i).getT().getValue());
//            }
//        }
//
        for (int i = 0; i < fu.getRange(); ++i) {
            if (fu.getArguments().get(i).isFSet() /* && !fu.createCVar(i).getF().isBusy()*/) {
//                fu.createCVar(i).getF().setResult(fu.createCVar(i).getValue());
                if (calculate(fu.getArguments().get(i).getF()) > 0) {
                    ++flag;
//                    fu.createCVar(i).setValue(fu.createCVar(i).getF().getResult());
                } else {
                    --flag;
                }
            }
        }

//        // Если еще не добавлен элемент результата - добавляем
//        if (fu.getArguments().size() < fu.getRange() + 1) {
//            fu.getArguments().createTVar(new Argument());
//        }
//        fu.setResult(result);
//        Argument tl = new Argument();
//        arg.createTVar(tl);
//        tl.setC(result);
        //fu.setA(arg);
//        if (!fu.isCalculated(arg)) {
//        flag = execute(fu);
        int k = execute(fu);
        if (k == 1 || k == 2) {
//            if (!"$$".equals(fu.getArguments().get(fu.getRange()).getValue())) {
            ++flag;
            mind.getFValues().add(fu);
//            fu.setResult(null);
//            fu.setCalculated(true);
            mind.getLog().add(LogMode.ANALIZER, "Calculated function:");
            mind.getLog().add(LogMode.ANALIZER, String.format("\t%s", fu.toString()
//                    + (fu.getResult() != null
//                    && fu.isCalculable()
//                    && (mind.getDebugLevel() & Enums.DEBUG_OPTION_VALUES) != 0 ? " = " + fu.getResult() : ""))
            ));
//            } else {
//                mind.getLog().add(LogMode.ANALIZER, "Invalid function result:");
//                mind.getLog().add(LogMode.ANALIZER, String.format("\t%s", fu.toString()
////                    + (fu.getResult() != null
////                    && fu.isCalculable()
////                    && (mind.getDebugLevel() & Enums.DEBUG_OPTION_VALUES) != 0 ? " = " + fu.getResult() : ""))
//                ));
//            }
            mind.getLog().add(LogMode.ANALIZER, "-------------------------------------------");
        }

//            flag = (arg.createCVar(i).isCSet()) ? 1 : 0;
//        flag = (fu.getResult() != null) ? 1 : 0;
//        if(!fu.isCalculated()) {
        for (int i = 0; i < fu.getRange(); ++i) {
            if (fu.getArguments().get(i).isFSet()) {
//                fu.createCVar(i).getF().setResult(fu.createCVar(i).getValue());
                if (calculate(fu.getArguments().get(i).getF()) > 0) {
                    ++flag;
//                    fu.createCVar(i).setValue(fu.createCVar(i).getF().getResult());
                } else {
                    --flag;
                }
            }
        }
//        }

        //TODO: Хочется разделить функции и логику
//        for (int i = 0; i <= fu.getRange(); ++i) {
//            if (fu.createCVar(i).isCSet() && fu.createCVar(i).isTSet() && fu.createCVar(i).getT().getOwner() == 0) {
//                mind.getAnalyser().tSubstitute(fu.createCVar(i).getT(), fu.createCVar(i).getValue(), level, null);
//                flag = 2;
//            }
//        }
//        } else {
//            flag = (fu.getResult() != null) ? 1 : 0;
//        }

        //func.setResult(arg.createCVar(i).getValue());
        fu.setBusy(false);
        return flag;
    }

    /**
     * *********************************************************
     */

    /* Обработка системных предикатов.
     * Возвращает 1 или 0 если предикат возвращает
     * TRUE или FALSE, либо -1 если предикат не
     * системный
     */
    public int execute(Domain d) throws RuntimeErrorException {
        int k = -1;
        String n = d.getPredicate().getName() + "(" + d.getPredicate().getRange() + ")";
        SysOp op = predicates.getSysOps().get(n) != null ? predicates.getSysOps().get(n) : mind.getLibrary().find(n);
        if (op != null) {

//            for (Argument a : d.getArguments()) {
//                if (a.isEmpty() && !a.isTSet() && !a.isFSet()/*&& "$$".equals(a.getValue().toString())*/) {
//                    return -1;
//                }
//            }

            k = (Integer) op.getProc().run(d);

//            if (k == 1 && "_eq".equals(op.getName())) {
//                for(Argument a : d.getArguments()) {
//                    if (a.isFSet()) {
//                        Function f = a.getF();
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
        SysOp op = functions.getSysOps().get(n) != null ? functions.getSysOps().get(n) : mind.getLibrary().find(n);
        if (op != null) {

            if (op.getRange() + 1 > fu.getArguments().size()) {
                fu.getArguments().add(new Argument());
            }

            for (Argument a : fu.getArguments()) {
                if (!a.isEmpty() && (a.getValue().isCVar() /*|| "$$".equals(a.getValue())*/)) {
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
        SysOp op = predicates.getSysOps().get(n) != null ? predicates.getSysOps().get(n) : mind.getLibrary().find(n);
        return op != null && op.getMode() == LibMode.PREDICATE;
    }

    public boolean exists(Function f) {
        String n = f.getName() + "(" + f.getRange() + ")";
        SysOp op = functions.getSysOps().get(n) != null ? functions.getSysOps().get(n) : mind.getLibrary().find(n);
        return op != null && functions.getSysOps().get(n).getMode() == LibMode.FUNCTION;
    }


    private SysOp findOp(String n) {
        if (predicates.getSysOps().containsKey(n))
            return predicates.getSysOps().get(n);
        else if (functions.getSysOps().containsKey(n))
            return functions.getSysOps().get(n);
        else
            return mind.getLibrary().find(n);
    }

    public SysOp find(Object o) {
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

    public void register(SysOp op) {
        mind.getLibrary().add(op);
    }

    public boolean unregister(String key) {
        return mind.getLibrary().remove(key);
    }

}


//TODO: РЕШЕНО !@x (a(x) || b(x)) -> (c(x) -> d(x)) && (e(x) -> f(x));          ? (a(z) && c(z)) -> d(z);
//TODO: РЕШЕНО !@x (a(x) || b(x)) -> (c(x) -> d(x)) && (e(x) -> f(x));          ? b(z) -> d(z);
//TODO: РЕШЕНО !@x (a(x) || b(x)) -> (c(x) -> d(x)) && (e(x) -> f(x)); !e (z);  ? $x f(x);

//TODO: РЕШЕНО !@x ~a(x,x); !@x $y a(y,x);         ?$x @y a(x,y);
//TODO: РЕШЕНО !@x ~a(x,x); !@x $y a(y,x);         ? ~($x @y a(x,y));
//TODO: РЕШЕНО !@x ~a(x,x); !@x $y a(y,x);         ?@x $y a(y,x);
//TODO: РЕШЕНО !@x ~a(x,x); !@x $y a(y,x);         ?$x @y a(x,y);
//TODO: РЕШЕНО !@x ~a(x,x); !@x $y a(y,x);         ?~($x ~($y a(y,x)));


//TODO: РЕШЕНО !(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); !@x a(x) -> ~n(x); !a(nnn); !b(ooo); !d(v);      ?a(nnn);
//TODO: РЕШЕНО !(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); !@x a(x) -> ~n(x); !a(nnn); !b(ooo); !d(v);      ?n(nnn);
//TODO: РЕШЕНО !(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); !@x a(x) -> ~n(x); !a(nnn); !b(ooo); !d(v);      ?a(xx);          -- ЛИШНИЕ ГИПОТЕЗЫ прибить в фазе первого линка
//TODO: РЕШЕНО !(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); !@x a(x) -> ~n(x); !a(nnn); !b(ooo); !d(v);      ?b(xx);
//TODO: РЕШЕНО !(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); !@x a(x) -> ~n(x); !a(nnn); !b(ooo); !d(v);      ?c(xx);
//TODO: РЕШЕНО !(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); !@x a(x) -> ~n(x); !a(nnn); !b(ooo); !d(v);      ?d(xx);
//TODO: РЕШЕНО !(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); !@x a(x) -> ~n(x); !a(nnn); !b(ooo); !d(v);      ?n(xx);
//TODO: РЕШЕНО !(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); !@x a(x) -> ~n(x); !a(nnn); !b(ooo); !d(v);      ?$x c(x);
//TODO: РЕШЕНО !(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); !@x a(x) -> ~n(x); !a(nnn); !b(ooo); !d(v);      ?$x d(x);
//TODO: РЕШЕНО !(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); !@x a(x) -> ~n(x); !a(nnn); !b(ooo); !d(v);      ?a(nn) -> b(nn);
//TODO: РЕШЕНО !(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); !@x a(x) -> ~n(x); !a(nnn); !b(ooo); !d(v);      ?a(nn) -> c(nn);
//TODO: РЕШЕНО !(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); !@x a(x) -> ~n(x); !a(nnn); !b(ooo); !d(v);      ?a(nn) -> d(nn);
//TODO: РЕШЕНО !(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); !@x a(x) -> ~n(x); !a(nnn); !b(ooo); !d(v);      ?$x a(x) && d(x);
//TODO: РЕШЕНО !(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); !@x a(x) -> ~n(x); !a(nnn); !b(ooo); !d(v);      ?$x a(x) || d(x);

//TODO: РЕШЕНО ?2 > 3;
//TODO: РЕШЕНО ?2 < 3;
//TODO: РЕШЕНО ?2 = 3;
//TODO: РЕШЕНО ?2 = 2;
//TODO: РЕШЕНО ?$x x=5;
//TODO: РЕШЕНО ?~$x x=5;
//TODO: РЕШЕНО ?$x ((x+3)*15)=965; --- не выводит результат
//TODO: РЕШЕНО ?$x $y (12+y)*2=256 && x=5*y;
//TODO: РЕШЕНО ?$x $y x + y = 12; --- выводит TRUE
//TODO: РЕШЕНО !num(0); !@x num(x) && x < 10 -> num(++x);      ?$x num(x);      --- ВКЛЮЧАЕТ 10
//TODO: РЕШЕНО !num(0); !@x num(x) && x < 10 -> num(++x);      ?$x num(x) && x > 5;
//TODO: РЕШЕНО !num(0); !@x num(x) && x < 10 -> num(++x);      ?$x $y num(x) && num(y) && x + y = 7;
