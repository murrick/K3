package org.kanger.units;

import org.kanger.enums.Enums;
import org.kanger.enums.UnitType;
import org.kanger.exception.OutOfBufferException;
import org.kanger.interfaces.IUnit;
import org.kanger.interfaces.IUser;
import org.kanger.storage.ByteBuffer;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 * <p>
 * Элемент подстановочной переменной
 */
public class TVariable implements Comparable<Object>, Externalizable, IUnit<TVariable> {

    private static final long serialVersionUID = 196402070010L;

    private long id = -1;                   // Идентификатор переменной
    private Term name = null;               // Оригинальное подкванторное имя
    private int index = 0;                  // Сквозной индекс переменной
    private Right right = null;             // Ссылка на правило

    private transient long nameId = -1;
    private transient long rightId = -1;
    private transient IUser user = null;

    private transient boolean deleted = false;

    public TVariable() {
    }

    public TVariable(IUser user) {
        this.user = user;
    }

    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putLong(id)
                .putByte(deleted ? 1 : 0)
                .putLong(nameId)
                .putInt(index)
                .putLong(rightId);
        return packet.createMarked();
    }

    public TVariable apply(ByteBuffer packet) throws OutOfBufferException {
        id = packet.getLong();
        deleted = packet.getByte() != 0;
        nameId = packet.getLong();
        index = packet.getInt();
        rightId = packet.getLong();
        return this;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        try {
            apply(new ByteBuffer(in));
        } catch (OutOfBufferException e) {
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.write(pack().getBuffer());
    }

//    @Override
//    public void linkExternal(User user) throws IOException, ClassNotFoundException {
//        this.user = user;
//        name = user.getMind().getTerms().load(nameId);
//        right = user.getMind().getRights().load(rightId);
//    }

    public Term getName() throws IOException, ClassNotFoundException, OutOfBufferException {
        if (name == null) {
            name = user.getMind().getTerms().load(nameId);
        }
        return name;
    }

    public void setName(Term tName) {
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

    public Term getValue() throws IOException, ClassNotFoundException, OutOfBufferException {
        if (user.getMind().getTValues().get(this) != null) {
            return user.getMind().getTValues().get(this).getValue();
        } else {
            return null;
        }
    }

    public TValue getCurrent() {
        if (user.getMind().getTValues().get(this) != null) {
            return user.getMind().getTValues().get(this);
        } else {
            return null;
        }
    }

    public TValue setCurrent(TValue v) {
        return user.getMind().getTValues().set(this, v);
    }

    public TValue setValue(Term value) throws IOException, ClassNotFoundException, OutOfBufferException { //throws TValueOutOfOrderException {
//        if (/*isInside(value) && */!"$$".equals(value.toString())) {
//            if (mind.getTValues().find(this, value) == null) {
//                mind.getSubstituted().createTVar(this);
//            }
        TValue v = value == null ? null : user.getMind().getTValues().add(this, value);
        return user.getMind().getTValues().set(this, v);
//        } else {
//            throw new TValueOutOfOrderException(String.format("%c%d:%s", Enums.TVC, index, value.toString()));
//        }
    }

//    public TValue addValue(Term value) { //throws TValueOutOfOrderException {
////        if (/*isInside(value) && */!"$$".equals(value.toString())) {
////            if (mind.getTValues().find(this, value) == null) {
////                mind.getSubstituted().createTVar(this);
////            }
//        TValue v = user.getMind().getTValues().add(this, value);
//        return v;
////        } else {
////            throw new TValueOutOfOrderException(String.format("%c%d:%s", Enums.TVC, index, value.toString()));
////        }
//    }

//    public void reset() {
//        user.getMind().getTValues().remove(this);
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
    public Right getRight() throws IOException, ClassNotFoundException, OutOfBufferException {
        if (right == null && rightId != -1) {
            right = user.getMind().getRights().load(rightId);
        }
        return right;
    }

    public void setRight(Right right) {
        this.right = right;
        this.rightId = right.getId();
    }

    public String getVarName() throws IOException, ClassNotFoundException, OutOfBufferException {
        switch (user.getMind().getDebugLevel() & 0x00FF) {
            case Enums.DEBUG_LEVEL_INFO:
                return getName().toString();
            case Enums.DEBUG_LEVEL_DEBUG:
                return String.format("[%s]%c%d", getName().toString(), Enums.TVC, index);
            default:
                return getName().toString();
        }
    }

    @Override
    public String toString() {
        try {
            return getVarName() + ((user.getMind().getDebugLevel() & Enums.DEBUG_OPTION_VALUES) != 0 ? (isEmpty() ? "" : (":" + getValue().toString())) : "");
        } catch (IOException | ClassNotFoundException | OutOfBufferException e) {
            e.printStackTrace(System.err);
            return "";
        }
    }

    @Override
    public int getHash() {
        int hash = 3;
        hash = 47 * hash + (int) (rightId ^ (rightId >>> 32));
        hash = 47 * hash + (int) (nameId ^ (nameId >>> 32));
        hash = 47 * hash + index;
        return hash;
    }

    @Override
    public boolean equalsTo(TVariable to) {
        return false;
    }

    @Override
    public IUser getUser() {
        return user;
    }

    @Override
    public TVariable setUser(IUser user) {
        this.user = user;
        return this;
    }

    @Override
    public int hashCode() {
        return ("" + id).hashCode();
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
    public TValue find(Term value) throws IOException, ClassNotFoundException, OutOfBufferException {
        return user.getMind().getTValues().find(this, value);
    }

    public boolean isEmpty() {
        return user.getMind().getTValues().isEmpty(this) || user.getMind().getTValues().get(this) == null;
    }

//    public TValue rewind() {
//        return user.getMind().getTValues().rewind(this);
//    }
//
//    public TValue next(TValue v) {
//        return user.getMind().getTValues().next(v);
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
//        for (Domain d = user.getMind().getDomains().getRoot(); d != null; d = d.getNext()) {
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

    public boolean isQuery() {
        return !isEmpty()
                && user.getMind().getQueryValues().containsKey(this)
                && user.getMind().getQueryValues().get(this).contains(getCurrent());
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

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted() {
        this.deleted = true;
    }

    @Override
    public UnitType getUnitType() {
        return UnitType.TVARIABLE;
    }

}
