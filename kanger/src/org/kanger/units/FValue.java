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
import org.kanger.exception.OutOfBufferException;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.primitives.Argument;
import org.kanger.primitives.ArgumentsList;
import org.kanger.storage.ByteBuffer;

import java.util.*;

public class FValue implements IUnit<FValue> {

    private static final long serialVersionUID = 196402070003L;

    private long id = -1;                                   // id решения
    private long mindId = -1;                               // id транзакции
    private Function function = null;                       // ссылка на функцию
    private Term value = null;                              // результат решения
    private ArgumentsList condition = new ArgumentsList();  // список значений параметров
    private List<Long> stamp = new ArrayList<>();           // список t-подстановок в порядке слдедования t-перем.

    private transient long functionId = -1;
    private transient long valueId = -1;
    private transient Mind mind = null;

    public FValue() {
    }

    public FValue(Function f, Mind mind) throws Exception {
        function = f;
        value = (Term) f.getArguments().get(f.getRange()).getValue(mind);
        functionId = function.getId();
        if (value != null) {
            valueId = value.getId();
        }
        for (IArgument a : f.getArguments()) {
            if (a.getType() == ArgumentType.TVARIABLE) {
                condition.add(new Argument(((TVariable) a.getObject(mind)).getCurrent()));
            } else if (a.getType() == ArgumentType.FUNCTION) {
                condition.add(new Argument(((Function) a.getObject(mind)).getCurrent()));
            } else {
                condition.add(new Argument(a.getValue(mind)));
            }
        }
        for (TVariable t : f.getArguments().getTVariables(mind)) {
            if (t.isEmpty()) {
                stamp.add(0L);
            } else {
                stamp.add(t.getCurrent().getValue(mind).getId());
            }
        }
        this.mind = mind;
    }

    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putLong(id)
                .putLong(mindId)
                .putByte(isDeleted(mind) ? 1 : 0)
                .putLong(functionId)
                .putLong(valueId);

