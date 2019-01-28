package kanger.units;

import kanger.User;
import kanger.exception.RuntimeErrorException;
import kanger.interfaces.Identifiable;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 */
public class Predicate implements Externalizable, Identifiable<Predicate> {

    private static final long serialVersionUID = 196402070004L;

    private long id = -1;                   // Идентификатор
    private Term name = null;               // Имя предиката
    private int range = 0;                  // К-во параметров

    private User user = null;

    private transient long nameId = -1;

    public Predicate() {
    }

    public Predicate(Term name, int range) {
        this.name = name;
        this.range = range;
    }

    public Predicate(User user) {
        this.user = user;
    }

    @Override
    public void readExternal(ObjectInput dis) throws IOException {
        id = dis.readLong();
        nameId = dis.readLong();
        range = dis.readInt();
    }

    @Override
    public void writeExternal(ObjectOutput dos) throws IOException {
        dos.writeLong(id);
        dos.writeLong(name.getId());
        dos.writeInt(range);
    }

    public void linkExternal(User user) throws RuntimeErrorException {
        this.user = user;
        if (name == null && nameId != -1) {
            name = user.getMind().getTerms().get(nameId);
            if (name == null) {
                name = user.getMind().getTerms().load(nameId);
                name.linkExternal(user);
            }
        }
    }

    public Term getName() {
        return name;
    }

    public void setName(Term name) {
        this.name = name;
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

    public Set<Domain> getSolves() throws RuntimeErrorException {
        Set<Domain> set = new HashSet<>();
        Iterator<Right> iterator = user.getMind().getRights().baseIterator(null);
        while (iterator.hasNext()) {
            Right r = iterator.next();
            if (user.getMind().getRights().getPredicatesLink().get(this).contains(r)) {
                r.linkExternal(user);
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
        return name + "(" + range + ")";
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
        StringBuffer buffer = new StringBuffer();
        buffer.append(name == null ? nameId : name.getId());
        buffer.append(range);
        return buffer.toString().hashCode();
    }

    @Override
    public boolean equalsTo(Predicate to) {
        if (nameId != -1 && to.getName() != null) {
            return to.getName().getId() == nameId && to.range == range;
        } else {
            return to.getName().getId() == getName().getId() && getRange() == to.getRange();
        }
    }

    @Override
    public int hashCode() {
        return ("" + id).hashCode();
    }
}
