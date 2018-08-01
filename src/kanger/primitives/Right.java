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
 * Список правил
 */
public class Right {

    private List<Tree> tree = new ArrayList<>();      // Ссылка на дерево правила
    private long id = -1;                       // ID Правила
    private Right next = null;                  // Следующее правило
    private String orig = "";                   // Оригинальная строка
    private boolean query = false;            // Вновь введенное правило

    private Mind mind = null;

    public Right(Mind mind) {
        this.mind = mind;
    }

    public Right(DataInputStream dis, Mind mind) throws IOException {
        id = dis.readLong();
        orig = dis.readUTF();
        int count = dis.readInt();
        while (count-- > 0) {
            tree.add(new Tree(dis, mind));
        }
        this.mind = mind;
    }

    public List<Tree> getTree() {
        return tree;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Right getNext() {
        return next;
    }

    public void setNext(Right next) {
        this.next = next;
    }

    public String getOrig() {
        return orig;
    }

    public void setOrig(String orig) {
        this.orig = orig;
    }

    public boolean isQuery() {
        return query;
    }

    public void setQuery(boolean current) {
        this.query = current;
    }

    public void writeCompiledData(DataOutputStream dos) throws IOException {
        dos.writeLong(id);
        dos.writeUTF(orig);
        dos.writeInt(tree.size());
        for (Tree r : tree) {
            r.writeCompiledData(dos);

        }
    }

    public int size() {
        return tree.size();
    }

    public Tree cloneTree(Tree t, boolean exclude) {
        Tree x = t.clone();
        tree.add(x);
        if(exclude){
            t.getExcludes().add(x.getId());
            x.getExcludes().add(t.getId());
        }
        return x;
    }

    public boolean isActive() {
        return mind.getActiveRights().contains(id);
    }

    public void setActive(boolean active) {
        if (active) {
            mind.getActiveRights().add(id);
        } else {
            mind.getActiveRights().remove(id);
        }
    }

    public Set<Right> getActualRights() {
        Set<Right> set = new HashSet<>();
        Set<Predicate> preds = new HashSet<>();
        for (Domain d = mind.getDomains().getRoot(); d != null; d = d.getNext()) {
            if (d.getRight().getId() == id) {
                preds.add(d.getPredicate());
            }
        }
        for (Domain d = mind.getDomains().getRoot(); d != null; d = d.getNext()) {
            if (d.getRight().getId() != id && preds.contains(d.getPredicate())) {
                set.add(d.getRight());
            }
        }
        return set;
    }

    public Set<Tree> getActualTrees() {
        Set<Tree> set = new HashSet<>();
        Set<Predicate> preds = new HashSet<>();
        for (Domain d = mind.getDomains().getRoot(); d != null; d = d.getNext()) {
            if (d.getRight().getId() == id) {
                preds.add(d.getPredicate());
            }
        }
        for (Tree t = mind.getTrees().getRoot(); t != null; t = t.getNext()) {
            for (Domain d : t.getSequence()) {
                if (preds.contains(d.getPredicate())) {
                    set.add(t);
                    break;
                }
            }
        }
        return set;
    }

    @Override
    public String toString() {
        return orig;
    }

    @Override
    public boolean equals(Object t) {
        return !(t == null || !(t instanceof Right)) && ((Right) t).id == id;
    }

    public List<TVariable> getTVariables(boolean full) {
        List<TVariable> list = new ArrayList<>();
        for (Tree x : tree) {
            for (TVariable t : x.getTVariables(full)) {
                if (!list.contains(t)) {
                    list.add(t);
                }
            }
        }
        return list;
    }

//    @Override
//    public boolean equals(Object o) {
//        if (o == null || !(o instanceof Right)) {
//            return false;
//        } else {
//            Right r = (Right) o;
//            if (r.size() != size()) {
//                return false;
//            }
//            for () {
//				if (!h1.getD().equals(h2.getD())) {
//					return false;
//				}
//
//            }
//            return true;
//        }
//    }
}
