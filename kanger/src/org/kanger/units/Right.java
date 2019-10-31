package org.kanger.units;

import org.kanger.enums.Enums;
import org.kanger.enums.UnitType;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IUnit;
import org.kanger.interfaces.IUser;
import org.kanger.primitives.Cause;
import org.kanger.storage.ByteBuffer;

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
public class Right implements Externalizable, IUnit<Right> {

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

    private int varIndex = 0;

    private transient long origId = -1;
    private transient List<List<Long>> treeIds = new ArrayList<>();
    private transient IUser user = null;

    private transient boolean deleted = false;

    public Right() {
    }

    public Right(IUser user) {
        this.user = user;
        List<Domain> t = new ArrayList<>();
        tree.add(t);
    }

    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putLong(id)
                .putByte(deleted ? 1 : 0)
                .putLong(origId)
                .putInt(varIndex)
                .putByte(query ? 1 : 0)
                .putByte(generated ? 1 : 0)
                .putByte(stored ? 1 : 0)
                .putInt(tree.size());
        for (List<Domain> branch : tree) {
            packet.putInt(branch.size());
            for (Domain domain : branch) {
                packet.putLong(domain.getId());
            }
        }
        packet.putInt(causes.size());
        for (Cause c : causes) {
            packet.append(c.pack());
        }
        return packet.createMarked();
    }

    public Right apply(ByteBuffer packet) throws OutOfBufferException {
        id = packet.getLong();
        deleted = packet.getByte() != 0;
        origId = packet.getLong();
        varIndex = packet.getInt();
        query = packet.getByte() != 0;
        generated = packet.getByte() != 0;
        stored = packet.getByte() != 0;
        tree.clear();
        int width = packet.getInt();
        while (width-- > 0) {
            List<Long> branch = new ArrayList<>();
            int height = packet.getInt();
            while (height-- > 0) {
                long id = packet.getLong();
                branch.add(id);
            }
            treeIds.add(branch);
        }
        int count = packet.getInt();
        while (count-- > 0) {
            try {
                packet.mark();
                Cause c = new Cause().apply(packet);
                c.setUser(user);
                causes.add(c);
            } finally {
                packet.release();
            }
        }
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


    private void checkTreeIsLoaded() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
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

    public Domain getDomain() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        return getTree().get(0).get(0);
    }

    public Set<Cause> getCauses() {
        return causes;
    }

    public List<TValue> getSolves() {
        return solves;
    }

    public boolean isGenerated() {
        return generated;
    }

    public void setGenerated(boolean b) {
        this.generated = b;
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

    public Set<Right> getNatives() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        Set<Right> list = new HashSet<>();
        for (List<Domain> t : getTree()) {
            for (Domain d : t) {
                for (Right r : user.getMind().getRights()) {
                    if (r != null) {
                        if (!r.isDeleted() && r.getPredicates().contains(d.getPredicateId())) {
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

    public List<List<Domain>> getTree() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
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

    public Term getOrig() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
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

    public int size() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        return getTree().size();
    }

    public List<Domain> cloneTree(List<Domain> branch) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
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
                    (isStored() && getDomain().isUsed() ? "U" : "") +
                    (isQuery() ? "Q" : "")
                    : "")
                    ;
        } catch (IOException | ClassNotFoundException | OutOfBufferException | RuntimeErrorException e) {
            e.printStackTrace(System.err);
            return "";
        }
    }

    //TODO: 5  !~b(z); ?b(z) -> c(z);  => TRUE - Не верно

    @Override
    public int getHash() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        //TODO: 4
        if (stored || (tree.size() == 1 && tree.get(0).size() == 1)) {
            return getDomain().getHashBase();
        } else {
            int hash = 0;
            for (List<Domain> list : tree) {
                int sub = 0;
                for (Domain d : list) {
                    sub += d.getHashStruct();
                }
                hash += sub;
            }
            return hash;
        }
    }

    private boolean branchEquals(List<Domain> a, List<Domain> b) throws ClassNotFoundException, RuntimeErrorException, OutOfBufferException, IOException {
        List<Domain> tmp = new ArrayList<>();
        tmp.addAll(b);
        for (Domain d : a) {
            for (Domain x : tmp) {
                if (d.equalsToStruct(x)) {
                    tmp.remove(x);
                    break;
                }
            }
        }
        return tmp.isEmpty();
    }

    @Override
    public boolean equalsTo(Right to) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
//        if (stored || (tree.size() == 1 && tree.get(0).size() == 1)) {
//            return equalsTo(to.getDomain());
//        } else
        if (getTree().size() == to.getTree().size()) {
            List<List<Domain>> tmp = new ArrayList<>();
            tmp.addAll(to.getTree());
            for (List<Domain> a : tree) {
                for (List<Domain> b : tmp) {
                    if (branchEquals(a, b)) {
                        tmp.remove(b);
                        break;
                    }
                }
            }
            return tmp.isEmpty();
        } else {
            return false;
        }
    }

    @Override
    public IUser getUser() {
        return user;
    }

    @Override
    public Right setUser(IUser user) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        this.user = user;
        for (Cause c : getCauses()) {
            c.setUser(user);
        }
        for (List<Domain> list : getTree()) {
            for (Domain d : list) {
                d.setUser(user);
            }
        }
        return this;
    }

    public boolean equalsTo(Domain x) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        Domain domain = getDomain();
        if (x.isAntc() == domain.isAntc()
                && x.getPredicateId() == domain.getPredicateId()
                && x.getRange() == domain.getRange()) {
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

    public int getVarIndex() {
        return varIndex;
    }

    public void setVarIndex(int varIndex) {
        this.varIndex = varIndex;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted() {
        this.deleted = true;
    }

    @Override
    public UnitType getUnitType() {
        return UnitType.RIGHT;
    }

}
