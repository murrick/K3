package kanger.units;

import kanger.User;
import kanger.compiler.Operation;
import kanger.compiler.Parser;
import kanger.enums.Enums;
import kanger.interfaces.Identifiable;
import kanger.primitives.ArgList;
import kanger.primitives.Argument;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Created by Dmitry G. Qusnetsov on 26.05.15.
 * <p>
 * Домен для функции. Может быть рекурсивным на уровне структуры TList.
 */
public class Function implements Externalizable, Identifiable<Function> {

    private static final long serialVersionUID = 196402070002L;

    private long id = -1;
    private Term name = null;
    private int range = 0;
    private ArgList arguments = new ArgList();     // Параметры

    private User user = null;

    private transient long nameId = -1;

    public Function() {

    }

    public Function(User user) {
        this.user = user;
    }

    @Override
    public void readExternal(ObjectInput dis) throws IOException, ClassNotFoundException {
        id = dis.readLong();
        nameId = dis.readLong();
        range = dis.readInt();
        arguments = (ArgList) dis.readObject();
        arguments.setUser(user);
    }

    @Override
    public void writeExternal(ObjectOutput dos) throws IOException {
        dos.writeLong(id);
        dos.writeLong(nameId);
        dos.writeInt(range);
        dos.writeObject(arguments);
    }

//    @Override
//    public void linkExternal(User user) throws IOException, ClassNotFoundException {
//        this.user = user;
//        name = user.getMind().getTerms().load(nameId);
////        arguments.linkExternal(user);
//    }

    @Override
    public void setId(long id) {
        this.id = id;
    }

    @Override
    public long getId() {
        return id;
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

    public Term getValue() throws IOException, ClassNotFoundException {
        FValue c = getCurrent();
        if (c != null) {
            return getCurrent().getValue();
        } else {
            return null;
        }
    }

    public Object setValue(Term r) throws Exception {
        while (range + 1 > arguments.size()) {
            arguments.add(new Argument());
        }
        arguments.get(range).setValue(r);
        return arguments.get(range);
    }

    public boolean isEmpty() {
        try {
            return getValue() == null;
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return true;
        }
    }

    public boolean setParameter(int i, Term r) throws Exception {
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


    public Term getName() throws IOException, ClassNotFoundException {
        if (name == null) {
            name = user.getMind().getTerms().load(nameId);
        }
        return name;
    }

    public void setName(Term name) {
        this.name = name;
        this.nameId = name.getId();
    }

    private String formatParam(Argument t) throws Exception {
        Operation op = Parser.getOp(getName().toString(), range);
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
        try {
            if (!isCalculable() && getValue() != null) {
                return getValue().toString();
            } else {
                Operation op = Parser.getOp(getName().toString(), range);
                String s = "";
                if (op == null || op.getRange() != range) {
                    s = String.format("%s(", getName().toString());
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
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return "";
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

    public void clear() throws Exception {
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
    public boolean isComplete() throws Exception {
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

    public boolean isCalculable() throws IOException, ClassNotFoundException {
        return arguments.getTVariables(true).size() > 0;
    }

//    public boolean isCalculated() {
//        return getCurrent() != null;
//    }


    public FValue getCurrent() throws IOException, ClassNotFoundException {
        return user.getMind().getFValues().find(this);
    }

    @Override
    public int getHash() {
        StringBuffer buffer = new StringBuffer();
        buffer.append(nameId);
        buffer.append(range);
        buffer.append(arguments.hashCode());
        return buffer.toString().hashCode();
    }

    @Override
    public boolean equalsTo(Function to) {
        return false;
    }

    @Override
    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
        arguments.setUser(user);
    }

    @Override
    public int hashCode() {
        return ("" + id).hashCode();
    }

    public int getHashStruct(Right r) throws IOException, ClassNotFoundException {
        StringBuffer buffer = new StringBuffer();
        buffer.append(nameId);
        buffer.append(range);
        for (int i = 0; i < range; ++i) {
            buffer.append(arguments.get(i).getType().ordinal());
            switch (arguments.get(i).getType()) {
                case CVARIABLE:
                    buffer.append(arguments.get(i).getValue().getIndex() - r.getVarIndex());
                    break;
                case TVARIABLE:
                    buffer.append(arguments.get(i).getT().getIndex() - r.getVarIndex());
                    break;
                case TERM:
                    buffer.append(arguments.get(i).getValue().getId());
                    break;
                case FUNCTION:
                    buffer.append(arguments.get(i).getF().getHashStruct(r));
                    break;
            }
        }
        return buffer.toString().hashCode();
    }

    public boolean equalsToStruct(Function f, Right left, Right right) throws IOException, ClassNotFoundException {
        if (nameId == f.nameId && range == f.getRange()) {
            for (int i = 0; i < range; ++i) {
                if (arguments.get(i).getType() == f.getArguments().get(i).getType()) {
                    switch (arguments.get(i).getType()) {
                        case CVARIABLE:
                            if ((arguments.get(i).getValue().getIndex() - left.getVarIndex())
                                    != (f.getArguments().get(i).getValue().getIndex() - right.getVarIndex())) {
                                return false;
                            }
                            break;
                        case TVARIABLE:
                            if ((arguments.get(i).getT().getIndex() - left.getVarIndex())
                                    != (f.getArguments().get(i).getT().getIndex() - right.getVarIndex())) {
                                return false;
                            }
                            break;
                        case TERM:
                            if (arguments.get(i).getValue().getId() != f.getArguments().get(i).getValue().getId()) {
                                return false;
                            }
                            break;
                        case FUNCTION:
                            if (!arguments.get(i).getF().equalsToStruct(f.getArguments().get(i).getF(), left, right)) {
                                return false;
                            }
                            break;
                    }
                } else {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

}
