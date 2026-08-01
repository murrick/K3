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

import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Определение подстановочной переменной, принадлежащей одному {@link IRule}.
 *
 * <p><strong>Архитектурная роль.</strong> {@code TVariable} представляет
 * потенциальное место значения в структуре правила. Её identity задаётся
 * operational ID и rule-local координатами имени/индекса; текущее значение не
 * хранится в самой переменной, а существует как контекстная {@link TValue}
 * проекция в активном {@link Mind}.</p>
 *
 * <p><strong>Inside.</strong> Устойчивое определение включает ссылку на Rule,
 * исходное подкванторное имя, сквозной индекс и persistent identifiers. Эти
 * данные описывают саму переменную и не меняются при переборе подстановок.</p>
 *
 * <p><strong>Outside.</strong> Текущее значение, query membership, flood
 * control и видимость удаления принадлежат конкретному Mind. Методы без
 * параметра Mind используют thread-confined active execution view; это не
 * переносит ownership переменной в вызывающий child Mind.</p>
 *
 * <p><strong>Ownership и concurrency.</strong> Поле owner/default Mind остаётся
 * стабильным. Временный {@code runtimeMind} хранится как слабая thread-local
 * ссылка, чтобы concurrent sibling Minds не перезаписывали общий execution
 * context и завершённый child не удерживался объектом переменной.</p>
 *
 * <p><strong>Canonicalization.</strong> Присваивание значения создаёт или
 * переиспользует канонический {@link TValue} по паре
 * {@code (TVariable id, Term id)}. {@link #setValue(ITerm)} не мутирует identity
 * переменной. {@link #setCurrent(TValue)} переключает только текущую проекцию
 * в active Mind.</p>
 *
 * <p><strong>Удаление.</strong> Удаление переменной является контекстной
 * пометкой и симметрично распространяется на её TValue в том же Mind. Оно не
 * уничтожает определение, Rule ownership или persistent ID.</p>
 *
 * <p><strong>Инварианты.</strong> variable definition != current value;
 * owner Mind != active execution Mind; Rule ownership != query membership;
 * undefined value != deleted variable; serialized form != canonical identity.</p>
 */
public class TVariable implements Comparable<Object>, IUnit<TVariable> {

    private static final long serialVersionUID = 196402070010L;

    private long id = -1;
    private long mindId = -1;
    private int index = 0;
    private ITerm name = null;
    private IRule rule = null;

    private long nameId = -1;
    private long ruleId = -1;

    /** Stable owner/default context for this transaction-owned object. */
    private Mind mind = null;

    /**
     * Parent TVariables are shared by concurrent sibling Minds. Historically
     * every hydration overwrote one mutable Mind field, so no-arg runtime
     * methods could read another sibling's TValueFactory. The active execution
     * view is now thread-confined and weak, while the owner/default remains
     * stable and does not retain completed child Minds.
     */
    private final transient ThreadLocal<WeakReference<Mind>> runtimeMind =
            new ThreadLocal<>();

    public TVariable() {
    }

    public TVariable(Mind mind) {
        this.mind = mind;
        runtimeMind.set(new WeakReference<>(mind));
    }

    private Mind activeMind() {
        WeakReference<Mind> reference = runtimeMind.get();
        Mind active = reference == null ? null : reference.get();
        return active == null ? mind : active;
    }

    /** @return persistent representation of the variable definition and deletion projection */
    public ByteBuffer pack() {
        Mind active = activeMind();
        ByteBuffer packet = new ByteBuffer()
                .putLong(id)
                .putLong(mindId)
                .putByte(isDeleted(active) ? 1 : 0)
                .putLong(nameId)
                .putInt(index)
                .putLong(ruleId);
        return packet.createMarked();
    }

    /**
     * Applies a persistent representation without resolving referenced objects.
     *
     * @param packet serialized variable state
     * @return this hydrated shell
     * @throws Exception if packet decoding or deletion restoration fails
     */
    public TVariable apply(ByteBuffer packet) throws Exception {
        id = packet.getLong();
        mindId = packet.getLong();
        if (packet.getByte() != 0) {
            setDeleted(true, activeMind());
        }
        nameId = packet.getLong();
        index = packet.getInt();
        ruleId = packet.getLong();
        return this;
    }

    /** Resolves the original variable name in the supplied Mind. */
    public ITerm getName(Mind mind) throws Exception {
        if (name == null) {
            name = mind.getTerms().get(nameId);
        }
        return name;
    }

    /** Sets the stable name reference; it does not assign a runtime value. */
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

    /**
     * Returns the Term projected by the current TValue in the active Mind.
     *
     * @return current donor Term or {@code null} when unbound
     * @throws Exception if the TValue or Term cannot be resolved
     */
    public ITerm getValue() throws Exception {
        Mind active = activeMind();
        if (active != null && active.getTValues().get(this) != null) {
            return active.getTValues().get(this).getValue(active);
        } else {
            return null;
        }
    }

    /** @return current TValue projection in the active Mind, or {@code null} */
    public TValue getCurrent() {
        Mind active = activeMind();
        if (active != null && active.getTValues().get(this) != null) {
            return active.getTValues().get(this);
        } else {
            return null;
        }
    }

    /**
     * Replaces the active Mind's current projection without changing variable identity.
     *
     * @param v canonical TValue or {@code null} to clear the projection
     * @return previous/current factory result according to TValueFactory contract
     */
    public TValue setCurrent(TValue v) {
        Mind active = activeMind();
        return active == null ? null : active.getTValues().set(this, v);
    }

    /**
     * Canonicalizes the pair {@code (this, value)} and makes it current.
     * Passing {@code null} clears the current projection.
     */
    public TValue setValue(ITerm value) throws Exception {
        Mind active = activeMind();
        TValue v = null;
        if (value != null && active != null) {
            v = active.getTValues().add(this, value);
        }
        return setCurrent(v);
    }

    /** Resolves the Rule that owns this variable. */
    public IRule getRule(Mind mind) throws Exception {
        if (rule == null && ruleId != -1) {
            rule = mind.getRules().get(ruleId);
        }
        return rule;
    }

    /** Sets the stable Rule ownership reference. */
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
            return getVarName((Mind) mind)
                    + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_VALUES) != 0
                    ? (isEmpty() ? "" : (":" + getValue().toString())) : "");
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

    /**
     * TVariable does not define structural equivalence beyond canonical identity.
     * The historical method therefore deliberately returns {@code false}.
     */
    @Override
    public boolean equalsTo(TVariable to) {
        return false;
    }

    @Override
    public Mind getMind() {
        return activeMind();
    }

    /**
     * Selects a thread-local execution context while preserving stable ownership.
     */
    @Override
    public TVariable setMind(Mind mind) {
        runtimeMind.set(new WeakReference<>(mind));
        if (this.mind == null || mind.getNext() == null || mindId == mind.getId()) {
            this.mind = mind;
        }
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

    /** Finds the canonical TValue for this variable and donor Term. */
    public TValue find(ITerm value) throws Exception {
        Mind active = activeMind();
        return active == null ? null : active.getTValues().find(this, value);
    }

    /** @return {@code true} when no current TValue exists in the active Mind */
    public boolean isEmpty() {
        Mind active = activeMind();
        return active == null
                || active.getTValues().isEmpty(this)
                || active.getTValues().get(this) == null;
    }

    /** Tests whether the current TValue belongs to the query-result projection. */
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

    /**
     * Changes deletion visibility and propagates it to this variable's TValue objects.
     */
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
            setDeleted(true, activeMind());
        }
        nameId = Long.parseLong(map.get("name_id") + "");
        ruleId = Long.parseLong(map.get("rule_id") + "");
        index = Integer.parseInt(map.get("index") + "");
        name = null;
        rule = null;
        return this;
    }

    /** Updates query-local flood-control telemetry for an observed Term. */
    public void incFloodControl(ITerm t) throws Exception {
        Mind active = activeMind();
        if (active == null) {
            return;
        }
        if (!active.getFloodControl().containsKey(this)) {
            Term r = active.getTerms().getRoot();
            long[] val = new long[]{r == null ? 0 : r.getId(), 0L};
            active.getFloodControl().put(this, val);
        } else {
            long lastTermId = active.getFloodControl().get(this)[0];
            if (t.getId() > lastTermId) {
                long counter = active.getFloodControl().get(this)[1];
                ++counter;
                active.getFloodControl().get(this)[1] = counter;
            }
        }
    }

    /** @return query-local flood-control counter for the active Mind */
    public int getFloodCounter() {
        Mind active = activeMind();
        if (active != null && active.getFloodControl().containsKey(this)) {
            return (int) active.getFloodControl().get(this)[1];
        } else {
            return 0;
        }
    }

}
