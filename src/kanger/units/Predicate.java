package kanger.units;

import kanger.User;
import kanger.interfaces.Identifiable;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 */
public class Predicate implements Externalizable, Identifiable {

    private long id = -1;                   // Идентификатор
    private Term name = null;               // Имя предиката
    private int range = 0;                  // К-во параметров

    private Predicate next = null;          // Следующий предикат

    private User user = null;

    public Predicate(User user) {
        this.user = user;
    }

    @Override
    public void readExternal(ObjectInput dis) throws IOException, ClassNotFoundException {
        id = dis.readLong();
        name = (Term) dis.readObject();
        range = dis.readInt();
    }

    @Override
    public void writeExternal(ObjectOutput dos) throws IOException {
        dos.writeLong(id);
        dos.writeObject(name);
        dos.writeInt(range);
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

    public Predicate getNext() {
        return next;
    }

    public void setNext(Predicate next) {
        this.next = next;
    }

    public Set<Domain> getSolves() {
        Set<Domain> set = new HashSet<>();
//        for(long id : mind.getProducedDomains().keySet()) {
//            Domain d = mind.getDomains().get(id);
//            if(d.getPredicate().getId() == getId()) {
//                for(List<Long> args : mind.getProducedDomains().get(id)) {
//                    Solution s = new Solution(mind, d.isAntc(), d.getPredicate(), d.getArguments());
//                    set.add(s);
//                }
//            }
//        }

        for (Record d = user.getMind().getDatabase().getRoot(); d != null; d = d.getNext()) {
            if (getId() == d.getDomain().getPredicate().getId()) {
                set.add(d.getDomain());
            }
        }
        return set;
    }

    public Set<Domain> getRelates() {
        Set<Domain> set = new HashSet<>();
        for (Domain d = user.getMind().getDomains().getRoot(); d != null; d = d.getNext()) {
            if (getId() == d.getPredicate().getId()) {
                set.add(d);
            }
        }
        return set;
    }

    public Set<Tree> getLinkedTrees() {
        Set<Tree> set = new HashSet<>();
        for (Tree t = user.getMind().getTrees().getRoot(); t != null; t = t.getNext()) {
            for (Domain d : t.getSequence()) {
                if (getId() == d.getPredicate().getId()) {
                    set.add(t);
                    break;
                }
            }
        }
        return set;
    }

    public Set<Right> getLinkedRights() {
        Set<Right> set = new HashSet<>();
        for(Domain d : getRelates()) {
            set.add(d.getRight());
        }
        return set;
    }

    public Set<TVariable> getTVariables(boolean full) {
        Set<TVariable> set = new HashSet<>();
        for (Domain d = user.getMind().getDomains().getRoot(); d != null; d = d.getNext()) {
            if (getId() == d.getPredicate().getId()) {
                set.addAll(d.getArguments().getTVariables(full));
                break;
            }
        }
        return set;
    }

    public Domain containsSolve(Domain d) {
        for (Domain x : getSolves()) {
            if (x.isStored() && d.equalsBase(x)) {
                return x;
            }
        }
        return null;
    }

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
}