        packet.putInt(stamp.size());
        for (long id : stamp) {
            packet.putLong(id);
        }
        packet.append(condition.pack());
        return packet.createMarked();
    }

    public FValue apply(ByteBuffer packet) throws OutOfBufferException {
        id = packet.getLong();
        mindId = packet.getLong();
        if (packet.getByte() != 0) {
            setDeleted(true, mind);
        }
        functionId = packet.getLong();
        valueId = packet.getLong();
        int cnt = packet.getInt();
        while (cnt-- > 0) {
            stamp.add(packet.getLong());
        }
        try {
            packet.mark();
            condition = new ArgumentsList().apply(packet);
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

    public void setValue(Term value) {
        this.value = value;
        valueId = value.getId();
    }

    public ITerm getValue(Mind mind) throws Exception {
        if (value == null && valueId != -1) {
            value = mind.getTerms().get(valueId);
        }
        return value;
    }

    public Function getFunction() throws Exception {
        if (function == null) {
            function = mind.getFunctions().get(functionId);
        }
        return function;
    }

    public void setFunction(Function function) {
        this.function = function;
        this.functionId = function.getId();
    }

    public ArgumentsList getCondition() {
        return condition;
    }

    private String formatParam(IArgument t, Mind mind) throws Exception {
        Operation op = Parser.getOp(getFunction().getName(mind).toString(), getFunction().getRange());
        boolean isOp = op != null && op.getRange() == getFunction().getRange();
        String s = "";
        if (t.getType() == ArgumentType.FUNCTION) {
            s += (isOp ? "(" : "") + t.getObject(mind).toString() + (isOp ? ")" : "");
        } else if (t.getType() == ArgumentType.FVALUE) {
            s += (isOp ? "(" : "") + t.getObject(mind).toString() + (isOp ? ")" : "");
        } else if (t.getType() == ArgumentType.TVARIABLE) {
            s += t.getObject(mind).toString();
        } else if (t.getType() == ArgumentType.TVALUE) {
            s += t.getObject(mind).toString();
        } else if (!t.isEmpty(mind)) {
            s += t.getValue(mind).toString();
        } else {
            s += "_";
        }
        return s;
    }

    @Override
    public int getHash() {
        int hash = 3;
        hash = 47 * hash + (int) (functionId ^ (functionId >>> 32));
        hash = 47 * hash + (int) (valueId ^ (valueId >>> 32));
        for (long id : stamp) {
            hash = 47 * hash + (int) (id ^ (id >>> 32));
        }
        return hash;
    }

    @Override
    public boolean equalsTo(FValue f) throws Exception {
        return equalsTo(f.getFunction());
    }

    public boolean equalsTo(Function f) {
        try {
            if (f.getId() == getFunction().getId()
                    && !f.getResult().isEmpty(mind)
                    && valueId == f.getResult().getValue(mind).getId()) {
                boolean complete = true;
                List<TVariable> list = f.getArguments().getTVariables(mind);
                for (int i = 0; i < list.size(); ++i) {
                    if (list.get(i).isEmpty() || list.get(i).getValue().getId() != stamp.get(i)) {
                        complete = false;
                        break;
                    }
                }
                return complete;
            } else {
                return false;
            }
        } catch (Exception e) {
            System.err.println(new Date());
            e.printStackTrace(System.err);
            return false;
        }
    }

    @Override
    public Mind getMind() {
        return mind;
    }

    @Override
    public FValue setMind(Mind mind) {
        this.mind = mind;
        return this;
    }

    @Override
    public boolean isDeleted(IMind mind) {
        return ((Mind) mind).isUnitDeleted(this);
    }

    @Override
    public void setDeleted(boolean on, Mind mind) {
        mind.setUnitDeleted(this, on);
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 47 * hash + (int) (id ^ (id >>> 32));
        return hash;
    }

    public String toString(IMind mind) {
        try {
            if (!getFunction().isCalculable() && getValue((Mind) mind) != null) {
                return getValue((Mind) mind).toString();
            } else {
                try {
                    Operation op = Parser.getOp(getFunction().getName((Mind) mind).toString(), getFunction().getRange());
                    String s = "";
                    if (op == null || op.getRange() != getFunction().getRange()) {
                        s = String.format("%s(", getFunction().getName((Mind) mind).toString());
                        for (int i = 0; i < getFunction().getRange(); ++i) {
                            s += formatParam(condition.get(i), (Mind) mind);
                            if (i + 1 < getFunction().getRange()) {
                                s += (char) Enums.COMMA;
                            }
                        }
                        s += ")";
                    } else if (op.getRange() == 1) {
                        if (op.isPost()) {
                            s = formatParam(condition.get(0), (Mind) mind) + op.getName();
                        } else {
                            s = op.getName() + formatParam(condition.get(0), (Mind) mind);
                        }
                    } else {
                        for (int i = 0; i < op.getRange(); ++i) {
                            s += formatParam(condition.get(i), (Mind) mind);
                            if (i + 1 < op.getRange()) {
                                s += " " + op.getName() + " ";
                            }
                        }
                    }

                    String res = "";
                    if ((mind.getDebugLevel() & Enums.DEBUG_OPTION_VALUES) != 0) {
                        //                if (getResult() != null) {
                        if (getValue((Mind) mind) != null) {
                            res = " {= " + getValue((Mind) mind) + "}";
                        } else if (condition.size() > function.getRange() && !condition.get(function.getRange()).isEmpty((Mind) mind)) {
                            res = " [= " + condition.get(function.getRange()).getValue(mind) + "]";
                        }
                    }
                    return s + res;
                } catch (Exception e) {
                    System.err.println(new Date());
                    e.printStackTrace(System.err);
                    return "";
                }
            }
        } catch (Exception e) {
            System.err.println(new Date());
            e.printStackTrace(System.err);
            return "";
        }
    }

    @Override
    public UnitType getUnitType() {
        return UnitType.FVALUE;
    }

    @Override
    public long getMindId() {
        return mindId;
    }

    @Override
    public void setMindId(long mindId) {
        this.mindId = mindId;
    }

    public long getFunctionId() {
        return functionId;
    }

    @Override
    public boolean isLoaded() {
        return function != null && functionId == function.getId();
    }

    @Override
    public Map<String, Object> createMap(IMind mind) throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("mind_id", mindId);
        map.put("deleted", isDeleted(mind));
        map.put("function_id", functionId);
        map.put("value_id", valueId);
        map.put("condition", condition.createMap(mind));
        map.put("function", function.createMap(mind));
        map.put("value", value.createMap(mind));
        return map;
    }

    @Override
    public FValue applyMap(Map<String, Object> map) throws Exception {
        id = Long.parseLong(map.get("id") + "");
        mindId = Long.parseLong(map.get("mind_id") + "");
        boolean deleted = Boolean.parseBoolean(map.get("deleted") + "");
        if (deleted) {
            setDeleted(true, mind);
        }
        functionId = Long.parseLong(map.get("function_id") + "");
        valueId = Long.parseLong(map.get("value_id") + "");
        condition.applyMap((List<Map<String, Object>>) map.get("condition"));
        function = null;
        value = null;
        return this;
    }

}
