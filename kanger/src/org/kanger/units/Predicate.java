package org.kanger.units;

import org.kanger.Mind;
import org.kanger.enums.UnitType;
import org.kanger.exception.OutOfBufferException;
import org.kanger.interfaces.IUnit;
import org.kanger.storage.ByteBuffer;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 */
public class Predicate implements IUnit<Predicate> {

    private static final long serialVersionUID = 196402070004L;

    private long id = -1;                   // Идентификатор
    private long mindId = -1;                                   // id транзакции
    private Term name = null;               // Имя предиката
    private int range = 0;                  // К-во параметров

    private transient Mind mind = null;

    private transient long nameId = -1;

    private transient boolean deleted = false;

    public Predicate() {
    }

    public Predicate(Term name, int range) {
        this.name = name;
        this.range = range;
        this.nameId = name.getId();
    }

    public Predicate(Mind mind) {
        this.mind = mind;
    }

    @Override
    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putLong(id)
                .putLong(mindId)
                .putByte(deleted ? 1 : 0)
                .putLong(nameId)
                .putInt(range);
        return packet.createMarked();
    }

    @Override
    public Predicate apply(ByteBuffer packet) throws OutOfBufferException {
        id = packet.getLong();
        mindId = packet.getLong();
        deleted = packet.getByte() != 0;
        nameId = packet.getLong();
        range = packet.getInt();
        return this;
    }

    public Term getName() throws Exception {
        if (name == null) {
            name = mind.getTerms().load(nameId);
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
        for (Right r : mind.getRights()) {
            if (r.isStored() && !r.isDeleted() && getId() == r.getDomain().getPredicateId()) {
                set.add(r.getDomain());
            }
        }
        return set;
    }

//    public Set<Domain> getRelates() {
//        Set<Domain> set = new HashSet<>();
//        for (Domain d : mind.getDomains()) {
//            if (getId() == d.getPredicate().getId()) {
//                set.add(d);
//            }
//        }
//        return set;
//    }

//    public Set<Tree> getLinkedTrees() {
//        Set<Tree> set = new HashSet<>();
//        for (Tree t : mind.getTrees()) {
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
//        for (Domain d : mind.getDomains()) {
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
        } catch (Exception e) {
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
    public Mind getMind() {
        return mind;
    }

    @Override
    public Predicate setMind(Mind mind) {
        this.mind = mind;
        return this;
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
        int hash = 3;
        hash = 47 * hash + (int) (id ^ (id >>> 32));
        return hash;
//        return ("" + id).hashCode();
    }

    public long getNameId() {
        return nameId;
    }

    @Override
    public UnitType getUnitType() {
        return UnitType.PREDICATE;
    }

    @Override
    public long getMindId() {
        return mindId;
    }

    @Override
    public void setMindId(long mindId) {
        this.mindId = mindId;
    }

//    public Predicate commit(Mind m) throws Exception {
//        setName(name.commit(m));
//        Predicate predicate = m.getPredicates().find(name, range);
//        if (predicate == null) {
//            predicate = m.getPredicates().add(name, range);
//        }
//        predicate.setMind(m);
//        return predicate;
//    }
}
