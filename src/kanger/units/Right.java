package kanger.units;

import kanger.User;
import kanger.enums.Enums;
import kanger.interfaces.Identifiable;
import kanger.primitives.Cause;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 * <p>
 * Список правил
 */
public class Right implements Externalizable, Identifiable<Right> {

    private static final long serialVersionUID = 196402070007L;

    private long id = -1;                                   // ID Правила
    private Term orig = null;                               // Оригинальная строка
    private boolean query = false;                          // Вновь введенное правило
    private boolean generated = false;                      // Правило добавлено в процессе выводс
    private boolean stored = false;                         // Правило добавлено в процессе выводс
    private List<List<Domain>> tree = new ArrayList<>();    // Ссылка на дерево правила
    private Set<Cause> causes = new HashSet<>();

    private List<TValue> solves = new ArrayList();
    private Set<Long> predicates = new HashSet<>();

    private transient long origId = -1;
    private transient List<List<Long>> treeIds = new ArrayList<>();
    private transient User user = null;

    public Right() {
    }

    public Right(User user) {
        this.user = user;
        List<Domain> t = new ArrayList<>();
        tree.add(t);
    }

    @Override
    public void readExternal(ObjectInput dis) throws IOException, ClassNotFoundException {
        id = dis.readLong();
        origId = dis.readLong();
        query = dis.readBoolean();
        generated = dis.readBoolean();
        stored = dis.readBoolean();
        treeIds.clear();
        int count = dis.readInt();
        while (count-- > 0) {
            List<Long> branch = new ArrayList<>();
            int len = dis.readInt();
            while (len-- > 0) {
                long id = dis.readLong();
                branch.add(id);
            }
            treeIds.add(branch);
        }
        count = dis.readInt();
        while (count-- > 0) {
            Cause c = (Cause) dis.readObject();
            c.setUser(user);
            causes.add(c);
        }
    }

    @Override
    public void writeExternal(ObjectOutput dos) throws IOException {
        dos.writeLong(id);
        dos.writeLong(origId);
        dos.writeBoolean(query);
        dos.writeBoolean(generated);
        dos.writeBoolean(stored);
        dos.writeInt(tree.size());
        for (List<Domain> branch : tree) {
            dos.writeInt(branch.size());
            for (Domain domain : branch) {
                dos.writeLong(domain.getId());
            }
        }
        dos.writeInt(causes.size());
        for (Cause c : causes) {
            dos.writeObject(c);
        }
    }

    private void checkTreeIsLoaded() throws IOException, ClassNotFoundException {
        if (tree.isEmpty() && !treeIds.isEmpty()) {
            for (List<Long> ids : treeIds) {
                List<Domain> branch = new ArrayList<>();
                for (long id : ids) {
                    Domain domain = user.getMind().getDomains().load(id);
                    branch.add(domain);
                    predicates.add(domain.getPredicateId());
                }
                tree.add(branch);
            }
        }
    }


//    @Override
//    public void linkExternal(User user) throws IOException, ClassNotFoundException {
//        this.user = user;
//        orig = user.getMind().getTerms().load(origId);
//        for (List<Long> ids : treeIds) {
//            List<Domain> branch = new ArrayList<>();
//            for (long id : ids) {
//                Domain domain = user.getMind().getDomains().load(id);
//                branch.add(domain);
//                predicates.add(domain.getPredicate());
//            }
//            tree.add(branch);
//        }
//        for (Cause c : causes) {
////            c.linkExternal(user);
//        }
//    }

    public Domain getDomain() throws IOException, ClassNotFoundException {
        return getTree().get(0).get(0);
    }

    public Set<Cause> getCauses() {
        return causes;
    }

    public List<TValue> getSolves() {
        return solves;
    }

    public void setGenerated() {
        this.generated = true;
    }

    public boolean isGenerated() {
        return generated;
    }

    public boolean isStored() {
        return stored;
    }

    public void setStored() {
        this.stored = true;
    }

    public boolean isUsed() {
        return user.getMind().getUsedRights().containsKey(0L) && user.getMind().getUsedRights().get(0L).contains(this);
    }

    public void setUsed() {
        if (!user.getMind().getUsedRights().containsKey(0L)) {
            user.getMind().getUsedRights().put(0L, new HashSet<>());
        }
        user.getMind().getUsedRights().get(0L).add(this);
    }

