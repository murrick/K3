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
import org.kanger.enums.Enums;
import org.kanger.enums.UnitType;
import org.kanger.exception.OutOfBufferException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.ByteBuffer;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Created by Dmitry G. Quznetsov on 13.12.16.
 */
public class TValue implements Comparable<TValue>, IUnit<TValue> {

    private static final long serialVersionUID = 196402070009L;

    private long id = -1;               // Идентификатор значения переменной
    private long mindId = -1;           // id транзакции
    private ITerm value = null;         // Подставленное значение донор
    private TVariable tVar = null;      // t-переменная аксептор

    private long valueId = -1;
    private long tVarId = -1;
    private Mind mind = null;

    public TValue() {
    }

    public TValue(TVariable var, ITerm val) {
        tVar = var;
        value = val;
        tVarId = tVar.getId();
        valueId = value.getId();
    }

    public TValue(Mind mind) {
        this.mind = mind;
    }

    public TValue(TVariable tv, ITerm t, IMind mind) {
        this.mind = (Mind) mind;
        this.tVar = tv;
        this.value = t;
        tVarId = tVar.getId();
        valueId = value.getId();
    }

    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putLong(id)
                .putLong(mindId)
                .putByte(isDeleted(mind) ? 1 : 0)
                .putLong(valueId)
                .putLong(tVarId);
        return packet.createMarked();
    }

    public TValue apply(ByteBuffer packet) throws OutOfBufferException {
        id = packet.getLong();
        mindId = packet.getLong();
        if (packet.getByte() != 0) {
            setDeleted(true, mind);
        }
        valueId = packet.getLong();
        tVarId = packet.getLong();
        return this;
    }

    public ITerm getValue(Mind mind) throws Exception {
        if (value == null && valueId != -1) {
            value = mind.getTerms().get(valueId);
        }
        return value;
    }

    public void setValue(Term value) {
        this.value = value;
        valueId = value.getId();
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
        this.id = id;
    }

    public TVariable getTVar(Mind mind) throws Exception {
        if (tVar == null && tVarId != -1) {
            tVar = mind.getTVars().get(tVarId);
        }
        return tVar;
    }

    public void setTVar(TVariable tVar) {
        this.tVar = tVar;
        this.tVarId = tVar.getId();
    }

    public String toString(IMind mind) {
        try {
            return ((mind.getDebugLevel() & Enums.DEBUG_OPTION_VALUES) != 0
                    ? getTVar((Mind) mind).getVarName((Mind) mind) + "="
                    : "") + getValue((Mind) mind).toString();
        } catch (Exception e) {
            System.err.println(new Date());
            e.printStackTrace(System.err);
            return "";
        }
    }

    public void setQuery(Mind mind) throws Exception {
        if (!mind.getQueryValues().containsKey(getTVar(mind))) {
            mind.getQueryValues().put(getTVar(mind), new HashSet<>());
        }
        mind.getQueryValues().get(getTVar(mind)).add(this);
    }

    @Override
    public int getHash() {
        int hash = 3;
        hash = 47 * hash + (int) (valueId ^ (valueId >>> 32));
        hash = 47 * hash + (int) (tVarId ^ (tVarId >>> 32));
        return hash;
    }

    @Override
    public boolean equalsTo(TValue to) {
        return to.getTVarId() == tVarId && to.getValueId() == valueId;
    }

    @Override
    public Mind getMind() {
        return mind;
    }

    @Override
    public TValue setMind(Mind mind) {
        this.mind = mind;
        return this;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 47 * hash + (int) (id ^ (id >>> 32));
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj instanceof TValue && ((TValue) obj).getId() == id;
    }

    @Override
    public int compareTo(TValue o) {
        return (int) (tVarId == o.getTVarId() ? id - o.getId() : tVarId - o.getTVarId());
    }

    public long getValueId() {
        return valueId;
    }

    public long getTVarId() {
        return tVarId;
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
    public UnitType getUnitType() {
        return UnitType.TVALUE;
    }

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
        return value != null && valueId == value.getId();
    }

    @Override
    public Map<String, Object> createMap(IMind mind) throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("mind_id", mindId);
        map.put("deleted", isDeleted(mind));
        map.put("value_id", valueId);
        map.put("tvar_id", tVarId);

        map.put("value", getValue((Mind) mind).getValue());
        map.put("tvar", getTVar((Mind) mind).createMap(mind));

        return map;
    }

    @Override
    public TValue applyMap(Map<String, Object> map) throws Exception {
        id = Long.parseLong(map.get("id") + "");
        mindId = Long.parseLong(map.get("mind_id") + "");
        boolean deleted = Boolean.parseBoolean(map.get("deleted") + "");
        if (deleted) {
            setDeleted(true, mind);
        }
        valueId = Long.parseLong(map.get("value_id") + "");
        tVarId = Long.parseLong(map.get("tvar_id") + "");
        value = null;
        tVar = null;
        return this;
    }
}
