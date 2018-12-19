package kanger.primitives;

import kanger.User;
import kanger.compiler.Operation;
import kanger.compiler.Parser;
import kanger.enums.Enums;
import kanger.enums.Tools;
import kanger.interfaces.IValue;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Dmitry G. Qusnetsov on 26.05.15.
 * <p>
 * Домен для функции. Может быть рекурсивным на уровне структуры TList.
 */
public class Function implements IValue {

    private long id = -1;
    private Term name = null;
    private int range = 0;
    private ArgList arguments = new ArgList();     // Параметры

    private Function next = null;
    private User user = null;

    public Function(User user) {
        this.user = user;
    }

    public Function readCompiledData(DataInputStream dis) throws IOException {
        this.user = user;
        id = dis.readLong();
        name = user.getMind().getTerms().get(dis.readLong());
        range = dis.readInt();
        arguments = new ArgList(dis, user);
        return this;
    }

    public void writeCompiledData(DataOutputStream dos, User user) throws IOException {
        dos.writeLong(id);
        dos.writeLong(name.getId());
        dos.writeInt(range);
        arguments.writeCompiledData(dos, user);
    }


    public void setId(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    public void setNext(Function next) {
        this.next = next;
    }

    public Function getNext() {
        return next;
    }

    public int getRange() {
        return range;
    }

    public void setRange(int range) {
        this.range = range;
    }

    public ArgList getArguments() {
        return arguments;
    }

    public Term getValue() {
        FValue c = getCurrent();
        if (c != null) {
            return getCurrent().getValue();
        } else {
            return null;
        }
    }


    public Object setValue(Term r) {
        while (range + 1 > arguments.size()) {
            arguments.add(new Argument());
        }
        arguments.get(range).setValue(r);
        return arguments.get(range);
    }

    public boolean setParameter(int i, Term r) {
//        if(i == range) {
//            TSubst s = setResult(r);
//            s.setSolves(owner, owner);
//            return true;
//        } else {

        return arguments.get(i).setValue(r);
//        }
    }

//    public boolean isCalculated() {
//        int i = 0;
//        for (; i <= range; ++i) {
//            if (arguments.createCVar(i) == null || !arguments.createCVar(i).isCSet()) {
//                return false;
//            }
//        }
//        return true;
//    }


    public Term getName() {
        return name;
    }

    public void setName(Term name) {
        this.name = name;
    }

    private String formatParam(Argument t) {
        Operation op = Parser.getOp(name.toString(), range);
        boolean isOp = op != null && op.getRange() == range;
        String s = "";
        if (t.isFSet()) {
            s += (isOp ? "(" : "") + t.getF().toString() + (isOp ? ")" : "");
        } else if (t.isTSet()) {
//            if (v == null) {
            s += t.getT().toString();
//            } else {
//                TValue tv = v.getValue(t.getTVariable());
//                s += t.getTVariable().getVarName()
//                        + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_VALUES) != 0 ? (tv == null ? "" : (":" + tv.getValue().toString())) : "")
//                        + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_STATUS) != 0 && tv != null && tv.isBlocked() ? " (B)" : "");
//
//            }
        } else if (!t.isEmpty()) {
            s += t.getValue().toString();
        } else {
            s += "_";
        }
        return s;
    }

    public String toString() {
        if (!isCalculable() && getValue() != null) {
            return getValue().toString();
        } else {
            Operation op = Parser.getOp(name.toString(), range);
            String s = "";
            if (op == null || op.getRange() != range) {
                s = String.format("%s(", name.toString());
                for (int i = 0; i < range; ++i) {
                    s += formatParam(arguments.get(i));
                    if (i + 1 < range) {
                        s += (char) Enums.COMMA;
                    }
                }
                s += ")";
            } else if (op.getRange() == 1) {
                if (op.isPost()) {
                    s = formatParam(arguments.get(0)) + op.getName();
                } else {
                    s = op.getName() + formatParam(arguments.get(0));
                }
            } else {
                for (int i = 0; i < op.getRange(); ++i) {
                    s += formatParam(arguments.get(i));
                    if (i + 1 < op.getRange()) {
                        s += " " + op.getName() + " ";
                    }
                }
            }

            String res = "";
            if ((user.getMind().getDebugLevel() & Enums.DEBUG_OPTION_VALUES) != 0) {
//                if (getResult() != null) {
                if (getCurrent() != null) {
                    res = " {= " + getValue() + "}";
                } else if (arguments.size() > range && !arguments.get(range).isEmpty()) {
                    res = " [= " + arguments.get(range).getValue() + "]";
                }
            }
            //Argument r = range < arguments.size() ? arguments.createCVar(range) : null;
            return s + res;
        }
    }


    //    public void setResult(Term c) {
//        if(f != null) {
//            while(f.getRange() >= arguments.size()) {
//                arguments.createTVar(new TList());
//            }
//            arguments.createCVar(f.getRange()).setC(c);
//        }
//
//    }
//    @Override
//    public boolean equals(Object o) {
//        if (o == null || !(o instanceof Function)) {
//            return false;
//        } else {
//            Function fo = (Function) o;
//            if (!fo.name.equals(name)) {
//                return false;
//            }
//            if (range != fo.getRange()) {
//                return false;
//            }
//            if (fo.arguments.size() != arguments.size()) {
//                return false;
//            }
//            for (int i = 0; i < arguments.size(); ++i) {
//                if (!fo.arguments.createCVar(i).equals(arguments.createCVar(i))) {
//                    return false;
//                }
//            }
//            return true;
//        }
//    }

    public void clear() {
        setValue(null);
    }

//    public List<TVariable> getTVariables() {
//        return Tools.getTVariables(arguments, true);
//    }

    //    public boolean isCalculated() {
//        FValue f = user.getMind().getFValues().get(this);
//        if (f != null) {
//            for (int i = 0; i < getRange(); ++i) {
//                if (getArguments().get(i).getValue() == null
//                        || getArguments().get(i).getValue().getId() != f.getCondition(i).getValue().getId()) {
//                    return false;
//                }
//            }
//        }
//        return f != null && f.isActual(this); // && getCalculatedResult() != null && f.getValue() == getCalculatedResult(); //!= null; //&& !isCalculable();//(getCalculatedResult() == null || f.getValue() == getCalculatedResult()); //mind.getFValues().createCVar(this) != null /*|| mind.getCalculated().contains(this)*/;
//    }
//
    public boolean isComplete() {
        for (Argument a : arguments) {
            if (a.getValue() == null) {
                return false;
            }
        }
        return true;
    }

//    public boolean isDirtyComplete() {
//        for (Argument a : arguments) {
//            if (a.getDirtyValue() == null) {
//                return false;
//            }
//        }
//        return true;
//    }

    public boolean isCalculable() {
        return arguments.getTVariables(true).size() > 0;
    }

//    public boolean isCalculated() {
//        return getCurrent() != null;
//    }

    @Override
    public boolean isEmpty() {
        return getValue() == null;
    }

    @Override
    public boolean isTVariable() {
        return false;
    }

    @Override
    public boolean isFunction() {
        return true;
    }

    @Override
    public boolean isTValue() {
        return false;
    }

    @Override
    public boolean isTerm() {
        return false;
    }

    @Override
    public boolean isFValue() {
        return false;
    }

    @Override
    public boolean isCVariable() {
        return !isEmpty() && getValue().isCVariable();
    }

    @Override
    public boolean isDefined() {
        return false;
    }

    @Override
    public TVariable getTVariable() {
        return null;
    }

    @Override
    public Function getFunction() {
        return this;
    }

    @Override
    public TValue getTValue() {
        return null;
    }

    @Override
    public FValue getFValue() {
        return null;
    }


    public FValue getCurrent() {
        return user.getMind().getFValues().find(this);
    }
}
