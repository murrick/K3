package org.kanger.units;

import org.kanger.Mind;
import org.kanger.compiler.Operation;
import org.kanger.compiler.Parser;
import org.kanger.enums.Enums;
import org.kanger.enums.UnitType;
import org.kanger.exception.OutOfBufferException;
import org.kanger.interfaces.IUnit;
import org.kanger.primitives.ArgList;
import org.kanger.primitives.Argument;
import org.kanger.storage.ByteBuffer;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Dmitry G. Qusnetsov on 26.05.15.
 * <p>
 * Домен для функции. Может быть рекурсивным на уровне структуры TList.
 */
public class Function implements IUnit<Function> {

    private static final long serialVersionUID = 196402070002L;

    private long id = -1;
    private long mindId = -1;                                   // id транзакции
    private Term name = null;
    private int range = 0;
    private ArgList arguments = new ArgList();     // Параметры

    private Mind mind = null;

    private transient long nameId = -1;

    private transient boolean deleted = false;

    public Function() {

    }

    public Function(Mind mind) {
        this.mind = mind;
    }

    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putLong(id)
                .putLong(mindId)
                .putByte(deleted ? 1 : 0)
                .putLong(nameId)
                .putInt(range)
                .append(arguments.pack());
        return packet.createMarked();
    }

    public Function apply(ByteBuffer packet) throws OutOfBufferException {
        id = packet.getLong();
        mindId = packet.getLong();
        deleted = packet.getByte() != 0;
        nameId = packet.getLong();
        range = packet.getInt();
        try {
            packet.mark();
            arguments = new ArgList().apply(packet);
//            arguments.setUser(user);
        } finally {
            packet.release();
        }
        return this;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
        this.id = id;
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

    public Term getValue() throws Exception {
        FValue c = getCurrent();
        if (c != null) {
            return getCurrent().getValue();
        } else {
            return null;
        }
    }

    public void clear() {
        while (range + 1 > arguments.size()) {
            arguments.add(new Argument());
        }
        arguments.get(range).clear();
    }

    public Argument setResult(Term r) throws Exception {
        while (range + 1 > arguments.size()) {
            arguments.add(new Argument());
        }
        arguments.get(range).setValue(mind, r);
        return arguments.get(range);
    }

    public Argument getResult() {
        while (range + 1 > arguments.size()) {
            arguments.add(new Argument());
        }
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

        if (arguments.get(i).setValue(mind, r)) {

            if (arguments.get(i).isTSet()) {
                List<TValue> list = new ArrayList<>();
                list.add(arguments.get(i).getT(mind).getCurrent());
                mind.addTSolve(list);
            }
            return true;

        } else {
            return false;
        }
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


    public Term getName() throws Exception {
        if (name == null) {
            name = mind.getTerms().load(nameId);
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
            s += (isOp ? "(" : "") + t.getF(mind).toString() + (isOp ? ")" : "");
        } else if (t.isTSet()) {
//            if (v == null) {
            s += t.getT(mind).toString();
//            } else {
//                TValue tv = v.getValue(t.getTVariable());
//                s += t.getTVariable().getVarName()
//                        + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_VALUES) != 0 ? (tv == null ? "" : (":" + tv.getValue().toString())) : "")
//                        + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_STATUS) != 0 && tv != null && tv.isBlocked() ? " (B)" : "");
//
//            }
        } else if (!t.isEmpty(mind)) {
            s += t.getValue(mind).toString();
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
                if ((mind.getDebugLevel() & Enums.DEBUG_OPTION_VALUES) != 0) {
                    //                if (getResult() != null) {
                    if (getCurrent() != null) {
                        res = " {= " + getValue() + "}";
                    } else if (arguments.size() > range && !arguments.get(range).isEmpty(mind)) {
                        res = " [= " + arguments.get(range).getValue(mind) + "]";
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

//    public void clear() throws Exception {
//        setResult(null);
//    }

//    public List<TVariable> getTVariables() {
//        return Tools.getTVariables(arguments, true);
//    }

    //    public boolean isCalculated() {
//        FValue f = mind.getFValues().get(this);
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
            if (a.getValue(mind) == null) {
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

    public boolean isCalculable() throws Exception {
        return arguments.getTVariables(mind).size() > 0;
    }

//    public boolean isCalculated() {
//        return getCurrent() != null;
//    }


    public FValue getCurrent() throws Exception {
        return mind.getFValues().find(this);
    }

    public int getHashBase() throws Exception {
        long valueId = getResult().isEmpty(mind) ? 0 : getResult().getValue(mind).getId();
        int hash = 3;
        hash = 47 * hash + (int) (id ^ (id >>> 32));
        hash = 47 * hash + (int) (valueId ^ (valueId >>> 32));
        for (TVariable t : arguments.getTVariables(mind)) {
            if (t.isEmpty()) {
                hash = 47 * hash + 0;
            } else {
                long id = t.getValue().getId();
                hash = 47 * hash + (int) (id ^ (id >>> 32));
            }
        }
        return hash;
    }

    @Override
    public int getHash() {
        int hash = 3;
        hash = 47 * hash + (int) (nameId ^ (nameId >>> 32));
        hash = 47 * hash + range;
//        arguments.setMind(mind);
        hash = 47 * hash + arguments.hashCode(); //.getHash(mind);
        return hash;
    }

    @Override
    public boolean equalsTo(Function to) {
        return false;
    }

    @Override
    public Mind getMind() {
        return mind;
    }

    @Override
    public Function setMind(Mind mind) {
        this.mind = mind;
//        arguments.setUser(user);
        return this;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 47 * hash + (int) (id ^ (id >>> 32));
        return hash;
//        return ("" + id).hashCode();
    }

    public int getHashStruct(Right r) throws Exception {
        int hash = 3;
        hash = 47 * hash + (int) (nameId ^ (nameId >>> 32));
        hash = 47 * hash + range;
        for (int i = 0; i < range; ++i) {
            hash = 47 * hash + (i + 1) * arguments.get(i).getType().ordinal();
            switch (arguments.get(i).getType()) {
                case TVARIABLE:
                    hash = 47 * hash + (i + 1) * (arguments.get(i).getT(mind).getIndex() - r.getVarIndex());
                    break;
                case TERM:
                    long id = arguments.get(i).getValue(mind).getId();
                    hash = 47 * hash + (i + 1) * (int) (id ^ (id >>> 32));
                    break;
                case FUNCTION:
                    hash = 47 * hash + (i + 1) * arguments.get(i).getF(mind).getHashStruct(r);
                    break;
            }
        }
        return hash;
    }

    public boolean equalsToStruct(Function f, Right left, Right right) throws Exception {
        if (nameId == f.nameId && range == f.getRange()) {
            for (int i = 0; i < range; ++i) {
                if (arguments.get(i).getType() == f.getArguments().get(i).getType()) {
                    switch (arguments.get(i).getType()) {
                        case TVARIABLE:
                            if ((arguments.get(i).getT(mind).getIndex() - left.getVarIndex())
                                    != (f.getArguments().get(i).getT(mind).getIndex() - right.getVarIndex())) {
                                return false;
                            }
                            break;
                        case TERM:
                            if (arguments.get(i).getValue(mind).getId() != f.getArguments().get(i).getValue(mind).getId()) {
                                return false;
                            }
                            break;
                        case FUNCTION:
                            if (!arguments.get(i).getF(mind).equalsToStruct(f.getArguments().get(i).getF(mind), left, right)) {
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

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    @Override
    public void setDeleted() {
        deleted = true;
    }

    @Override
    public UnitType getUnitType() {
        return UnitType.FUNCTION;
    }

//    @Override
//    public Function commit(Mind m) throws Exception {
//        setName(name.commit(m));
//        for (int i = 0; i < range; ++i) {
//            arguments.get(i).setO((IUnit) arguments.get(i).getO(mind).commit(m));
//        }
//        setMind(m);
//        for (FValue v : mind.getFValues()) {
//            if (v.getFunctionId() == id) {
//                v.commit(m);
//            }
//        }
//        return this;
//    }

    @Override
    public long getMindId() {
        return mindId;
    }

    @Override
    public void setMindId(long mindId) {
        this.mindId = mindId;
    }

    @Override
    public boolean isLoaded() {
        return name != null && nameId == name.getId();
    }

}
