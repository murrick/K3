/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to
 * deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package org.kanger.units;

import org.kanger.Mind;
import org.kanger.compiler.Parser;
import org.kanger.enums.ArgumentType;
import org.kanger.enums.Enums;
import org.kanger.enums.UnitType;
import org.kanger.exception.OutOfBufferException;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.ByteBuffer;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Материализованный результат одного {@link Function} при конкретном stamp
 * входных T-подстановок.
 *
 * <p><strong>Архитектурная роль.</strong> {@code FValue} отделяет устойчивое
 * определение вычислительного узла от результата его исполнения. Он хранит
 * ссылку на Function, результирующий Term и упорядоченный stamp значений всех
 * участвующих {@link TVariable}; сам Function при этом не превращается в
 * mutable result slot.</p>
 *
 * <p><strong>Inside.</strong> Собственное состояние включает operational ID,
 * owner Mind ID, Function ID, result Term ID и stamp входных Term ID. Именно
 * сочетание Function и stamp определяет применимость ранее вычисленного
 * результата к текущему состоянию аргументов.</p>
 *
 * <p><strong>Outside.</strong> Транзакционная видимость удаления, lazy hydration
 * Function/Term, диагностическое представление и включение в continuation
 * lifecycle принадлежат конкретному Mind и его {@code FValueFactory}.</p>
 *
 * <p><strong>Canonicalization и lifecycle.</strong> Factory должна повторно
 * использовать канонический FValue для совместимого Function/stamp, а rollback
 * обязан удалять continuation, созданные после checkpoint. Удалённый объект не
 * считается несуществующим и может участвовать в восстановлении канонической
 * identity согласно factory contract.</p>
 *
 * <p><strong>Инварианты.</strong> Function definition != FValue result;
 * result Term != input stamp; operational ID != semantic applicability;
 * deleted != nonexistent; diagnostic text != persistence representation.</p>
 */
public class FValue implements IUnit<FValue> {

    private static final long serialVersionUID = 196402070003L;

    private long id = -1;
    private long mindId = -1;
    private Function function = null;
    private Term value = null;
    private List<Long> stamp = new ArrayList<>();

    private transient long functionId = -1;
    private transient long valueId = -1;
    private transient Mind mind = null;

    public FValue() {
    }

    /**
     * Captures the current result and ordered T-variable substitution stamp.
     *
     * @param f function whose current execution state is materialized
     * @param mind owning execution context
     * @throws Exception if arguments or the result cannot be resolved
     */
    public FValue(Function f, Mind mind) throws Exception {
        function = f;
        value = (Term) f.getArguments().get(f.getRange()).getValue(mind);
        functionId = function.getId();
        if (value != null) {
            valueId = value.getId();
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

    /** @return persistent representation containing IDs and ordered stamp */
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
        return packet.createMarked();
    }

    /**
     * Applies serialized state without resolving Function or result Term.
     *
     * @param packet serialized FValue state
     * @return this hydrated shell
     * @throws OutOfBufferException if the packet is incomplete
     */
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

    /** Sets the materialized result reference; it does not alter the input stamp. */
    public void setValue(Term value) {
        this.value = value;
        valueId = value.getId();
    }

    /** Resolves the result Term in the supplied Mind. */
    public ITerm getValue(Mind mind) throws Exception {
        if (value == null && valueId != -1) {
            value = mind.getTerms().get(valueId);
        }
        return value;
    }

    /** Resolves the Function definition through the owning Mind. */
    public Function getFunction() throws Exception {
        if (function == null) {
            function = mind.getFunctions().get(functionId);
        }
        return function;
    }

    /** Sets the stable Function reference represented by this result. */
    public void setFunction(Function function) {
        this.function = function;
        this.functionId = function.getId();
    }

    private String formatParam(IArgument t, Mind mind) throws Exception {
        Parser.Op op = Parser.getOp(getFunction().getName(mind).toString(), getFunction().getRange());
        boolean isOp = op != null && op.getRange() == getFunction().getRange();
        String s = "";
        if (t.getType() == ArgumentType.FVALUE) {
            s += (isOp ? "(" : "") + t.getObject(mind).toString() + (isOp ? ")" : "");
        } else if (t.getType() == ArgumentType.TVALUE) {
            s += t.getObject(mind).toString();
        } else if (!t.isEmpty(mind)) {
            s += t.getValue(mind).toString();
        } else {
            s += "_";
        }
        return s;
    }

    /**
     * Hashes Function ID, result ID and ordered input stamp for candidate lookup.
     */
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

    /** Tests applicability against another materialized result's Function state. */
    @Override
    public boolean equalsTo(FValue f) throws Exception {
        return equalsTo(f.getFunction());
    }

    /**
     * Tests whether the current substitutions of a Function match this stamp.
     * The result Term is deliberately not part of this applicability check.
     */
    public boolean equalsTo(Function f) {
        try {
            if (f.getId() == getFunction().getId()) {
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

    /** Returns a context-sensitive diagnostic rendering, not a stable protocol. */
    public String toString(IMind mind) {
        try {
            if (!getFunction().isCalculable() && getValue((Mind) mind) != null) {
                return getValue((Mind) mind).toString();
            } else {
                try {
                    Function f = getFunction();
                    Parser.Op op = Parser.getOp(f.getName((Mind) mind).toString(), f.getRange());
                    String s = "";
                    if (op == null || op.getRange() != f.getRange()) {
                        s = String.format("%s(", f.getName((Mind) mind).toString());
                        for (int i = 0; i < f.getRange(); ++i) {
                            s += formatParam(f.getArguments().get(i), (Mind) mind);
                            if (i + 1 < f.getRange()) {
                                s += (char) Enums.COMMA;
                            }
                        }
                        s += ")";
                    } else if (op.getRange() == 1) {
                        if (op.isPost()) {
                            s = formatParam(f.getArguments().get(0), (Mind) mind) + op.getName();
                        } else {
                            s = op.getName() + formatParam(f.getArguments().get(0), (Mind) mind);
                        }
                    } else {
                        for (int i = 0; i < op.getRange(); ++i) {
                            s += formatParam(f.getArguments().get(i), (Mind) mind);
                            if (i + 1 < op.getRange()) {
                                s += " " + op.getName() + " ";
                            }
                        }
                    }

                    String res = "";
                    if ((mind.getDebugLevel() & Enums.DEBUG_OPTION_VALUES) != 0) {
                        if (getValue((Mind) mind) != null) {
                            res = " {= " + getValue((Mind) mind) + "}";
                        } else if (f.getArguments().size() > function.getRange()
                                && !f.getArguments().get(function.getRange()).isEmpty((Mind) mind)) {
                            res = " [= " + f.getArguments().get(function.getRange()).getValue(mind) + "]";
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

    /** @return operational ID of the represented Function */
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
        function = null;
        value = null;
        return this;
    }
}
