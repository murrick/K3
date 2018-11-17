package kanger.primitives;

import java.io.*;
import java.util.*;
import kanger.*;
import kanger.enums.*;
import kanger.interfaces.*;

/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 * <p>
 * Элемент подстановочной переменной
 */
public class TVariable implements IValue<TValue>, Comparable<Object> {


    
    private Right right = null;             // Ссылка на правило
    private long id = -1;                   // Идентификатор переменной
    private TVariable next = null;          // Следующая переменная

    private String name = "";               // Оригинальное подкванторное имя
    private int index = 0;                  // Сквохзной индекс переменной

    private Mind mind = null;

    public TVariable(Mind mind) {
        this.mind = mind;
    }

    public TVariable(DataInputStream dis, Mind mind) throws IOException {
        id = dis.readLong();
        mind.getTVariableLinks().put(this, dis.readLong());
//        long did = dis.readLong();
//        if (did != -1) {
//            area = mind.getTerms().get(did);
//        } else {
//            area = null;
//        }
        index = dis.readInt();
        long did = dis.readLong();
        right = mind.getRights().get(did);
        name = dis.readUTF();
        this.mind = mind;
    }

    public String getName() {
        return name;
    }

    public void setName(String tName) {
        this.name = tName;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public Term getValue() {
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

    public TValue setValue(Term value) { //throws TValueOutOfOrderException {
//        if (/*isInside(value) && */!"$$".equals(value.toString())) {
//            if (mind.getTValues().find(this, value) == null) {
//                mind.getSubstituted().createTVar(this);
//            }
        TValue v = mind.getTValues().add(this, value);
        return mind.getTValues().set(this, v);
//        } else {
//            throw new TValueOutOfOrderException(String.format("%c%d:%s", Enums.TVC, index, value.toString()));
//        }
    }

    public TValue addValue(Term value) { //throws TValueOutOfOrderException {
//        if (/*isInside(value) && */!"$$".equals(value.toString())) {
//            if (mind.getTValues().find(this, value) == null) {
//                mind.getSubstituted().createTVar(this);
//            }
        TValue v = mind.getTValues().add(this, value);
        return v;
//        } else {
//            throw new TValueOutOfOrderException(String.format("%c%d:%s", Enums.TVC, index, value.toString()));
//        }
    }

    public void clear() {
        mind.getTValues().remove(this);
//        if (mind.getTValues().createCVar(this).isEmpty()) {
//            mind.getTValues().createCVar(this).setRoot(null);
//            mind.getSubstituted().createTVar(this);
//        }
    }

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
    public Right getRight() {
        return right;
    }

    public void setRight(Right right) {
        this.right = right;
    }

    public TVariable getNext() {
        return next;
    }

    public void setNext(TVariable next) {
        this.next = next;
    }

    public List<Domain> getSrcSolves() {
        if (mind.getTValues().get(this) != null) {
            return mind.getTValues().get(this).getSrcSolves();
        } else {
            return null;
        }
    }

//    public void setSrcSolve(Domain d) {
//        if (mind.getTValues().containsKey(this)) {
//            mind.getTValues().put(this, new TValue());
//        } else {
//            mind.getTValues().createCVar(this).setSrcSolve(d);
//        }
//    }

    //    public Domain getSrcValue() {
//        if (mind.getTValues().containsKey(this)) {
//            return mind.getTValues().createCVar(this).getSrcSolve();
//        } else {
//            return null;
//        }
//    }
//
    public List<Domain> getDstSolves() {
        if (mind.getTValues().get(this) != null) {
            return mind.getTValues().get(this).getDstSolves();
        } else {
            return null;
        }
    }

    public int getDstIndex(Domain d) {
        if (mind.getTValues().get(this) != null) {
            int pos = mind.getTValues().get(this).getDstSolves().indexOf(d);
            if (pos != -1) {
                return mind.getTValues().get(this).getPosSolves().get(pos);
            } else {
                return -1;
            }
        } else {
            return -1;
        }
    }

    public Domain getSrcSolve(int index) {
        if (mind.getTValues().get(this) != null) {
            int pos = mind.getTValues().get(this).getPosSolves().indexOf(index);
            if (pos != -1) {
                return mind.getTValues().get(this).getSrcSolves().get(pos);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

//    public List<Integer> getPosSolves() {
//        if (mind.getTValues().get(this) != null) {
//            return mind.getTValues().get(this).getPosSolves();
//        } else {
//            return null;
//        }
//    }

//    public void setDstSolve(Domain d) {
//        if (mind.getTValues().containsKey(this)) {
//            mind.getTValues().put(this, new TValue());
//        } else {
//            mind.getTValues().createCVar(this).setDstSolve(d);
//        }
//    }

//    public Domain getDstValue() {
//        if (mind.getTValues().containsKey(this)) {
//            return mind.getTValues().createCVar(this).getDstSolve();
//        } else {
//            return null;
//        }
//    }
//
//    public boolean isDestFor(Domain d) {
//        if (mind.getTValues().containsKey(this)) {
//            return mind.getTValues().createCVar(this).isDestFor(d);
//        } else {
//            return false;
//        }
//    }

    //    public Predicate getPredicate() {
//        return p;
//    }
//
//    public void setPredicate(Predicate p) {
//        this.p = p;
//    }
//
    public String getVarName() {
        switch (mind.getDebugLevel() & 0x00FF) {
            case Enums.DEBUG_LEVEL_INFO:
                return name;
            case Enums.DEBUG_LEVEL_DEBUG:
                return String.format("[%s]%c%d", name, Enums.TVC, index);
            default:
                return name;
        }
    }

    @Override
    public String toString() {
        return getVarName() + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_VALUES) != 0 ? (isEmpty() ? "" : (":" + getValue().toString())) : "");
    }

    public void writeCompiledData(DataOutputStream dos) throws IOException {
        dos.writeLong(id);
        dos.writeLong(right.getId());
        dos.writeInt(index);
//        dos.writeLong(area == null ? -1 : area.getId());
        dos.writeLong(right == null ? -1 : right.getId());
        dos.writeUTF(name);
    }

    @Override
    public boolean equals(Object t) {
        return !(t == null || !(t instanceof TVariable)) && ((TVariable) t).id == id;
    }

    public boolean isInside(Term c) {

        return (c == null || !c.isCVar()
                && c.getRight() == getRight()
                && c.getIndex() < c.getIndex());
    }

    public boolean contains(Term value) {
        return find(value) != null;
    }

    //
    public TValue find(Term value) {
        return mind.getTValues().find(this, value);
    }

    public boolean isEmpty() {
        return mind.getTValues().isEmpty(this) || mind.getTValues().get(this) == null;
    }

    public TValue rewind() {
        return mind.getTValues().rewind(this);
    }

    public TValue next(TValue v) {
        return mind.getTValues().next(v);
    }

//    public TValue rewindTop() {
//        return mind.getTValues().rewindTop(this);
//    }
//
//    public TValue nextTop(TValue v) {
//        return mind.getTValues().nextTop(this, v);
//    }

    public Set<Domain> getUsage() {
        Set<Domain> set = new HashSet<>();
        for (Domain d = mind.getDomains().getRoot(); d != null; d = d.getNext()) {
            if (d.contains(this)) {
                set.add(d);
            }
        }
        return set;
    }

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
//    public void clear() {
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
    public boolean isTSet() {
        return true;
    }

    @Override
    public boolean isFSet() {
        return false;
    }

    @Override
    public boolean isVSet() {
        return false;
    }

    @Override
    public boolean isTerm() {
        return false;
    }

    @Override
    public boolean isCVar() {
        return !isEmpty() && getValue().isCVar();
    }

    @Override
    public TVariable getT() {
        return null;
    }

    @Override
    public Function getF() {
        return null;
    }

    @Override
    public TValue getV() {
        return null;
    }
}
