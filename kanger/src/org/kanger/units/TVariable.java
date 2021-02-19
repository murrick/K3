package org.kanger.units;

import org.kanger.Mind;
import org.kanger.enums.Enums;
import org.kanger.enums.UnitType;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.ByteBuffer;

/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 * <p>
 * Элемент подстановочной переменной
 */
public class TVariable implements Comparable<Object>, IUnit<TVariable> {

    private static final long serialVersionUID = 196402070010L;

    private long id = -1;                   // Идентификатор переменной
    private long mindId = -1;                                   // id транзакции
    private ITerm name = null;               // Оригинальное подкванторное имя
    private int index = 0;                  // Сквозной индекс переменной
    private IRule rule = null;             // Ссылка на правило

    private transient long nameId = -1;
    private transient long ruleId = -1;
    private transient Mind mind = null;

//    private transient boolean deleted = false;

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

    public ITerm getName() throws Exception {
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

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public ITerm getValue() throws Exception {
        if (mind.getTValues().get(this) != null) {
            return mind.getTValues().get(this).getValue();
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
//        if (/*isInside(value) && */!"$$".equals(value.toString())) {
//            if (mind.getTValues().find(this, value) == null) {
//                mind.getSubstituted().createTVar(this);
//            }
        TValue v = null;
        if (value != null) {
            v = mind.getTValues().add(this, value);

//            List<TValue> list = new ArrayList<>();
//            list.add(v);
//            mind.addTSolve(list);
        }
//        value == null ? null : mind.getTValues().add(this, value);
        return setCurrent(v);
//        return mind.getTValues().set(this, v);
//        } else {
//            throw new TValueOutOfOrderException(String.format("%c%d:%s", Enums.TVC, index, value.toString()));
//        }
    }

//    public TValue addValue(Term value) { //throws TValueOutOfOrderException {
////        if (/*isInside(value) && */!"$$".equals(value.toString())) {
////            if (mind.getTValues().find(this, value) == null) {
////                mind.getSubstituted().createTVar(this);
////            }
//        TValue v = mind.getTValues().add(this, value);
//        return v;
////        } else {
////            throw new TValueOutOfOrderException(String.format("%c%d:%s", Enums.TVC, index, value.toString()));
////        }
//    }

//    public void reset() {
//        mind.getTValues().remove(this);
////        if (mind.getTValues().createCVar(this).isEmpty()) {
////            mind.getTValues().createCVar(this).setRoot(null);
////            mind.getSubstituted().createTVar(this);
////        }
//    }

    //    public TSubst addValue(Term value) throws TValueOutOfOrderException {
//        if (!mind.getTValues().containsKey(this)) {
//            mind.getTValues().put(this, new TValue());
//        }
//        if (!isInside(value)) {
//            if (mind.getTValues().createCVar(this).contains(value) == -1) {
//                mind.getSubstituted().createTVar(id);
//            }
//            return mind.getTValues().createCVar(this).addValue(value);
//        } else {
//            throw new TValueOutOfOrderException(value.toString());
//        }
//    }
    //    public int getOwner() {
//        if (mind.getTValues().containsKey(this)) {
//            return mind.getTValues().createCVar(this).getLevel();
//        } else {
//            return 0;
//        }
//    }
//
//    public void setOwner(int owner) {
//        if (!mind.getTValues().containsKey(this)) {
//            mind.getTValues().put(this, new TValue());
//        }
//        mind.getTValues().createCVar(this).setLevel(owner);
//
//    }
    public IRule getRule() throws Exception {
        if (rule == null && ruleId != -1) {
            rule = mind.getRules().get(ruleId);
        }
        return rule;
    }

    public void setRule(IRule rule) {
        this.rule = rule;
        this.ruleId = rule.getId();
    }

    public String getVarName() throws Exception {
        switch (mind.getDebugLevel() & 0x00FF) {
            case Enums.DEBUG_LEVEL_DEBUG:
                return String.format("%c%d", Enums.TVC, index);
            default:
                return getName().toString();
        }
    }

    @Override
    public String toString() {
        try {
            return getVarName() + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_VALUES) != 0 ? (isEmpty() ? "" : (":" + getValue().toString())) : "");
        } catch (Exception e) {
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
//        return ("" + id).hashCode();
    }

    @Override
    public boolean equals(Object t) {
        return !(t == null || !(t instanceof TVariable)) && ((TVariable) t).id == id;
    }

//    public boolean isInside(Term c) throws IOException, ClassNotFoundException {
//
//        return (c == null || !c.isCVariable()
//                && c.getRight() == getRight()
//                && c.getIndex() < c.getIndex());
//    }

//    public boolean contains(Term value) {
//        return find(value) != null;
//    }

    //
    public TValue find(ITerm value) throws Exception {
        return mind.getTValues().find(this, value);
    }

    public boolean isEmpty() {
        return mind.getTValues().isEmpty(this) || mind.getTValues().get(this) == null;
    }

//    public TValue rewind() {
//        return mind.getTValues().rewind(this);
//    }
//
//    public TValue next(TValue v) {
//        return mind.getTValues().next(v);
//    }

//    public TValue rewindTop() {
//        return mind.getTValues().rewindTop(this);
//    }
//
//    public TValue nextTop(TValue v) {
//        return mind.getTValues().nextTop(this, v);
//    }

//    public Set<Domain> getUsage() {
//        Set<Domain> set = new HashSet<>();
//        for (Domain d = mind.getDomains().getRoot(); d != null; d = d.getNext()) {
//            if (d.contains(this)) {
//                set.add(d);
//            }
//        }
//        return set;
//    }
//
    //    public void mark() {
//        if (!mind.getTValues().containsKey(this)) {
//            mind.getTValues().put(this, new TValueFactory(mind));
//        }
//        mind.getTValues().createCVar(this).mark();
//    }
//
//    public void release() {
//        if (mind.getTValues().containsKey(this)) {
//            mind.getTValues().createCVar(this).release();
//        }
//    }
//
//    public void commit() {
//        if (mind.getTValues().containsKey(this)) {
//            mind.getTValues().createCVar(this).commit();
//        }
//    }
//
//    public void reset() {
//        if (mind.getTValues().containsKey(this)) {
//            mind.getTValues().remove(this);
//        }
//    }
//
//    public void setQuery() {
//        if (!mind.getQueryValues().containsKey(id)) {
//            mind.getQueryValues().put(id, new HashSet<>());
//        }
//        mind.getQueryValues().createCVar(id).createTVar(getValue().getId());
//    }

    public boolean isQuery(Mind mind) {
        return !isEmpty()
                && mind.getQueryValues().containsKey(this)
                && mind.getQueryValues().get(this).contains(getCurrent());
    }

//    public boolean isBlocked() {
//        return mind.getTValues().get(this) != null && mind.getTValues().get(this).isBlocked();
//    }

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
            if (v.getTVar().getId() == id) {
                v.setDeleted(on, mind);
            }
        }
    }

    @Override
    public UnitType getUnitType() {
        return UnitType.TVARIABLE;
    }

//    @Override
//    public TVariable commit(Mind m) throws Exception {
//        setName(name.commit(m));
//        setMind(m);
//        for (TValue v : mind.getTValues()) {
//            if (v.getTVarId() == id) {
//                v.commit(m);
//            }
//        }
//
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

    public long getRuleId() {
        return ruleId;
    }

    @Override
    public boolean isLoaded() {
        return name != null && nameId == name.getId();
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
