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

package org.kanger.units;

import org.kanger.Mind;
import org.kanger.compiler.Operation;
import org.kanger.compiler.Parser;
import org.kanger.enums.ArgumentType;
import org.kanger.enums.Enums;
import org.kanger.enums.UnitType;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.primitives.Argument;
import org.kanger.primitives.ArgumentsList;
import org.kanger.storage.ByteBuffer;

import java.util.*;

/**
 * Created by Dmitry G. Quznetsov on 26.05.15.
 * <p>
 * Домен для функции. Может быть рекурсивным на уровне структуры TList.
 */
public class Function implements IUnit<Function> {

    private static final long serialVersionUID = 196402070002L;

    private long id = -1;
    private long mindId = -1;                                   // id транзакции
    private ITerm name = null;
    private int range = 0;
    private ArgumentsList arguments = new ArgumentsList();     // Параметры

    private transient Mind mind = null;

    private transient long nameId = -1;

//    private transient boolean deleted = false;

    public Function() {

    }

    public Function(Mind mind) {
        this.mind = mind;
    }

    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putLong(id)
                .putLong(mindId)
                .putByte(isDeleted(mind) ? 1 : 0)
                .putLong(nameId)
                .putInt(range)
                .append(arguments.pack());
        return packet.createMarked();
    }

    public Function apply(ByteBuffer packet) throws Exception {
        id = packet.getLong();
        mindId = packet.getLong();
        if (packet.getByte() != 0) {
            setDeleted(true, mind);
        }
        nameId = packet.getLong();
        range = packet.getInt();
        try {
            packet.mark();
            arguments = new ArgumentsList().apply(packet);
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

    public long getNameId() {
        return nameId;
    }

    public int getRange() {
        return range;
    }

    public void setRange(int range) {
        this.range = range;
    }

    public ArgumentsList getArguments() {
        return arguments;
    }

    public ITerm getValue(Mind mind) throws Exception {
        FValue c = getCurrent();
        if (c != null) {
            return getCurrent().getValue(mind);
        } else {
            return null;
        }
    }

    public void clear() {
        while (range + 1 > arguments.size()) {
            arguments.add(new Argument());
        }
        ((Argument) arguments.get(range)).clear();
    }

    public IArgument setResult(ITerm r) throws Exception {
        while (range + 1 > arguments.size()) {
            arguments.add(new Argument());
        }
        ((Argument) arguments.get(range)).setValue(mind, r);
        return arguments.get(range);
    }

    public IArgument getResult() {
        while (range + 1 > arguments.size()) {
            arguments.add(new Argument());
        }
        return arguments.get(range);
    }

    public boolean isEmpty(Mind mind) {
        try {
            return getValue(mind) == null;
        } catch (Exception e) {
            System.err.println(new Date());
            e.printStackTrace(System.err);
            return true;
        }
    }

    public boolean setParameter(int i, ITerm r) throws Exception {
//        if(i == range) {
//            TSubst s = setResult(r);
//            s.setSolves(owner, owner);
//            return true;
//        } else {

        if (((Argument) arguments.get(i)).setValue(mind, r)) {

            if (arguments.get(i).getType() == ArgumentType.TVARIABLE) {
                List<TValue> list = new ArrayList<>();
                list.add(((TVariable) arguments.get(i).getObject(mind)).getCurrent());
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


    public ITerm getName(Mind mind) throws Exception {
        if (name == null) {
            name = mind.getTerms().get(nameId);
        }
        return name;
    }

    public void setName(ITerm name) {
        this.name = name;
        this.nameId = name.getId();
    }

    private String formatParam(IArgument t, Mind mind, boolean asRight) throws Exception {
        Operation op = Parser.getOp(getName(mind).toString(), range);
        boolean isOp = op != null && op.getRange() == range;
        String s = "";
        if (t.getType() == ArgumentType.FUNCTION) {
            s += (isOp ? "(" : "") + t.getObject(mind).toString() + (isOp ? ")" : "");
        } else if (t.getType() == ArgumentType.TVARIABLE) {
//            if (v == null) {
            s += t.getObject(mind).toString();
//            } else {
//                TValue tv = v.getValue(t.getTVariable());
//                s += t.getTVariable().getVarName()
//                        + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_VALUES) != 0 ? (tv == null ? "" : (":" + tv.getValue().toString())) : "")
//                        + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_STATUS) != 0 && tv != null && tv.isBlocked() ? " (B)" : "");
//
//            }
        } else if (!t.isEmpty(mind)) {
            if (asRight && t.getValue(mind).isCVariable()) {
                s += ((Term) t.getValue(mind)).getName(mind).getValue();
            } else {
                s += t.getValue(mind).toString();
            }
        } else {
            s += "_";
        }
        return s;
    }

    public String toString(IMind mind, boolean asRight) {
        try {
            if (!isCalculable() && getValue((Mind) mind) != null) {
                return getValue((Mind) mind).toString();
            } else {
                Operation op = Parser.getOp(getName((Mind) mind).toString(), range);
                String s = "";
                if (op == null || op.getRange() != range) {
                    s = String.format("%s(", getName((Mind) mind).toString());
                    for (int i = 0; i < range; ++i) {
                        s += formatParam(arguments.get(i), (Mind) mind, asRight);
                        if (i + 1 < range) {
                            s += (char) Enums.COMMA;
                        }
                    }
                    s += ")";
                } else if (op.getRange() == 1) {
                    if (op.isPost()) {
                        s = formatParam(arguments.get(0), (Mind) mind, asRight) + op.getName();
                    } else {
                        s = op.getName() + formatParam(arguments.get(0), (Mind) mind, asRight);
                    }
                } else {
                    for (int i = 0; i < op.getRange(); ++i) {
                        s += formatParam(arguments.get(i), (Mind) mind, asRight);
                        if (i + 1 < op.getRange()) {
                            s += " " + op.getName() + " ";
                        }
                    }
                }

                String res = "";
                if ((mind.getDebugLevel() & Enums.DEBUG_OPTION_VALUES) != 0) {
                    //                if (getResult() != null) {
                    if (getCurrent() != null) {
                        res = " {= " + getValue((Mind) mind) + "}";
                    } else if (arguments.size() > range && !arguments.get(range).isEmpty((Mind) mind)) {
                        res = " [= " + arguments.get(range).getValue(mind) + "]";
                    }
                }
                //Argument r = range < arguments.size() ? arguments.createCVar(range) : null;
                return s + res;
            }
        } catch (Exception e) {
            System.err.println(new Date());
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
        for (IArgument a : arguments) {
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

    public int getHashBase(Mind mind) throws Exception {
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

    public int getHashStruct(IRule r) throws Exception {
        int hash = 3;
        hash = 47 * hash + (int) (nameId ^ (nameId >>> 32));
        hash = 47 * hash + range;
        for (int i = 0; i < range; ++i) {
            hash = 47 * hash + (i + 1) * arguments.get(i).getType().ordinal();
            switch (arguments.get(i).getType()) {
                case TVARIABLE:
                    hash = 47 * hash + (i + 1) * (((TVariable) arguments.get(i).getObject(mind)).getIndex() - ((Rule) r).getVarIndex());
                    break;
                case TERM:
                    long id = arguments.get(i).getValue(mind).getId();
                    hash = 47 * hash + (i + 1) * (int) (id ^ (id >>> 32));
                    break;
                case FUNCTION:
                    hash = 47 * hash + (i + 1) * ((Function) arguments.get(i).getObject(mind)).getHashStruct(r);
                    break;
            }
        }
        return hash;
    }

    public boolean equalsToStruct(Function f, IRule left, IRule rule) throws Exception {
        if (nameId == f.nameId && range == f.getRange()) {
            for (int i = 0; i < range; ++i) {
                if (arguments.get(i).getType() == f.getArguments().get(i).getType()) {
                    switch (arguments.get(i).getType()) {
                        case TVARIABLE:
                            if ((((TVariable) arguments.get(i).getObject(mind)).getIndex() - ((Rule) left).getVarIndex())
                                    != (((TVariable) f.getArguments().get(i).getObject(mind)).getIndex() - ((Rule) rule).getVarIndex())) {
                                return false;
                            }
                            break;
                        case TERM:
                            if (arguments.get(i).getValue(mind).getId() != f.getArguments().get(i).getValue(mind).getId()) {
                                return false;
                            }
                            break;
                        case FUNCTION:
                            if (!((Function) arguments.get(i).getObject(mind))
                                    .equalsToStruct(((Function) f.getArguments().get(i).getObject(mind)), left, rule)) {
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

    @Override
    public boolean isDeleted(IMind mind) {
        return ((Mind) mind).isUnitDeleted(this);
    }

    @Override
    public void setDeleted(boolean on, Mind mind) throws Exception {
        mind.setUnitDeleted(this, on);
        if (on) {
            FValue v = mind.getFValues().find(this);
            if (v != null) {
                v.setDeleted(on, mind);
            }
        }
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

    @Override
    public Map<String, Object> createMap(IMind mind) throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("mind_id", mindId);
        map.put("deleted", isDeleted(mind));
        map.put("name_id", nameId);
        map.put("range", range);
        map.put("name", getName((Mind) mind).getValue());
        map.put("arguments", arguments.createMap(mind));
        return map;
    }

    @Override
    public Function applyMap(Map<String, Object> map) throws Exception {
        id = Long.parseLong(map.get("id") + "");
        mindId = Long.parseLong(map.get("mind_id") + "");
        boolean deleted = Boolean.parseBoolean(map.get("deleted") + "");
        if (deleted) {
            setDeleted(true, mind);
        }
        nameId = Long.parseLong(map.get("name_id") + "");
        range = Integer.parseInt(map.get("tange") + "");
        arguments.applyMap((List<Map<String, Object>>) map.get("arguments"));
        name = null;
        return this;
    }

}
