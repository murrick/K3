package kanger.primitives;

import kanger.Mind;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 * <p>
 * Элемент ветви дерева
 */
public class Tree {

    private List<Domain> sequence = new ArrayList<>();          // Домены
    private long id = -1;                                       // Идентификатор
    private Tree next = null;
    private Right right = null;

    private Set<Long> excludes = new HashSet<>();
    private boolean generated = false;

    private Mind mind = null;

    //    private boolean closed = false;
//    private boolean used = false;
    public Tree(Mind mind) {
        this.mind = mind;
    }

    public Tree(DataInputStream dis, Mind mind) throws IOException {
        this.mind = mind;
        id = dis.readLong();
        int count = dis.readInt();
        while (count-- > 0) {
            sequence.add(mind.getDomains().get(dis.readLong()));
        }
    }

    public void setGenerated(boolean generated) {
        this.generated = generated;
    }

    public boolean isGenerated() {
        return generated;
    }

    public Set<Long> getExcludes() {
        return excludes;
    }

    public List<Domain> getSequence() {
        return sequence;
    }

    public boolean isUsed() {
        return mind.getUsedTrees().contains(this);
    }

    public void setUsed() {
        mind.getUsedTrees().add(this);
    }

    public long getId() {
        return id;
    }

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
        Tree t = mind.getTrees().add();
        t.setRight(right);
        t.sequence.addAll(sequence);
        t.excludes.addAll(excludes);
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

    public void writeCompiledData(DataOutputStream dos) throws IOException {
        dos.writeLong(id);
        dos.writeInt(sequence.size());
        for (Domain d : sequence) {
            dos.writeLong(d.getId());
        }
    }

    public boolean isExcluded(Tree t) {
        return excludes.contains(t.getId());
    }

    public boolean equals(Object t) {
        return !(t == null || !(t instanceof Tree)) && ((Tree) t).id == id;
    }

    public List<TVariable> getTVariables(boolean full) {
        List<TVariable> list = new ArrayList<>();
        for (Domain d : sequence) {
            for (TVariable t : d.getTVariables(full)) {
                if (!list.contains(t)) {
                    list.add(t);
                }
            }
        }
        return list;
    }

//    public void recalculate() throws RuntimeErrorException {
//        for (Domain d : sequence) {
//            d.recalculate();
//        }
//    }

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
        return mind.getClosedTrees().contains(this);
    }

    public void setClosed(boolean closed) {
        if (closed) {
            mind.getClosedTrees().add(this);
        } else {
            mind.getClosedTrees().remove(this);
        }
    }

    public boolean isExcluded() {
        return mind.getExcludedTrees().contains(this);
    }

    public void setExcluded(boolean excluded) {
        if (excluded) {
            mind.getExcludedTrees().add(this);
        } else {
            mind.getExcludedTrees().remove(this);
        }
    }

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
            for (TVariable t : dom.getTVariables(true)) {
                for (TVariable x : d.getTVariables(true)) {
                    if (t.getId() == x.getId()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

}
