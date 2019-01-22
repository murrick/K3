package kanger.units;

import kanger.User;
import kanger.interfaces.Identifiable;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 * <p>
 * Список правил
 */
public class Right implements Externalizable, Identifiable<Right> {

    private long id = -1;                                   // ID Правила
    private Term orig = null;                               // Оригинальная строка
    private boolean query = false;                          // Вновь введенное правило
    private boolean generated = false;                      // Правило добавлено в процессе выводс
    private List<List<Domain>> tree = new ArrayList<>();    // Ссылка на дерево правила

    private User user = null;

    private transient long origId = -1;
    private transient List<List<Long>> treeIds = new ArrayList<>();

    public Right() {
    }

    public Right(User user) {
        this.user = user;
    }

    @Override
    public void readExternal(ObjectInput dis) throws IOException, ClassNotFoundException {
        id = dis.readLong();
        origId = dis.readLong();
        query = dis.readBoolean();
        generated = dis.readBoolean();
        treeIds.clear();
        int count = dis.readInt();
        while (count-- > 0) {
            List<Long> branch = new ArrayList<>();
            int len = dis.readInt();
            while(len-- > 0) {
                branch.add(dis.readLong());
            }
            treeIds.add(branch);
        }
    }

    @Override
    public void writeExternal(ObjectOutput dos) throws IOException {
        dos.writeLong(id);
        dos.writeLong(orig.getId());
        dos.writeBoolean(query);
        dos.writeBoolean(generated);
        dos.writeInt(tree.size());
        for (List<Domain> branch : tree) {
            dos.writeInt(branch.size());
            for(Domain domain : branch) {
                dos.writeLong(domain.getId());
            }
        }
    }

    public void linkExternal(User user) {
        if(tree.isEmpty()) {
            this.user = user;
            orig = user.getMind().getTerms().get(origId);
            tree.clear();
            for (List<Long> ids : treeIds) {
                List<Domain> branch = new ArrayList<>();
                for(long id : ids) {
                    Domain domain = user.getMind().getDomains().get(id);
                    branch.add(domain);
                }
                tree.add(branch);
            }
        }
    }

    public void setGenerated(boolean generated) {
        this.generated = generated;
    }

    public boolean isGenerated() {
        return generated;
    }

    public List<List<Domain>> getTree() {
        return tree;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
        this.id = id;
    }

    public Term getOrig() {
        return orig;
    }

    public void setOrig(Term orig) {
        this.orig = orig;
    }

    public boolean isQuery() {
        return query;
    }

    public void setQuery(boolean current) {
        this.query = current;
    }

    public int size() {
        return tree.size();
    }

    public List<Domain> cloneTree(List<Domain> branch) {
        List<Domain> list = new ArrayList<>();
        list.addAll(branch);
        tree.add(list);
        return list;
    }

    public boolean contains(Predicate predicate) {
        for(List<Domain> list : tree) {
            for(Domain d : list) {
                if(d.getPredicate().getId() == predicate.getId()) {
                    return true;
                }
            }
        }
        return false;
    }



    //    public Set<Right> getActualRights() {
//        Set<Right> set = new HashSet<>();
//        Set<Predicate> preds = new HashSet<>();
//        for (Domain d = user.getMind().getDomains().getRoot(); d != null; d = d.getNext()) {
//            if (d.getRight().getId() == id) {
//                preds.add(d.getPredicate());
//            }
//        }
//        for (Domain d = user.getMind().getDomains().getRoot(); d != null; d = d.getNext()) {
//            if (d.getRight().getId() != id && preds.contains(d.getPredicate())) {
//                set.add(d.getRight());
//            }
//        }
//        return set;
//    }
//
//    public Set<Tree> getActualTrees() {
//        Set<Tree> set = new HashSet<>();
//        Set<Predicate> preds = new HashSet<>();
//        for (Domain d = user.getMind().getDomains().getRoot(); d != null; d = d.getNext()) {
//            if (d.getRight().getId() == id) {
//                preds.add(d.getPredicate());
//            }
//        }
//        for (Tree t = user.getMind().getTrees().getRoot(); t != null; t = t.getNext()) {
//            for (Domain d : t.getSequence()) {
//                if (preds.contains(d.getPredicate())) {
//                    set.add(t);
//                    break;
//                }
//            }
//        }
//        return set;
//    }
//
    @Override
    public String toString() {
        return orig.toString();
    }

    @Override
    public int getHash() {
        StringBuffer buffer = new StringBuffer();
        buffer.append(query);
        buffer.append(generated);
        for(List<Domain> list : tree) {
            for(Domain d : list) {
                buffer.append(d.getId());
            }
        }
        return buffer.toString().hashCode();
    }

    @Override
    public boolean equalsTo(Right to) {
        return false;
    }

    @Override
    public int hashCode() {
        return ("" + id).hashCode();
    }

    @Override
    public boolean equals(Object t) {
        return !(t == null || !(t instanceof Right)) && ((Right) t).id == id;
    }

//    public List<TVariable> getTVariables(boolean full) {
//        List<TVariable> list = new ArrayList<>();
//        for (Tree x : tree) {
//            for (TVariable t : x.getTVariables(full)) {
//                if (!list.contains(t)) {
//                    list.add(t);
//                }
//            }
//        }
//        return list;
//    }

//    public boolean equalsBase(Right r) { 
//        if(r == null || !(r instanceof Right)) { 
//            return false;
//        } else { 
//            if(r.getTree().size() != tree.size()) { 
//                return false;
//            } else { 
//                for(Tree t : r.getTree()) {
//                    boolean found = false;
//                    for(Tree x : tree) {
//                        
//                    }
//                }
//            }
//        }
//    }


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
