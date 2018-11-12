package kanger.calculator;

import kanger.compiler.SysOp;
import kanger.enums.DataType;
import kanger.enums.LibMode;
import kanger.interfaces.IRunnable;
import kanger.primitives.Argument;
import kanger.primitives.Domain;
import kanger.primitives.Term;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Created by murray on 18.01.17.
 */
public class Predicates {
    private final Map<String, SysOp> sysOps = new HashMap<String, SysOp>() {

        /// Системные предикаты
        {

            put("_eq(2)", new SysOp(LibMode.PREDICATE, "_eq", 2, new IRunnable() {

                @Override
                public Object run(Object o) {
                    int i = -1;
                    List<Argument> arg = ((Domain) o).getArguments();
                    if (arg.get(0).isDefined() && !arg.get(1).isDefined()) {
                        if (arg.get(1).setValue(arg.get(0).getValue())) {
                            i = 1;
                        }
                    } else if (!arg.get(0).isDefined() && arg.get(1).isDefined()) {
                        if (arg.get(0).setValue(arg.get(1).getValue())) {
                            i = 1;
                        }
                    } else if (arg.get(0).isCalculated() && arg.get(1).isCalculated()) {
                        if (arg.get(0).getValue().compareTo(arg.get(1).getValue()) == 0) {
                            i = 1;
                        } else { //if ((arg.createCVar(0).getValue().isCVar() && arg.createCVar(1).getValue().isCVar()) || (!arg.createCVar(0).getValue().isCVar() && !arg.createCVar(1).getValue().isCVar())) {
                            i = 0;
                        }
//                        else //if(!arg.createCVar(0).getValue().isCVar() && !arg.createCVar(1).getValue().isCVar())
//                            i = 0;
                    }
                    return i;
                }
            }));
        }


        {
            put("_ne(2)", new SysOp(LibMode.PREDICATE, "_ne", 2, new IRunnable() {
                public Object run(Object o) {
                    int i = -1;
                    List<Argument> arg = ((Domain) o).getArguments();
                    if (arg.get(0).isCalculated() && arg.get(1).isCalculated()) {
                        int rc = arg.get(0).getValue().compareTo(arg.get(1).getValue());
                        if (rc == -1 || rc == 1) {
                            i = 1;
                        } else if (rc == 0) {
                            i = 0;
                        }
                    }
                    return i;
                }
            }));
        }

        {
            put("_gr(2)", new SysOp(LibMode.PREDICATE, "_gr", 2, new IRunnable() {
                public Object run(Object o) {
                    int i = -1;
                    List<Argument> arg = ((Domain) o).getArguments();
                    if (arg.get(0).isCalculated() && arg.get(1).isCalculated() && !arg.get(0).getValue().isCVar() && !arg.get(1).getValue().isCVar()) {
                        int rc = arg.get(0).getValue().compareTo(arg.get(1).getValue());
                        if (rc != -2) {
                            i = rc > 0 ? 1 : 0;
                        }
                    }
                    return i;
                }
            }));
        }

        {
            put("_ge(2)", new SysOp(LibMode.PREDICATE, "_ge", 2, new IRunnable() {
                public Object run(Object o) {
                    int i = -1;
                    List<Argument> arg = ((Domain) o).getArguments();
                    if (arg.get(0).isCalculated() && arg.get(1).isCalculated() && !arg.get(0).getValue().isCVar() && !arg.get(1).getValue().isCVar()) {
                        int rc = arg.get(0).getValue().compareTo(arg.get(1).getValue());
                        if (rc != -2) {
                            i = rc >= 0 ? 1 : 0;
                        }
                    }
                    return i;
                }
            }));
        }

        {
            put("_lr(2)", new SysOp(LibMode.PREDICATE, "_lr", 2, new IRunnable() {
                public Object run(Object o) {
                    int i = -1;
                    List<Argument> arg = ((Domain) o).getArguments();
                    if (arg.get(0).isCalculated() && arg.get(1).isCalculated() && !arg.get(0).getValue().isCVar() && !arg.get(1).getValue().isCVar()) {
                        int rc = arg.get(0).getValue().compareTo(arg.get(1).getValue());
                        if (rc != -2) {
                            i = rc < 0 ? 1 : 0;
                        }
                    }
                    return i;
                }
            }));
        }

        {
            put("_le(2)", new SysOp(LibMode.PREDICATE, "_le", 2, new IRunnable() {
                public Object run(Object o) {
                    int i = -1;
                    List<Argument> arg = ((Domain) o).getArguments();
                    if (arg.get(0).isCalculated() && arg.get(1).isCalculated() && !arg.get(0).getValue().isCVar() && !arg.get(1).getValue().isCVar()) {
                        int rc = arg.get(0).getValue().compareTo(arg.get(1).getValue());
                        if (rc != -2) {
                            i = rc <= 0 ? 1 : 0;
                        }
                    }
                    return i;
                }
            }));
        }

        {
            put("_in(2)", new SysOp(LibMode.PREDICATE, "_in", 2, new IRunnable() {
                public Object run(Object o) {
                    int i = -1;
                    List<Argument> arg = ((Domain) o).getArguments();
                    if (arg.get(0).isCalculated() && arg.get(1).isCalculated() && !arg.get(0).getValue().isCVar() && !arg.get(1).getValue().isCVar()) {
                        if (arg.get(1).getValue().getType() == DataType.INTERVAL
                                && arg.get(1).getValue().getValue() instanceof Collection
                                && ((Collection) arg.get(1).getValue().getValue()).size() == 2) {

                            Term min = (Term) ((Collection) arg.get(1).getValue().getValue()).toArray()[0];
                            Term max = (Term) ((Collection) arg.get(1).getValue().getValue()).toArray()[1];
                            int rc = min.compareTo(max);
                            int rcmin = arg.get(0).getValue().compareTo(min);
                            int rcmax = arg.get(0).getValue().compareTo(max);
                            if (rcmin != -2 && rcmax != -2 && rc != -2) {
                                i = (rc > 0 ? (rcmin >= 0 && rcmax < 0) : (rcmin <= 0 && rcmax > 0)) ? 1 : 0;
                            }
                        }
                    }
                    return i;
                }
            }));
        }

        //TODO: Добавить ret значение 2 для всех заполненных и совпадающих полей
        {
            put("match(2)", new SysOp(LibMode.PREDICATE, "match", 2, new IRunnable() {
                public Object run(Object o) {
                    int i = -1;
                    List<Argument> arg = ((Domain) o).getArguments();
                    if (arg.get(0).isCalculated() && arg.get(1).isCalculated()) {
                        if (arg.get(0).getValue().isCVar() || arg.get(1).getValue().isCVar()) {
                            i = -1;
                        } else {
//                            i = maskcmp(arg.createCVar(0).getValue().getTerm().getName(), arg.createCVar(1).getValue().getTerm().getName()) == 0 ? 1 : 0;
                            try {
                                i = Pattern.matches((String) arg.get(0).getValue().getValue(), (String) arg.get(1).getValue().getValue()) ? 1 : 0;
                            } catch (PatternSyntaxException ex) {
                                System.err.println("Regexp error: " + ex.getDescription());
                            }
                        }
                    }
                    return i;
                }
            }));
        }

    };

    public Map<String, SysOp> getSysOps() {
        return sysOps;
    }
}