    public Set<Right> getNatives() throws IOException, ClassNotFoundException {
        Set<Right> list = new HashSet<>();
        for (List<Domain> t : getTree()) {
            for (Domain d : t) {
                for (Right r : user.getMind().getRights()) {
                    if (r != null) {
                        if (r.getPredicates().contains(d.getPredicateId())) {
                            list.add(r);
                        }
                    } else {
                        break;
                    }
                }
            }
        }
        return list;
    }

    public List<List<Domain>> getTree() throws IOException, ClassNotFoundException {
        checkTreeIsLoaded();
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

    public Term getOrig() throws IOException, ClassNotFoundException {
        if (orig == null && origId != -1) {
            orig = user.getMind().getTerms().load(origId);
        }
        return orig;
    }

    public void setOrig(Term orig) {
        this.orig = orig;
        this.origId = orig.getId();
    }

    public boolean isQuery() {
        return query;
    }

    public void setQuery(boolean current) {
        this.query = current;
    }

    public int size() throws IOException, ClassNotFoundException {
        return getTree().size();
    }

    public List<Domain> cloneTree(List<Domain> branch) throws IOException, ClassNotFoundException {
        List<Domain> list = new ArrayList<>();
        list.addAll(branch);
        getTree().add(list);
        return list;
    }

    @Override
    public String toString() {
        try {
            return getOrig().toString()
                    + ((user.getMind().getDebugLevel() & Enums.DEBUG_OPTION_STATUS) != 0 && (isGenerated() || isQuery() || isStored())
                    ? " " +
                    (isGenerated() ? "G" : "") +
                    (isStored() ? "B" : "") +
                    (isQuery() ? "Q" : "")
                    : "")
                    ;
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace(System.err);
            return "";
        }
    }

    @Override
    public int getHash() throws IOException, ClassNotFoundException {
//        StringBuffer buffer = new StringBuffer();
//        for (List<Domain> list : tree) {
//            for (Domain d : list) {
//                buffer.append(d.getHashBase());
//            }
//        }
//        return buffer.toString().hashCode();
        return getDomain().getHashBase();
    }

    @Override
    public boolean equalsTo(Right to) throws IOException, ClassNotFoundException {
        if (stored) {
            return equalsTo(to.getDomain());
        } else {
            if (getTree().size() != to.getTree().size()) {
                return false;
            } else {
//                int size = tree.size();
//                for (List<Domain> rowMaster : tree) {
//                    for (List<Domain> rowSlave : to.tree) {
//                        //TODO: Сравнение двух правил для блокировки дублирования - задачка не тривиальная
//                    }
//                }
                return false;
            }
        }
    }

    @Override
    public User getUser() {
        return user;
    }

    @Override
    public void setUser(User user) throws IOException, ClassNotFoundException {
        this.user = user;
        for (Cause c : getCauses()) {
            c.setUser(user);
        }
        for (List<Domain> list : getTree()) {
            for (Domain d : list) {
                d.setUser(user);
            }
        }
    }

    public boolean equalsTo(Domain x) throws IOException, ClassNotFoundException {
        Domain domain = getDomain();
        if (x.isAntc() == domain.isAntc()
                && x.getPredicateId() == domain.getPredicateId()
                && x.getRange() == domain.getRange()) {
            try {
                int i = 0;
                for (; i < domain.getRange(); ++i) {
                    //TODO: Костыль!
                    x.get(i).setUser(user);
                    if (!x.get(i).isEmpty()
                            && !domain.getArguments().get(i).isEmpty()
                            && x.get(i).getValue().getId() != domain.getArguments().get(i).getValue().getId()) {
                        break;
                    }

                    TValue a = x.get(i).isTSet() ? x.get(i).getT().getCurrent() : x.get(i).getV();
                    TValue b = domain.getArguments().get(i).isTSet() ? domain.getArguments().get(i).getT().getCurrent() : domain.getArguments().get(i).getV();
                    if (a != null && b != null && a.getTVarId() != b.getTVarId()) {
                        break;
                    }
                }
                return i == domain.getRange();
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace(System.err);
                return false;
            }
        } else {
            return false;
        }
    }

    public Set<Long> getPredicates() {
        return predicates;
    }

    @Override
    public int hashCode() {
        return ("" + id).hashCode();
    }

    @Override
    public boolean equals(Object t) {
        return !(t == null || !(t instanceof Right)) && ((Right) t).id == id;
    }

}
