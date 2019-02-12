package kanger.units;

import kanger.User;
import kanger.enums.Enums;
import kanger.exception.RuntimeErrorException;
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
    private List solves = new ArrayList();

    private User user = null;

    private transient long origId = -1;
    private transient List<List<Long>> treeIds = new ArrayList<>();

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
                branch.add(dis.readLong());
            }
            treeIds.add(branch);
        }
        count = dis.readInt();
        while (count-- > 0) {
            Cause c = (Cause) dis.readObject();
            causes.add(c);
        }
    }

    @Override
    public void writeExternal(ObjectOutput dos) throws IOException {
        dos.writeLong(id);
        dos.writeLong(orig.getId());
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

    public void linkExternal(User user) throws RuntimeErrorException {
        this.user = user;
        if (orig == null && origId != -1) {
            orig = user.getMind().getTerms().get(origId);
            if (orig == null) {
                orig = user.getMind().getTerms().load(origId);
                orig.linkExternal(user);
            }
        }
        if (tree.isEmpty() && !treeIds.isEmpty()) {
            for (List<Long> ids : treeIds) {
                List<Domain> branch = new ArrayList<>();
                for (long id : ids) {
                    Domain domain = user.getMind().getDomains().get(id);
                    if (domain == null) {
                        domain = user.getMind().getDomains().load(id);
                        domain.linkExternal(user);
                    }
                    branch.add(domain);
                }
                tree.add(branch);
            }
        }
        for (Cause c : causes) {
            c.linkExternal(user);
        }
    }

    public Domain getDomain() {
        return tree.get(0).get(0);
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

    @Override
    public String toString() {
        return orig.toString()
                + ((user.getMind().getDebugLevel() & Enums.DEBUG_OPTION_STATUS) != 0 && (isGenerated() || isQuery() || isStored())
                ? " " +
                (isGenerated() ? "G" : "") +
                (isStored() ? "B" : "") +
                (isQuery() ? "Q" : "")
                : "")
                ;
    }

    @Override
    public int getHash() {
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
    public boolean equalsTo(Right to) {
        if (stored) {
            return equalsTo(to.getDomain());
        } else {
            if (tree.size() != to.tree.size()) {
                return false;
            } else {
                int size = tree.size();
                for (List<Domain> rowMaster : tree) {
                    for (List<Domain> rowSlave : to.tree) {
                        //TODO: Сравнение двух правил для блокировки дублирования - задачка не тривиальная
                    }
                }
                return false;
            }
        }
    }

    public boolean equalsTo(Domain x) {
        Domain domain = getDomain();
        if (x.isAntc() == domain.isAntc()
                && x.getPredicate().getId() == domain.getPredicate().getId()
                && x.getPredicate().getRange() == domain.getPredicate().getRange()) {
            int i = 0;
            try {
                for (; i < domain.getPredicate().getRange(); ++i) {
                    if (!x.get(i).isEmpty()
                            && !domain.getArguments().get(i).isEmpty()
                            && x.get(i).getValue().getId() != domain.getArguments().get(i).getValue().getId()) {
                        break;
                    }

                    TValue a = x.get(i).isTSet() ? x.get(i).getT().getCurrent() : x.get(i).getV();
                    TValue b = domain.getArguments().get(i).isTSet() ? domain.getArguments().get(i).getT().getCurrent() : domain.getArguments().get(i).getV();
                    if (a != null && b != null && a.getTVar().getId() != b.getTVar().getId()) {
                        break;
                    }
                }
            } catch (RuntimeErrorException e) {
                e.printStackTrace(System.err);
            }
            return i == domain.getPredicate().getRange();
        } else {
            return false;
        }
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
