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
import org.kanger.compiler.Parser;
import org.kanger.enums.UnitType;
import org.kanger.exception.OutOfBufferException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IPredicate;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.ByteBuffer;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Каноническое определение логического отношения KANGER.
 *
 * <p><strong>Архитектурная роль.</strong> {@code Predicate} связывает
 * канонический name Term и arity. Утверждения и составные правила ссылаются на
 * это определение по ID; множество использующих его assertion-правил является
 * контекстной проекцией конкретного {@link Mind}, а не частью identity самого
 * предиката.</p>
 *
 * <p><strong>Inside.</strong> Устойчивое определение включает operational ID,
 * owner Mind ID, name Term ID и range. Factory canonicalization использует пару
 * {@code (name Term, arity)}; физический ID остаётся operational identity уже
 * зарегистрированного объекта.</p>
 *
 * <p><strong>Outside.</strong> Lazy hydration имени, transaction-visible
 * deletion, набор видимых assertion-правил и признак system operation зависят
 * от переданного Mind и текущего runtime registry.</p>
 *
 * <p><strong>Lifecycle.</strong> Удаление является контекстной пометкой и не
 * уничтожает определение, необходимое существующим Rule-ссылкам и rollback.
 * {@link #getSolves(IMind)} сканирует текущую видимость правил и не является
 * persistent reverse index.</p>
 *
 * <p><strong>Инварианты.</strong> Predicate definition != assertion set;
 * operational ID != structural key; empty assertion set != absent predicate;
 * deleted != nonexistent; diagnostic rendering != persistence protocol.</p>
 */
public class Predicate implements IUnit<Predicate>, IPredicate {

    private static final long serialVersionUID = 196402070004L;

    private long id = -1;
    private long mindId = -1;
    private ITerm name = null;
    private int range = 0;

    private Mind mind = null;
    private long nameId = -1;

    public Predicate() {
    }

    /** Creates an unresolved canonical definition from name and arity. */
    public Predicate(ITerm name, int range) {
        this.name = name;
        this.range = range;
        this.nameId = name.getId();
    }

    /** Creates a hydration shell owned by the supplied Mind. */
    public Predicate(Mind mind) {
        this.mind = mind;
    }

    /** @return persistent representation containing IDs, deletion marker and arity */
    @Override
    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putLong(id)
                .putLong(mindId)
                .putByte(isDeleted(mind) ? 1 : 0)
                .putLong(nameId)
                .putInt(range);
        return packet.createMarked();
    }

    /**
     * Applies serialized state without resolving the name Term.
     *
     * @param packet serialized predicate state
     * @return this hydrated shell
     * @throws OutOfBufferException if the packet is incomplete
     */
    @Override
    public Predicate apply(ByteBuffer packet) throws OutOfBufferException {
        id = packet.getLong();
        mindId = packet.getLong();
        if (packet.getByte() != 0) {
            setDeleted(true, mind);
        }
        nameId = packet.getLong();
        range = packet.getInt();
        return this;
    }

    /** Resolves and returns the canonical name in the supplied Mind. */
    @Override
    public String getName(IMind mind) throws Exception {
        if (name == null) {
            name = mind.getTerms().get(nameId);
        }
        return name.getValue() + "";
    }

    /** Sets the stable name reference; it does not change assertion membership. */
    public void setName(ITerm name) {
        this.name = name;
        this.nameId = name.getId();
    }

    @Override
    public int getRange() {
        return range;
    }

    /** Sets arity before canonical publication. */
    public void setRange(int range) {
        this.range = range;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
        this.id = id;
    }

    /**
     * Returns assertion Rules visible in the supplied Mind.
     *
     * @param mind transaction context defining Rule visibility
     * @return visible non-deleted assertion Rules using this predicate
     * @throws Exception if Rule or Domain hydration fails
     */
    @Override
    public Set<IRule> getSolves(IMind mind) throws Exception {
        Set<IRule> set = new HashSet<>();
        for (IRule r : mind.getRules()) {
            if (r.isStored() && !r.isDeleted(mind) && getId() == ((Rule) r).getPredicateId()) {
                set.add(r);
            }
        }
        return set;
    }

    /** Returns a context-sensitive diagnostic rendering, not a stable protocol. */
    public String toString(IMind mind) {
        try {
            return getName(mind) + "(" + range + ")";
        } catch (Exception e) {
            System.err.println(new Date());
            e.printStackTrace(System.err);
            return "";
        }
    }

    /** Hashes the structural key {@code (nameId, range)} for candidate lookup. */
    @Override
    public int getHash() {
        int hash = 3;
        hash = 47 * hash + (int) (nameId ^ (nameId >>> 32));
        hash = 47 * hash + range;
        return hash;
    }

    /** Compares canonical definition keys independently of operational IDs. */
    @Override
    public boolean equalsTo(Predicate to) {
        return to.getNameId() == nameId && to.getRange() == range;
    }

    @Override
    public Mind getMind() {
        return mind;
    }

    /** Selects the Mind used for hydration and deletion projection. */
    @Override
    public Predicate setMind(Mind mind) {
        this.mind = mind;
        return this;
    }

    @Override
    public boolean isDeleted(IMind mind) {
        return ((Mind) mind).isUnitDeleted(this);
    }

    /** Changes transaction-visible deletion without destroying identity. */
    @Override
    public void setDeleted(boolean on, Mind mind) {
        mind.setUnitDeleted(this, on);
    }

    /** Hashes operational identity, not the structural key. */
    @Override
    public int hashCode() {
        int hash = 3;
        hash = 47 * hash + (int) (id ^ (id >>> 32));
        return hash;
    }

    /** @return ID of the canonical name Term */
    public long getNameId() {
        return nameId;
    }

    @Override
    public UnitType getUnitType() {
        return UnitType.PREDICATE;
    }

    @Override
    public long getMindId() {
        return mindId;
    }

    @Override
    public void setMindId(long mindId) {
        this.mindId = mindId;
    }

    /** @return whether the name Term is currently hydrated */
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
        map.put("name", getName(mind));
        return map;
    }

    @Override
    public Predicate applyMap(Map<String, Object> map) throws Exception {
        id = Long.parseLong(map.get("id") + "");
        mindId = Long.parseLong(map.get("mind_id") + "");
        boolean deleted = Boolean.parseBoolean(map.get("deleted") + "");
        if (deleted) {
            setDeleted(true, mind);
        }
        nameId = Long.parseLong(map.get("name_id") + "");
        range = Integer.parseInt(map.get("range") + "");
        name = null;
        return this;
    }

    /** Tests whether no visible assertion Rule uses this definition. */
    @Override
    public boolean isEmpty(IMind mind) throws Exception {
        return getSolves(mind).isEmpty();
    }

    /**
     * Tests whether runtime syntax registration contains an operation with the
     * same name and arity. This classification is not part of predicate identity.
     */
    public boolean isSystem(Mind mind) throws Exception {
        return Parser.getOp(getName(mind), getRange()) != null;
    }

}
