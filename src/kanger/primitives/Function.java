package kanger.primitives;

import kanger.Mind;
import kanger.compiler.Operation;
import kanger.compiler.Parser;
import kanger.enums.Enums;
import kanger.enums.Tools;

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
public class Function {

    private Term name = null;
    private int range = 0;
    private final List<Argument> arguments = new ArrayList<>();     // Параметры
    private boolean busy = false;                       // Предотвращение бесконечной рекурсии

    private long id = -1;
    private Function next = null;
    private Domain owner = null;
    private int index = -1;
    private Mind mind = null;

    public Function(Domain owner, Mind mind) {
        this.owner = owner;
        this.mind = mind;
    }

    public Function(DataInputStream dis, Mind mind) throws IOException {
        long id = dis.readLong();
        name = mind.getTerms().get(id);
        range = dis.readInt();
        this.mind = mind;

//        f = (FunctionDescriptor) mind.getFunctions().createCVar(id);
//        line.clear();
//        int count = dis.readInt();
//        while (count-- > 0) {
//            Argument a = new Argument(dis, mind);
//            line.createTVar(a);
//        }
        arguments.clear();
        int count = dis.readInt();
        while (count-- > 0) {
            Argument a = new Argument(dis, mind);
            arguments.add(a);
        }
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

    public void setOwner(Domain owner) {
        this.owner = owner;
    }

    public Domain getOwner() {
        return owner;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }


//    public FunctionDescriptor getF() {
//        return f;
//    }
//
//    public void setF(FunctionDescriptor f) {
//        this.f = f;
//    }
//
//    public void createTVar(Argument t) {
//        line.createTVar(t);
//    }

    //    public Argument createCVar(int i) {
//        if (i == range && range == arguments.size()) {
//            arguments.createTVar(new Argument());
//        }
//        return arguments.createCVar(i);
//    }
//
    public int getRange() {
        return range;
    }

    public void setRange(int range) {
        this.range = range;
    }

//    public void setL(List<Argument> list) {
//        line = list;
//    }

//    public void setA(List<Argument> list) {
//        arguments = list;
//    }

    //    public List<Argument> getArguments() {
//        return arguments;
//    }
//
    public List<Argument> getArguments() {
        return arguments;
    }

    public Term getResult() {
        while (range + 1 > arguments.size()) {
            arguments.add(new Argument());
        }
        return arguments.get(range).getValue();
    }

//    public Term getResult() {
//        FValue f = mind.getFValues().createCVar(this);
//        if (f != null) {
//            return f.getValue();
//        } else {
//            return null;
//        }
//    }

//    public void setResult(Domain d, Argument r) {
//        if (range + 1 > arguments.size()) {
//            arguments.createTVar(new Argument());
//        }
//        arguments.createCVar(range).setValue(d, r.getValue());
////        arguments.createCVar(range).setC(r.getC());
////        arguments.createCVar(range).setT(r.getT());
////        arguments.createCVar(range).setF(r.getF());
//    }

    public void setResult(Term r) {
        while (range + 1 > arguments.size()) {
            arguments.add(new Argument());
        }
        if ((r == null && arguments.get(range).getValue() != null)
                || (r != null && arguments.get(range).getValue() == null)
                || (r != null && arguments.get(range).getValue() != null
                && r.getId() != arguments.get(range).getValue().getId())) {
            mind.getCalculated().add(this);
        }
        arguments.get(range).setValue(r);
    }

    public boolean setParameter(int i, Term r) {
//        if(i == range) {
//            TSubst s = setResult(r);
//            s.setSolves(owner, owner);
//            return true;
//        } else {

        if ((arguments.get(i).isEmpty() && r != null)
                || !arguments.get(i).isEmpty() && r == null
                || (!arguments.get(i).isEmpty() && r != null && arguments.get(i).getValue().getId() != r.getId())) {
            mind.getCalculated().add(this);
        }

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

    public boolean isBusy() {
        return busy;
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
    }

    private String formatParam(Argument t, FValue v) {
        Operation op = Parser.getOp(name.toString());
        boolean isOp = op != null && op.getRange() == range;
        String s = "";
        if (t.isFSet()) {
            s += (isOp ? "(" : "") + t.getF().toString() + (isOp ? ")" : "");
        } else if (t.isTSet()) {
            if (v == null) {
                s += t.getT().toString();
            } else {
                TValue tv = v.getValue(t.getT());
                s += t.getT().getVarName()
                        + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_VALUES) != 0 ? (tv == null ? "" : (":" + tv.getValue().toString())) : "")
                        + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_STATUS) != 0 && tv != null && tv.isBlocked() ? " (B)" : "");

            }
        } else if (!t.isEmpty()) {
            s += t.getValue().toString();
        } else {
            s += "_";
        }
        return s;
    }

    public String toString(FValue v) {
        Operation op = Parser.getOp(name.toString());
        String s = "";
        if (op == null || op.getRange() != range) {
            s = String.format("%s(", name.toString());
            for (int i = 0; i < range; ++i) {
                s += formatParam(arguments.get(i), v);
                if (i + 1 < range) {
                    s += (char) Enums.COMMA;
                }
            }
            s += ")";
        } else if (op.getRange() == 1) {
            if (op.isPost()) {
                s = formatParam(arguments.get(0), v) + op.getName();
            } else {
                s = op.getName() + formatParam(arguments.get(0), v);
            }
        } else {
            for (int i = 0; i < op.getRange(); ++i) {
                s += formatParam(arguments.get(i), v);
                if (i + 1 < op.getRange()) {
                    s += " " + op.getName() + " ";
                }
            }
        }

        String res = "";
        if ((mind.getDebugLevel() & Enums.DEBUG_OPTION_VALUES) != 0) {
            if (v == null) {
                if (((isCalculable() && isCalculated()) || !isCalculable()) && getResult() != null) {
                    res = " = " + getResult();
                }
            } else {
                res = " = " + v.getValue();
            }
        }
        //Argument r = range < arguments.size() ? arguments.createCVar(range) : null;
        return s + res;
    }

    public String toString() {
        if (!isCalculable() && getResult() != null) {
            return getResult().toString();
        } else {
            return toString(null);
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
    public void writeCompiledData(DataOutputStream dos) throws IOException {
        dos.writeLong(name.getId());
//        dos.writeInt(line.size());
//        for (Argument a : line) {
//            a.writeCompiledData(dos);
//        }
        dos.writeInt(range);
        dos.writeInt(arguments.size());
        for (Argument a : arguments) {
            a.writeCompiledData(dos);
        }
    }

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

    public void clearResult() {
        setResult(null);
    }

    public List<TVariable> getTVariables() {
        return Tools.getTVariables(arguments, true);
    }

    public boolean isCalculated() {
        FValue f = mind.getFValues().get(this);
        return f != null; // && getCalculatedResult() != null && f.getValue() == getCalculatedResult(); //!= null; //&& !isCalculable();//(getCalculatedResult() == null || f.getValue() == getCalculatedResult()); //mind.getFValues().createCVar(this) != null /*|| mind.getCalculated().contains(this)*/;
    }

    public boolean isSubstituted() {
        for (TVariable t : getTVariables()) {
            if (!t.isSubstituted() || /*!mind.getUsed().contains(t) ||*/ t.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public boolean isCalculable() {
        return Tools.getTVariables(arguments, true).size() > 0;
    }

}
