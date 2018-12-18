package kanger.calculator;

import kanger.User;
import kanger.compiler.SysOp;
import kanger.enums.DataType;
import kanger.enums.LibMode;
import kanger.interfaces.IRunnable;
import kanger.primitives.ArgList;
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


    private User user = null;
    private final Map<String, SysOp> sysOps = new HashMap<String, SysOp>() {

        /// Системные предикаты
        {

            put("_eq(2)", new SysOp(LibMode.PREDICATE, "_eq", 2, new IRunnable() {

                @Override
                public Object run(Object o) {
                    int i = -1;
                    ArgList arg = ((Domain) o).getArguments();
                    if (arg.get(0).isDefined() && !arg.get(1).isDefined()) {
                        if (arg.get(1).setValue(arg.get(0).getValue())) {
                            i = 1;
                        }
                    } else if (!arg.get(0).isDefined() && arg.get(1).isDefined()) {
                        if (arg.get(0).setValue(arg.get(1).getValue())) {
                            i = 1;
                        }
                    } else if (!arg.get(0).isEmpty() && !arg.get(1).isEmpty()) {
                        if (arg.get(0).getValue().compareTo(arg.get(1).getValue()) == 0) {
                            i = 1;
                        } else { //if ((arg.createCVar(0).getValue().isCVariable() && arg.createCVar(1).getValue().isCVariable()) || (!arg.createCVar(0).getValue().isCVariable() && !arg.createCVar(1).getValue().isCVariable())) {
                            i = 0;
                        }
//                        else //if(!arg.createCVar(0).getValue().isCVariable() && !arg.createCVar(1).getValue().isCVariable())
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
                    ArgList arg = ((Domain) o).getArguments();
                    if (!arg.get(0).isEmpty() && !arg.get(1).isEmpty()) {
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
                    ArgList arg = ((Domain) o).getArguments();
                    if (!arg.get(0).isEmpty() && !arg.get(1).isEmpty() && !arg.get(0).getValue().isCVariable() && !arg.get(1).getValue().isCVariable()) {
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
                    ArgList arg = ((Domain) o).getArguments();
                    if (!arg.get(0).isEmpty() && !arg.get(1).isEmpty() && !arg.get(0).getValue().isCVariable() && !arg.get(1).getValue().isCVariable()) {
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
                    ArgList arg = ((Domain) o).getArguments();
                    if (!arg.get(0).isEmpty() && !arg.get(1).isEmpty() && !arg.get(0).getValue().isCVariable() && !arg.get(1).getValue().isCVariable()) {
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
                    ArgList arg = ((Domain) o).getArguments();
                    if (!arg.get(0).isEmpty() && !arg.get(1).isEmpty() && !arg.get(0).getValue().isCVariable() && !arg.get(1).getValue().isCVariable()) {
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
                    ArgList arg = ((Domain) o).getArguments();
                    if (!arg.get(0).isDefined() && arg.get(1).isDefined()) {
                        if (arg.get(1).getValue().getType() == DataType.INTERVAL
                                && arg.get(1).getValue().getValue() instanceof Collection
                                && ((Collection) arg.get(1).getValue().getValue()).size() == 2) {

                            Term min = (Term) ((Collection) arg.get(1).getValue().getValue()).toArray()[0];
                            Term max = (Term) ((Collection) arg.get(1).getValue().getValue()).toArray()[1];
                            Term cur = min;
                            int rc = min.compareTo(max);
                            while (true) {
                                if (arg.get(0).setValue(cur)) {
                                    i = 1;
                                    Term next = rc < 0
                                            ? new Calculator(user).getFunctions()._inc(cur)
                                            : new Calculator(user).getFunctions()._dec(cur);
                                    if (next.getId() == cur.getId()) {
                                        if (arg.get(0).setValue(max)) {
                                            i = 1;
                                        }
                                        break;
                                    } else if (rc < 0 && next.compareTo(max) > 0) {
                                        break;
                                    } else if (rc > 0 && next.compareTo(max) < 0) {
                                        break;
                                    } else {
                                        cur = next;
                                    }
                                } else {
                                    break;
                                }
                            }
                        } else if (arg.get(1).getValue().getType() == DataType.STRING) {
                            for (int k = 0; k < arg.get(1).getValue().toString().length(); ++k) {
                                arg.get(0).setValue(user.getMind().getTerms().add(arg.get(1).getValue().toString().charAt(k) + ""));
                            }
                        }
                    } else if (!arg.get(0).isEmpty() && !arg.get(1).isEmpty() && !arg.get(0).getValue().isCVariable() && !arg.get(1).getValue().isCVariable()) {
                        if (arg.get(1).getValue().getType() == DataType.INTERVAL
                                && arg.get(1).getValue().getValue() instanceof Collection
                                && ((Collection) arg.get(1).getValue().getValue()).size() == 2) {
                            i = _in(arg.get(0).getValue(),
                                    (Term) ((Collection) arg.get(1).getValue().getValue()).toArray()[0],
                                    (Term) ((Collection) arg.get(1).getValue().getValue()).toArray()[1]) ? 1 : 0;
                        } else if (arg.get(1).getValue().getType() == DataType.STRING) {
                            i = (arg.get(1).getValue().getValue().toString().contains(arg.get(0).getValue().getValue().toString()) ||
                                    Pattern.matches((String) arg.get(0).getValue().getValue(), (String) arg.get(1).getValue().getValue())) ? 1 : 0;
                        }
                    }
                    return i;
                }
            }));
        }

        {
            put("_in(3)", new SysOp(LibMode.PREDICATE, "_in", 3, new IRunnable() {
                public Object run(Object o) {
                    int i = -1;
                    ArgList arg = ((Domain) o).getArguments();
                    if (!arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()) {
                        if (arg.get(1).getValue().getType() == DataType.INTERVAL
                                && arg.get(1).getValue().getValue() instanceof Collection
                                && ((Collection) arg.get(1).getValue().getValue()).size() == 2) {

                            Term min = (Term) ((Collection) arg.get(1).getValue().getValue()).toArray()[0];
                            Term max = (Term) ((Collection) arg.get(1).getValue().getValue()).toArray()[1];
                            Term cur = min;
                            Term step = arg.get(2).getValue();
                            int rc = min.compareTo(max);
                            while (true) {
                                if (arg.get(0).setValue(cur)) {
                                    i = 1;
                                    Term next = rc < 0
                                            ? new Calculator(user).getFunctions()._add(cur, step)
                                            : new Calculator(user).getFunctions()._sub(cur, step);
                                    if (next.getId() == cur.getId()) {
                                        if (arg.get(0).setValue(max)) {
                                            i = 1;
                                        }
                                        break;
                                    } else if (rc < 0 && next.compareTo(max) > 0) {
                                        break;
                                    } else if (rc > 0 && next.compareTo(max) < 0) {
                                        break;
                                    } else {
                                        cur = next;
                                    }
                                } else {
                                    break;
                                }
                            }
                        }
                    } else if (!arg.get(0).isEmpty() && !arg.get(1).isEmpty() && !arg.get(0).getValue().isCVariable() && !arg.get(1).getValue().isCVariable()) {
                        if (arg.get(1).getValue().getType() == DataType.INTERVAL
                                && arg.get(1).getValue().getValue() instanceof Collection
                                && ((Collection) arg.get(1).getValue().getValue()).size() == 2) {
                            i = _in(arg.get(0).getValue(),
                                    (Term) ((Collection) arg.get(1).getValue().getValue()).toArray()[0],
                                    (Term) ((Collection) arg.get(1).getValue().getValue()).toArray()[1]) ? 1 : 0;
                        }
                    }
                    return i;
                }
            }));
        }

        //TODO: Добавить ret значение 2 для всех заполненных и совпадающих полей
//        {
//            put("_match(2)", new SysOp(LibMode.PREDICATE, "_match", 2, new IRunnable() {
//                public Object run(Object o) {
//                    int i = -1;
//                    ArgList arg = ((Domain) o).getArguments();
//                    if (!arg.get(0).isEmpty() && !arg.get(1).isEmpty()) {
//                        if (arg.get(0).getValue().isCVariable() || arg.get(1).getValue().isCVariable()) {
//                            i = -1;
//                        } else {
////                            i = maskcmp(arg.createCVar(0).getValue().getTerm().getName(), arg.createCVar(1).getValue().getTerm().getName()) == 0 ? 1 : 0;
//                            try {
//                                i = Pattern.matches((String) arg.get(0).getValue().getValue(), (String) arg.get(1).getValue().getValue()) ? 1 : 0;
//                            } catch (PatternSyntaxException ex) {
//                                System.err.println("Regexp error: " + ex.getDescription());
//                            }
//                        }
//                    }
//                    return i;
//                }
//            }));
//        }

    };

    public Predicates(User user) {
        this.user = user;
    }

    public Map<String, SysOp> getSysOps() {
        return sysOps;
    }

    public boolean _in(Term cur, Term min, Term max) {
        int rcmin = -2;
        int rcmax = -2;
        int i = -1;
        int rc = min.compareTo(max);
        if (cur.getType() == DataType.INTERVAL && cur.getValue() instanceof Collection && ((Collection) cur.getValue()).size() == 2) {
            Term xmin = (Term) ((Collection) cur.getValue()).toArray()[0];
            Term xmax = (Term) ((Collection) cur.getValue()).toArray()[1];
            int xrc = xmin.compareTo(xmax);
            rcmin = rc == xrc ? xmin.compareTo(min) : xmin.compareTo(max);
            rcmax = rc == xrc ? xmax.compareTo(max) : xmax.compareTo(min);
        } else {
            rcmin = cur.compareTo(min);
            rcmax = cur.compareTo(max);
        }
        if (rcmin != -2 && rcmax != -2 && rc != -2) {
            return (rc < 0 ? (rcmin >= 0 && rcmax <= 0) : (rcmin <= 0 && rcmax >= 0));
        } else {
            return false;
        }
    }
}
