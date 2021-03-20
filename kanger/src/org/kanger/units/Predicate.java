/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to
 *  deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 *  sell copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 *  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 *  IN THE SOFTWARE.
 *
 */

package org.kanger.units;

import org.kanger.Mind;
import org.kanger.compiler.Parser;
import org.kanger.enums.UnitType;
import org.kanger.exception.OutOfBufferException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IPredicate;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.ByteBuffer;

import java.util.*;

/**
 * Created by Dmitry G. Quznetsov on 20.05.15.
 */
public class Predicate implements IUnit<Predicate>, IPredicate {

    private static final long serialVersionUID = 196402070004L;

    private long id = -1;                   // Идентификатор
    private long mindId = -1;                                   // id транзакции
    private ITerm name = null;               // Имя предиката
    private int range = 0;                  // К-во параметров

    private transient Mind mind = null;

    private transient long nameId = -1;

//    private transient boolean deleted = false;

    public Predicate() {
    }

    public Predicate(ITerm name, int range) {
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
                .putByte(isDeleted(mind) ? 1 : 0)
                .putLong(nameId)
                .putInt(range);
        return packet.createMarked();
    }

    @Override
    public Predicate apply(ByteBuffer packet) throws OutOfBufferException {
        id = packet.getLong();
        mindId = packet.getLong();
        if (packet.getByte() != 0) {
            setDeleted(true, mind);
        }
        nameId = packet.getLong();
        range = packet.getInt();
        return this;
    }

    @Override
    public String getName(IMind mind) throws Exception {
        if (name == null) {
            name = mind.getTerms().get(nameId);
        }
        return (String) name.getValue();
    }

    public void setName(ITerm name) {
        this.name = name;
        this.nameId = name.getId();
    }

    @Override
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

    @Override
    public Set<IRule> getSolves(IMind mind) throws Exception {
        Set<IRule> set = new HashSet<>();
        for (IRule r : mind.getRules()) {
            if (r.isStored() && !r.isDeleted(mind) && getId() == ((Rule) r).getPredicateId()) {
                set.add(r);
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

    public String toString(IMind mind) {
        try {
            return getName(mind) + "(" + range + ")";
        } catch (Exception e) {
            System.err.println(new Date());
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
    public boolean isDeleted(IMind mind) {
        return ((Mind) mind).isUnitDeleted(this);
    }

    @Override
    public void setDeleted(boolean on, Mind mind) {
        mind.setUnitDeleted(this, on);
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

    @Override
    public boolean isLoaded() {
        return name != null && nameId == name.getId();
    }

    @Override
    public Map<String, Object> createMap(IMind mind) throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("mind_id", mindId);
        map.put("deleted", isDeleted(mind));
        map.put("name_id", nameId);
        map.put("range", range);
        map.put("name", getName(mind));
        return map;
    }

    @Override
    public Predicate applyMap(Map<String, Object> map) throws Exception {
        id = Long.parseLong(map.get("id") + "");
        mindId = Long.parseLong(map.get("mind_id") + "");
        boolean deleted = Boolean.parseBoolean(map.get("deleted") + "");
        if (deleted) {
            setDeleted(true, mind);
        }
        nameId = Long.parseLong(map.get("name_id") + "");
        range = Integer.parseInt(map.get("range") + "");
        name = null;
        return this;
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

    @Override
    public boolean isEmpty(IMind mind) throws Exception {
        return getSolves(mind).isEmpty();
    }

    public boolean isSystem(Mind mind) throws Exception {
        return Parser.getOp(getName(mind), getRange()) != null;
    }

}
