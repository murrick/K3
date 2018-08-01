package kanger.primitives;

import java.io.*;
import java.util.*;

import kanger.*;
import kanger.enums.*;
import kanger.exception.*;
import kanger.factory.TValueFactory;

/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 * <p>
 * Элемент подстановочной переменной
 */
public class TVariable {
    private String name = "";               // Оригинальное подкванторное имя
    private Term area = null;               // Ссылка на область определения
    private Right right = null;             // Ссылка на правило
    private long id = -1;                   // Идентификатор переменной
    private TVariable next = null;          // Следующая переменная

    private Mind mind = null;

    public TVariable(Mind mind) {
        this.mind = mind;
    }

    public TVariable(DataInputStream dis, Mind mind) throws IOException {
        id = dis.readLong();
        mind.gettVariableLinks().put(this, dis.readLong());
        long did = dis.readLong();
        if (did != -1) {
            area = mind.getTerms().get(did);
        } else {
            area = null;
        }
        did = dis.readLong();
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

    public Term getArea() {
        return area;
    }

    public void setArea(Term area) {
        this.area = area;
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

    public TValue setValue(Term value) throws TValueOutOfOrver {
        if (!isInside(value) && !"$$".equals(value.toString())) {
//            if (mind.getTValues().find(this, value) == null) {
//                mind.getSubstituted().add(this);
//            }
            return mind.getTValues().add(this, value);
        } else {
            throw new TValueOutOfOrver(value.toString());
        }
    }

    public void delValue() {
        mind.getTValues().remove(this);
//        if (mind.getTValues().get(this).isEmpty()) {
//            mind.getTValues().get(this).setRoot(null);
//            mind.getSubstituted().add(this);
//        }
    }

    //    public TSubst addValue(Term value) throws TValueOutOfOrver {
//        if (!mind.getTValues().containsKey(this)) {
//            mind.getTValues().put(this, new TValue());
//        }
//        if (!isInside(value)) {
//            if (mind.getTValues().get(this).contains(value) == -1) {
//                mind.getSubstituted().add(id);
//            }
//            return mind.getTValues().get(this).addValue(value);
//        } else {
//            throw new TValueOutOfOrver(value.toString());
//        }
//    }
    //    public int getOwner() {
//        if (mind.getTValues().containsKey(this)) {
//            return mind.getTValues().get(this).getLevel();
//        } else {
//            return 0;
//        }
//    }
//
//    public void setOwner(int owner) {
//        if (!mind.getTValues().containsKey(this)) {
//            mind.getTValues().put(this, new TValue());
//        }
//        mind.getTValues().get(this).setLevel(owner);
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

    public Domain getSrcSolve() {
        if (mind.getTValues().get(this) != null) {
            return mind.getTValues().get(this).getSrcSolve();
        } else {
            return null;
        }
    }

//    public void setSrcSolve(Domain d) {
//        if (mind.getTValues().containsKey(this)) {
//            mind.getTValues().put(this, new TValue());
//        } else {
//            mind.getTValues().get(this).setSrcSolve(d);
//        }
//    }

    //    public Domain getSrcValue() {
//        if (mind.getTValues().containsKey(this)) {
//            return mind.getTValues().get(this).getSrcSolve();
//        } else {
//            return null;
//        }
//    }
//
    public Domain getDstSolve() {
        if (mind.getTValues().get(this) != null) {
            return mind.getTValues().get(this).getDstSolve();
        } else {
            return null;
        }
    }

//    public void setDstSolve(Domain d) {
//        if (mind.getTValues().containsKey(this)) {
//            mind.getTValues().put(this, new TValue());
//        } else {
//            mind.getTValues().get(this).setDstSolve(d);
//        }
//    }

//    public Domain getDstValue() {
//        if (mind.getTValues().containsKey(this)) {
//            return mind.getTValues().get(this).getDstSolve();
//        } else {
//            return null;
//        }
//    }
//
//    public boolean isDestFor(Domain d) {
//        if (mind.getTValues().containsKey(this)) {
//            return mind.getTValues().get(this).isDestFor(d);
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
                return String.format("%c%d", Enums.TVC, id);
            default:
                return name;
        }
    }

    @Override
    public String toString() {
        return getVarName() + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_VALUES) != 0 ? (isEmpty() ? "" : (":" + getValue().toString())) : "")
                + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_STATUS) != 0 && !isEmpty() && mind.getTValues().get(this).isBlocked() ? " (B)" : "");
    }

    public void writeCompiledData(DataOutputStream dos) throws IOException {
        dos.writeLong(id);
        dos.writeLong(right.getId());
//        dos.writeInt(owner);
        dos.writeLong(area == null ? -1 : area.getId());
        dos.writeLong(right == null ? -1 : right.getId());
        dos.writeUTF(name);
    }

    @Override
    public boolean equals(Object t) {
        return !(t == null || !(t instanceof TVariable)) && ((TVariable) t).id == id;
    }

    public boolean isInside(Term c) {

        return (c != null
                && c.isCVar()
                && c.getRight() == getRight()
                && (getArea() == null || getArea().getId() < c.getId()));
    }

    public boolean contains(Term value) {
        return mind.getTValues().find(this, value) != null;
    }

    public boolean isEmpty() {
        return mind.getTValues().isEmpty(this);
    }

    public TValue rewind() {
        return mind.getTValues().rewind(this);
    }

    public TValue next(TValue v) {
        return mind.getTValues().next(this, v);
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
//        mind.getTValues().get(this).mark();
//    }
//
//    public void release() {
//        if (mind.getTValues().containsKey(this)) {
//            mind.getTValues().get(this).release();
//        }
//    }
//
//    public void commit() {
//        if (mind.getTValues().containsKey(this)) {
//            mind.getTValues().get(this).commit();
//        }
//    }
//
//    public void clear() {
//        if (mind.getTValues().containsKey(this)) {
//            mind.getTValues().remove(this);
//        }
//    }
//
    public boolean isSubstituted() {
        return mind.getSubstituted().contains(this) /*|| mind.getUsed().contains(this)*/;
    }

//    public void setQuery() {
//        if (!mind.getQueryValues().containsKey(id)) {
//            mind.getQueryValues().put(id, new HashSet<>());
//        }
//        mind.getQueryValues().get(id).add(getValue().getId());
//    }

    public boolean isQuery() {
        return !isEmpty()
                && mind.getQueryValues().containsKey(id)
                && mind.getQueryValues().get(id).contains(getCurrent().getId());
    }

    public boolean isBlocked() {
        return mind.getTValues().get(this) != null && mind.getTValues().get(this).isBlocked();
    }
}
