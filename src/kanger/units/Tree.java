package kanger.units;

import kanger.User;
import kanger.interfaces.Identifiable;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 * <p>
 * Элемент ветви дерева
 */
public class Tree implements Comparable<Tree>, Externalizable, Identifiable {

    private long id = -1;                                       // Идентификатор
    private List<Domain> sequence = new ArrayList<>();          // Домены
    private Right right = null;
    private boolean generated = false;

    private Tree next = null;
    private User user = null;

    //    private boolean closed = false;
//    private boolean used = false;
    public Tree(User user) {
        this.user = user;
    }

    @Override
    public void readExternal(ObjectInput dis) throws IOException, ClassNotFoundException {
        id = dis.readLong();
        int count = dis.readInt();
        while (count-- > 0) {
            sequence.add((Domain) dis.readObject());
        }
        right = (Right) dis.readObject();
        generated = dis.readBoolean();
    }

    @Override
    public void writeExternal(ObjectOutput dos) throws IOException {
        dos.writeLong(id);
        dos.writeInt(sequence.size());
        for (Domain d : sequence) {
            dos.writeObject(d);
        }
        dos.writeObject(right);
        dos.writeBoolean(generated);
    }


    public void setGenerated() {
        this.generated = true;
    }

    public boolean isGenerated() {
        return generated;
    }

    public List<Domain> getSequence() {
        return sequence;
    }


    public boolean isReady() {
        if (sequence.size() > 1) {
            for (Domain d : sequence) {
                if (/*!d.isExcluded() && !d.isCalculated() && !d.isStored() &&*/ !d.isQuery()) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean isUsed() {
//        for(Domain d : sequence) {
//            if(d.isExcluded()) {
//                return true;
//            }
//        }
//        return false;
        return user.getMind().getUsedTrees().contains(this);
    }

    public void setUsed() {
        user.getMind().getUsedTrees().add(this);
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
        this.id = id;
    }

    public Right getRight() {
        return right;
    }

    public void setRight(Right right) {
        this.right = right;
    }

    public Tree getNext() {
        return next;
    }

    public void setNext(Tree next) {
        this.next = next;
    }


    @Override
    public Tree clone() {
        Tree t = user.getMind().getTrees().add();
        t.setRight(right);
        t.sequence.addAll(sequence);
        return t;
    }

    @Override
    public String toString() {
        String s = "";
        for (Domain d : sequence) {
            if (!s.isEmpty()) {
                s += "\n";
            }
            s += d.toString();
        }
        return s;
    }

    @Override
    public int hashCode() {
        StringBuffer buffer = new StringBuffer();
        buffer.append(right.getId());
        buffer.append(generated);
        for(Domain d : sequence) {
            buffer.append(d.getId());
        }
        return buffer.toString().hashCode();
    }    

    @Override
    public boolean equals(Object t) {
        return !(t == null || !(t instanceof Tree)) && ((Tree) t).id == id;
    }

    public Set<TVariable> getTVariables(boolean full) {
        Set<TVariable> list = new HashSet<>();
        for (Domain d : sequence) {
            for (TVariable t : d.getArguments().getTVariables(full)) {
                    list.add(t);
            }
        }
        return list;
    }

    public Set<TVariable> getRelatedTVariables(boolean full) {
        Set<TVariable> list = new HashSet<>();
        for (Domain d : sequence) {
            for (TVariable t : d.getRelatedTVariables(full)) {
                    list.add(t);
            }
        }
        return list;
    }

    public Set<Tree> getRelatedTrees() {
        Set<Tree> list = new HashSet<>();
        for (Domain d : sequence) {
            list.addAll(d.getPredicate().getLinkedTrees());
        }
        return list;
    }


    public List<Domain> getSystem() {
        List<Domain> list = new ArrayList<>();
        for (Domain d : sequence) {
            if (d.isSystem()) {
                list.add(d);
            }
        }
        return list;
    }

    public boolean isClosed() {
        return user.getMind().getClosedTrees().contains(this);
    }

    public void setClosed() {
        user.getMind().getClosedTrees().add(this);
    }

//    public boolean isExcluded() {
//        for(Domain d : sequence) {
//            if(d.isExcluded()) {
//                return true;
//            }
//        }
//        return false;
//        return user.getMind().getExcludedTrees().contains(this);
//    }

//    public void setExcluded(boolean excluded) {
//        if (excluded) {
//            user.getMind().getExcludedTrees().add(this);
//        } else {
//            user.getMind().getExcludedTrees().remove(this);
//        }
//    }

    public List<Function> getFunctions() {
        List<Function> list = new ArrayList<>();
        for (Domain d : sequence) {
            list.addAll(d.getFunctions());
        }
        return list;
    }

    public boolean contains(Domain dom) {
        for (Domain d : sequence) {
            if (d.getPredicate().getId() == dom.getPredicate().getId()) {
                return true;
            }
            for (TVariable t : dom.getArguments().getTVariables(true)) {
                for (TVariable x : d.getArguments().getTVariables(true)) {
                    if (t.getId() == x.getId()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public Set<Domain> getExcluded() {
        Set<Domain> set = new HashSet<>();
        for (Domain d : sequence) {
            if (d.isExcluded()) {
                set.add(d);
            }
        }
        return set;
    }

    @Override
    public int compareTo(Tree o) {
        return (int) (id - o.id);
    }

//    public boolean equalsBase(Tree t) {
//        if(t == null || !(t instanceof Tree) || t.getSequence().size() != sequence.size()) {
//            return false;
//        } else {
//            for(Domain d : t.getSequence()) {
//                boolean found = false;
//                for(Domain x : sequence) {
//                    if(x.isAntc() == d.isAntc() && x.equalsBase(d)) {
//                        found = true;
//                        break;
//                    }
//                } 
//                if(!found) {
//                    return false;
//                }
//            } 
//            return true;
//        }
//    }
}
