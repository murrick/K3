package org.kanger.units;

import org.kanger.User;
import org.kanger.interfaces.IUnit;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 */
public class Predicate implements Externalizable, IUnit<Predicate> {

    private static final long serialVersionUID = 196402070004L;

    private long id = -1;                   // Идентификатор
    private Term name = null;               // Имя предиката
    private int range = 0;                  // К-во параметров

    private User user = null;

    private transient long nameId = -1;

    private transient boolean deleted = false;

    public Predicate() {
    }

    public Predicate(Term name, int range) {
        this.name = name;
        this.range = range;
        this.nameId = name.getId();
    }

    public Predicate(User user) {
        this.user = user;
    }

    @Override
    public void readExternal(ObjectInput dis) throws IOException, ClassNotFoundException {
        id = dis.readLong();
        deleted = dis.readBoolean();
        nameId = dis.readLong();
        range = dis.readInt();
    }

    @Override
    public void writeExternal(ObjectOutput dos) throws IOException {
        dos.writeLong(id);
        dos.writeBoolean(deleted);
        dos.writeLong(nameId);
        dos.writeInt(range);
    }

//    @Override
//    public void linkExternal(User user) throws IOException, ClassNotFoundException {
//        this.user = user;
//        name = user.getMind().getTerms().get(nameId);
//    }

    public Term getName() throws IOException, ClassNotFoundException {
        if (name == null) {
            name = user.getMind().getTerms().load(nameId);
        }
        return name;
    }

    public void setName(Term name) {
        this.name = name;
        this.nameId = name.getId();
    }

    public int getRange() {
        return range;
    }

    public void setRange(int range) {
        this.range = range;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
        this.id = id;
    }

    public Set<Domain> getSolves() throws Exception {
        Set<Domain> set = new HashSet<>();
        for (long id : user.getMind().getRights().getDatabase(-1)) {
            Right r = user.getMind().getRights().get(id);
            if (!r.isDeleted() && getId() == r.getDomain().getPredicateId()) {
                set.add(r.getDomain());
            }
        }
        return set;
    }

//    public Set<Domain> getRelates() {
//        Set<Domain> set = new HashSet<>();
//        for (Domain d : user.getMind().getDomains()) {
//            if (getId() == d.getPredicate().getId()) {
//                set.add(d);
//            }
//        }
//        return set;
//    }

//    public Set<Tree> getLinkedTrees() {
//        Set<Tree> set = new HashSet<>();
//        for (Tree t : user.getMind().getTrees()) {
//            for (Domain d : t.getSequence()) {
//                if (getId() == d.getPredicate().getId()) {
//                    set.add(t);
//                    break;
//                }
//            }
//        }
//        return set;
//    }
//
//    public Set<Right> getLinkedRights() {
//        Set<Right> set = new HashSet<>();
//        for (Domain d : getRelates()) {
//            set.add(d.getRight());
//        }
//        return set;
//    }
//
//    public Set<TVariable> getTVariables(boolean full) {
//        Set<TVariable> set = new HashSet<>();
//        for (Domain d : user.getMind().getDomains()) {
//            if (getId() == d.getPredicate().getId()) {
//                set.addAll(d.getArguments().getTVariables(full));
//                break;
//            }
//        }
//        return set;
//    }
//
//    public Domain containsSolve(Domain d) {
//        for (Domain x : getSolves()) {
//            if (x.isStored() && d.equalsBase(x)) {
//                return x;
//            }
//        }
//        return null;
//    }
//
//    public boolean checkSolves() {
//        for(Domain d = mind.getDomains().getRoot(); d != null; d = d.getNext()) {
//            if(getId() == d.getPredicate().getId()) {
//                for(Domain q = mind.getDomains().getRoot(); q != null; q = d.getNext()) {
//                    if(getId() == q.getPredicate().getId()) {
//                        for(int i=0; i<d.getPredicate().getRange(); ++i) {
//                            if(!d.getArguments().get(i).isEmpty()
//                                && !q.getArguments().get(i).isEmpty()
//                            && d.getArguments().get(i).getValue().getId() == q.getArguments().get(i).getValue().getId()) {
//                                return true;
//                            }
//                        }
//                    }
//                }
//            }
//        }
//        return false;
//    }

//    public Set<Right> getRights() {
//        Set<Right> set = new HashSet<>();
//        for(Domain d = mind.getDomains().getRoot(); d != null; d = d.getNext()) {
//            if(d.getPredicate().getId() == id) {
//                set.add(d.getRight());
//            }
//        }
//        return set;
//    }

    @Override
    public String toString() {
        try {
            return getName() + "(" + range + ")";
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace(System.err);
            return "";
        }
    }

//    @Override
//    public boolean equals(Object t) {
//        return !(t == null || !(t instanceof Predicate)) && ((Predicate) t).id == id;
//    }

//    public List<TVariable> getTVariables(boolean full) {
//        List<TVariable> list = new ArrayList<>();
//        for(Domain d = mind.getDomains().getRoot(); d != null; d = d.getNext()) {
//            if(d.getPredicate().id == id) {
//                for(TVariable t : d.getTVariables(full)) {
//                    if(!list.contains(t)) {
//                        list.add(t);
//                    }
//                }
//            }
//        }
//        return list;
//    }

    @Override
    public int getHash() {
        int hash = 3;
        hash = 47 * hash + (int) (nameId ^ (nameId >>> 32));
        hash = 47 * hash + range;
        return hash;
    }

    @Override
    public boolean equalsTo(Predicate to) {
        return to.getNameId() == nameId && to.getRange() == range;
    }

    @Override
    public User getUser() {
        return user;
    }

    @Override
    public void setUser(User user) {
        this.user = user;
    }

    @Override
    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public void setDeleted() {
        deleted = true;
    }

    @Override
    public int hashCode() {
        return ("" + id).hashCode();
    }

    public long getNameId() {
        return nameId;
    }
}
