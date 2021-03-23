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
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.ByteBuffer;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Dmitry G. Quznetsov on 20.05.15.
 * <p>
 * Элемент подстановочной переменной
 */
public class TVariable implements Comparable<Object>, IUnit<TVariable> {

    private static final long serialVersionUID = 196402070010L;

    private long id = -1;               // Идентификатор переменной
    private long mindId = -1;           // id транзакции
    private int index = 0;              // Сквозной индекс переменной
    private ITerm name = null;          // Оригинальное подкванторное имя
    private IRule rule = null;          // Ссылка на правило

    private long nameId = -1;
    private long ruleId = -1;
    private Mind mind = null;

    public TVariable() {
    }

    public TVariable(Mind mind) {
        this.mind = mind;
    }

    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putLong(id)
                .putLong(mindId)
                .putByte(isDeleted(mind) ? 1 : 0)
                .putLong(nameId)
                .putInt(index)
                .putLong(ruleId);
        return packet.createMarked();
    }

    public TVariable apply(ByteBuffer packet) throws Exception {
        id = packet.getLong();
        mindId = packet.getLong();
        if (packet.getByte() != 0) {
            setDeleted(true, mind);
        }
        nameId = packet.getLong();
        index = packet.getInt();
        ruleId = packet.getLong();
        return this;
    }

    public ITerm getName(Mind mind) throws Exception {
        if (name == null) {
            name = mind.getTerms().get(nameId);
        }
        return name;
    }

    public void setName(ITerm tName) {
        this.name = tName;
        this.nameId = tName.getId();
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

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public ITerm getValue() throws Exception {
        if (mind.getTValues().get(this) != null) {
            return mind.getTValues().get(this).getValue(mind);
        } else {
            return null;
        }
    }

    public TValue getCurrent() {
        if (mind.getTValues().get(this) != null) {
            return mind.getTValues().get(this);
        } else {
            return null;
        }
    }

    public TValue setCurrent(TValue v) {
        return mind.getTValues().set(this, v);
    }

    public TValue setValue(ITerm value) throws Exception { //throws TValueOutOfOrderException {
        TValue v = null;
        if (value != null) {
            v = mind.getTValues().add(this, value);
        }
        return setCurrent(v);
    }

    public IRule getRule(Mind mind) throws Exception {
        if (rule == null && ruleId != -1) {
            rule = mind.getRules().get(ruleId);
        }
        return rule;
    }

    public void setRule(IRule rule) {
        this.rule = rule;
        this.ruleId = rule.getId();
    }

    public String getVarName(Mind mind) throws Exception {
        switch (mind.getDebugLevel() & 0x00FF) {
            case Enums.DEBUG_LEVEL_DEBUG:
                return String.format("%c%d", Enums.TVC, index);
            default:
                return getName(mind).toString();
        }
    }

    public String toString(IMind mind) {
        try {
            return getVarName((Mind) mind) + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_VALUES) != 0 ? (isEmpty() ? "" : (":" + getValue().toString())) : "");
        } catch (Exception e) {
            System.err.println(new Date());
            e.printStackTrace(System.err);
            return "";
        }
    }

    @Override
    public int getHash() {
        int hash = 3;
        hash = 47 * hash + (int) (ruleId ^ (ruleId >>> 32));
        hash = 47 * hash + (int) (nameId ^ (nameId >>> 32));
        hash = 47 * hash + index;
        return hash;
    }

    @Override
    public boolean equalsTo(TVariable to) {
        return false;
    }

    @Override
    public Mind getMind() {
        return mind;
    }

    @Override
    public TVariable setMind(Mind mind) {
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
    public boolean equals(Object t) {
        return !(t == null || !(t instanceof TVariable)) && ((TVariable) t).id == id;
    }

    public TValue find(ITerm value) throws Exception {
        return mind.getTValues().find(this, value);
    }

    public boolean isEmpty() {
        return mind.getTValues().isEmpty(this) || mind.getTValues().get(this) == null;
    }

    public boolean isQuery(Mind mind) {
        return !isEmpty()
                && mind.getQueryValues().containsKey(this)
                && mind.getQueryValues().get(this).contains(getCurrent());
    }

    @Override
    public int compareTo(Object o) {
        return o instanceof TVariable
                ? Integer.valueOf(index).compareTo(((TVariable) o).getIndex())
                : Integer.valueOf(index).compareTo(((Term) o).getIndex());
    }

    @Override
    public boolean isDeleted(IMind mind) {
        return ((Mind) mind).isUnitDeleted(this);
    }

    @Override
    public void setDeleted(boolean on, Mind mind) throws Exception {
        mind.setUnitDeleted(this, on);
        for (TValue v : mind.getTValues()) {
            if (v.getTVar(mind).getId() == id) {
                v.setDeleted(on, mind);
            }
        }
    }

    @Override
    public UnitType getUnitType() {
        return UnitType.TVARIABLE;
    }

    @Override
    public long getMindId() {
        return mindId;
    }

    @Override
    public void setMindId(long mindId) {
        this.mindId = mindId;
    }

    public long getRuleId() {
        return ruleId;
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
        map.put("ruleId", ruleId);
        map.put("index", index);

        map.put("name", getName((Mind) mind).getValue());
        map.put("rule", getRule((Mind) mind).getOrigin());
        return map;
    }

    @Override
    public TVariable applyMap(Map<String, Object> map) throws Exception {
        id = Long.parseLong(map.get("id") + "");
        mindId = Long.parseLong(map.get("mind_id") + "");
        boolean deleted = Boolean.parseBoolean(map.get("deleted") + "");
        if (deleted) {
            setDeleted(true, mind);
        }
        nameId = Long.parseLong(map.get("name_id") + "");
        ruleId = Long.parseLong(map.get("rule_id") + "");
        index = Integer.parseInt(map.get("index") + "");
        name = null;
        rule = null;
        return this;
    }

    public void incFloodControl(ITerm t) throws Exception {
        if (!mind.getFloodControl().containsKey(this)) {
            Term r = mind.getTerms().getRoot();
            long[] val = new long[]{r == null ? 0 : r.getId(), 0L};
            mind.getFloodControl().put(this, val);
        } else {
            long lastTermId = mind.getFloodControl().get(this)[0];
            if (t.getId() > lastTermId) {
                long counter = mind.getFloodControl().get(this)[1];
                ++counter;
                mind.getFloodControl().get(this)[1] = counter;
            }
        }
    }

    public int getFloodCounter() {
        if (mind.getFloodControl().containsKey(this)) {
            return (int) mind.getFloodControl().get(this)[1];
        } else {
            return 0;
        }
    }

}
