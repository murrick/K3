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
import org.kanger.enums.Enums;
import org.kanger.enums.UnitType;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.*;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.primitives.ArgumentsList;
import org.kanger.primitives.Cause;
import org.kanger.primitives.Solve;
import org.kanger.storage.ByteBuffer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 * <p>
 * Список правил
 */
public class Rule implements IUnit<IRule>, IRule {

    private static final long serialVersionUID = 196402070007L;

    private long id = -1;                                   // ID Правила
    private long mindId = -1;                                   // id транзакции
    private ITerm origin = null;                               // Оригинальная строка
    private boolean query = false;                          // Вновь введенное правило
    private boolean generated = false;                      // Правило добавлено в процессе выводс
    private boolean stored = false;                         // Правило добавлено в процессе выводс
    private boolean substitutable = false;                  // Правило содержит t-переменные
    private boolean abstractive = false;                    // Правило содержит c-переменные
    private List<List<Domain>> tree = new ArrayList<>();    // Ссылка на дерево правила
    private Set<ICause> causes = new HashSet<>();

    private List<TValue> solves = new ArrayList();
    private Set<Long> predicates = new HashSet<>();
    private Set<Long> terms = new HashSet<>();

    private int varIndex = 0;

    private transient long originId = -1;
    private transient List<List<Long>> treeIds = new ArrayList<>();

    //    private transient IUser user = null;
    private transient Mind mind = null;

//    private transient boolean deleted = false;

    public Rule() {
    }

    public Rule(Mind mind) {
        this.mind = mind;
        List<Domain> t = new ArrayList<>();
        tree.add(t);
    }

    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putLong(id)
                .putLong(mindId)
                .putByte(isDeleted(mind) ? 1 : 0)
                .putLong(originId)
                .putInt(varIndex)
                .putByte(query ? 1 : 0)
                .putByte(generated ? 1 : 0)
                .putByte(stored ? 1 : 0)
                .putByte(substitutable ? 1 : 0)
                .putByte(abstractive ? 1 : 0)
                .putInt(tree.size());
        for (List<Domain> branch : tree) {
            packet.putInt(branch.size());
            for (Domain domain : branch) {
                packet.putLong(domain.getId());
            }
        }
        packet.putInt(causes.size());
        for (ICause c : causes) {
            packet.append(((Cause) c).pack());
        }
        return packet.createMarked();
    }

    public Rule apply(ByteBuffer packet) throws Exception {
        id = packet.getLong();
        mindId = packet.getLong();
        if (packet.getByte() != 0) {
            setDeleted(true, mind);
        }
        originId = packet.getLong();
        varIndex = packet.getInt();
        query = packet.getByte() != 0;
        generated = packet.getByte() != 0;
        stored = packet.getByte() != 0;
        substitutable = packet.getByte() != 0;
        abstractive = packet.getByte() != 0;
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
                ICause c = new Cause().apply(packet);
//                c.setUser(user);
                causes.add(c);
            } finally {
                packet.release();
            }
        }
        return this;
    }

    private void checkTreeIsLoaded() throws Exception {
        if (tree.isEmpty() && !treeIds.isEmpty()) {
            for (List<Long> ids : treeIds) {
                List<Domain> branch = new ArrayList<>();
                for (long id : ids) {
                    Domain domain = mind.getDomains().get(id);
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
//        orig = mind.getTerms().load(origId);
//        for (List<Long> ids : treeIds) {
//            List<Domain> branch = new ArrayList<>();
//            for (long id : ids) {
//                Domain domain = mind.getDomains().load(id);
//                branch.add(domain);
//                predicates.add(domain.getPredicate());
//            }
//            tree.add(branch);
//        }
//        for (Cause c : causes) {
////            c.linkExternal(user);
//        }
//    }

    public Domain getDomain() throws Exception {
        return getTree().get(0).get(0);
    }

    @Override
    public Set<ICause> getCauses() {
        return causes;
    }

    public List<TValue> getSolves() {
        return solves;
    }

    @Override
    public boolean isGenerated() {
        return generated;
    }

    public void setGenerated(boolean b) {
        this.generated = b;
    }

    @Override
    public boolean isStored() {
        return stored;
    }

    public void setStored(Mind mind) throws Exception {
//        setDeleted(false, mind);
        this.stored = true;
    }

    public boolean isUsed(Mind mind) {
        return mind.getUsedRules().containsKey(0L) && mind.getUsedRules().get(0L).contains(this);
    }

    public void setUsed(Mind mind) {
        if (!mind.getUsedRules().containsKey(0L)) {
            mind.getUsedRules().put(0L, new HashSet<>());
        }
        mind.getUsedRules().get(0L).add(this);
    }

    public Set<Rule> getNatives() throws Exception {
        Set<Rule> list = new HashSet<>();
        for (List<Domain> t : getTree()) {
            for (Domain d : t) {
                for (IRule r : mind.getRules()) {
                    if (r != null) {
                        if (!r.isDeleted(mind) && ((Rule) r).getPredicates().contains(d.getPredicateId())) {
                            list.add((Rule) r);
                        }
                    } else {
                        break;
                    }
                }
            }
        }
        return list;
    }

    public List<List<Domain>> getTree() throws Exception {
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

    @Override
    public ITerm getOrigin() throws Exception {
        if (origin == null && originId != -1) {
            origin = mind.getTerms().get(originId);

            //TODO: Кастыль
//            if (origin == null && isStored()) {
//                int save = mind.getDebugLevel();
//                mind.setDebugLevel(0);
//                origin = mind.getTerms().add(getDomain().toString());
//                mind.setDebugLevel(save);
//            }

        }
        return origin;
    }

    public void setOrigin(ITerm origin) {
        this.origin = origin;
        this.originId = origin.getId();
    }

    @Override
    public boolean isQuery() {
        return query;
    }

    public void setQuery(boolean current) {
        this.query = current;
    }

    public int size() throws Exception {
        return getTree().size();
    }

    public List<Domain> cloneTree(List<Domain> branch) throws Exception {
        List<Domain> list = new ArrayList<>();
        list.addAll(branch);
        getTree().add(list);
        return list;
    }

    @Override
    public String toString() {
        try {
            return getOrigin().toString()
                    + ((mind.getDebugLevel() & Enums.DEBUG_OPTION_STATUS) != 0
                    ? " " + mindId + " " +
                    (isGenerated() ? "G" : "") +
                    (isStored() ? "B" : "") +
                    (isStored() && getDomain().isUsed(mind) ? "U" : "") +
                    (isQuery() ? "Q" : "")
                    : "");
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return "";
        }
    }

    @Override
    public IComment getComment() throws Exception {
        return mind.getComments().get(id);
    }

    @Override
    public void setComment(String comment) throws Exception {
        mind.getComments().add(id, comment);
    }

    //TODO: 5  !~b(z); ?b(z) -> c(z);  => TRUE - Не верно

    @Override
    public int getHash() throws Exception {
        //TODO: 4
        if (stored || (tree.size() == 1 && tree.get(0).size() == 1 && !getDomain().isSubstitutable())) {
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

    private boolean branchEquals(List<Domain> a, List<Domain> b) throws Exception {
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
    public boolean equalsTo(IRule to) throws Exception {
//        if (stored || (tree.size() == 1 && tree.get(0).size() == 1)) {
//            return equalsTo(to.getDomain());
//        } else
        if (getTree().size() == ((Rule) to).getTree().size()) {
            List<List<Domain>> tmp = new ArrayList<>();
            tmp.addAll(((Rule) to).getTree());
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
    public IMind getMind() {
        return mind;
    }

    @Override
    public Rule setMind(Mind mind) throws Exception {
        this.mind = mind;
//        for (Cause c : getCauses()) {
//            c.setUser(user);
//        }

        for (List<Domain> list : getTree()) {
            for (Domain d : list) {
                d.setMind(mind);
            }
        }
        return this;
    }

    public boolean equalsTo(IHypothesis x) throws Exception {
        if (isStored()
                && x.getPredicate().getId() == getDomain().getPredicateId()
                && x.getPredicate().getRange() == getDomain().getRange()
                && x.getArguments().equalsBase(mind, getDomain().getArguments())) {
            return true;
        } else {
            return false;
        }
    }

    public boolean equalsTo(ISolve x) throws Exception {
        Domain domain = getDomain();
        if (x.isAntc() == domain.isAntc()
                && ((Solve) x).getPredicateId() == domain.getPredicateId()
                && x.getRange() == domain.getRange()) {
            int i = 0;
            for (; i < domain.getRange(); ++i) {
                //TODO: Костыль!
//                    x.get(i).setUser(user);
                if (!x.getArguments().get(i).isEmpty(mind)
                        && !domain.getArguments().get(i).isEmpty(mind)
                        && x.getArguments().get(i).getValue(mind).getId() != domain.getArguments().get(i).getValue(mind).getId()) {
                    break;
                }

                TValue a = null;
                switch (x.getArguments().get(i).getType()) {
                    case TVARIABLE:
                        a = ((TVariable) x.getArguments().get(i).getObject(mind)).getCurrent();
                        break;
                    case TVALUE:
                        a = (TValue) x.getArguments().get(i).getObject(mind);
                        break;
                }
                TValue b = null;
                switch (domain.getArguments().get(i).getType()) {
                    case TVARIABLE:
                        b = ((TVariable) domain.getArguments().get(i).getObject(mind)).getCurrent();
                        break;
                    case TVALUE:
                        b = (TValue) domain.getArguments().get(i).getObject(mind);
                        break;
                }
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

    public Set<Long> getTerms() {
        return terms;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 47 * hash + (int) (id ^ (id >>> 32));
        return hash;
//        return ("" + id).hashCode();
    }

    @Override
    public boolean equals(Object t) {
        return !(t == null || !(t instanceof Rule)) && ((Rule) t).id == id;
    }

    public int getVarIndex() {
        return varIndex;
    }

    public void setVarIndex(int varIndex) {
        this.varIndex = varIndex;
    }

    @Override
    public boolean isRestored(IMind mind) {
        return ((Mind) mind).getRestored().containsKey(UnitType.RULE) && ((Mind) mind).getRestored().get(UnitType.RULE).contains(id);
    }

    @Override
    public boolean isDeleted(IMind mind) {
        return ((Mind) mind).isUnitDeleted(this);
    }

    @Override
    public void setDeleted(boolean on, Mind mind) throws Exception {
        mind.setUnitDeleted(this, on);
        if (mind.getComments().get(id) != null) {
            mind.getComments().get(id).setDeleted(on, mind);
        }
        for (List<Domain> list : getTree()) {
            for (Domain d : list) {
                d.setDeleted(on, mind);
            }
        }

    }

    @Override
    public boolean isSubstitutable() {
        return substitutable;
    }

    public void setSubstitutable(boolean substitutable) {
        this.substitutable = substitutable;
    }

    @Override
    public boolean isAbstractive() {
        return abstractive;
    }

    public void setAbstractive(boolean abstractive) {
        this.abstractive = abstractive;
    }

//    public TSolve addTSolve(List<TValue> list) {
//        TSolve tmp = findTSolve(list);
//        if (tmp != null) {
//            return tmp;
//        } else {
//            if (!mind.getRightSolves().containsKey(this)) {
//                mind.getRightSolves().put(this, new ArrayList<>());
//            }
//            tmp = new TSolve(list, mind);
//            mind.getRightSolves().get(this).add(tmp);
//            return tmp;
//        }
//    }
//
//    public TSolve findTSolve(List<TValue> list) {
//        TSolve tmp = new TSolve(list, mind);
//        if (mind.getRightSolves().containsKey(this)) {
//            for (TSolve t : mind.getRightSolves().get(this)) {
//                if (tmp.equalsTo(t)) {
//                    return t;
//                }
//            }
//        }
//        return null;
//    }
//
//    public List<TSolve> getTSolves() {
//        if (!mind.getRightSolves().containsKey(this)) {
//            mind.getRightSolves().put(this, new ArrayList<>());
//        }
//        return mind.getRightSolves().get(this);
//    }

//    public Set<TVariable> setTSlolve(TSolve s) throws Exception {
//        final SortedSet<TVariable> tvars = new TreeSet<>();
//        for (List<Domain> tree : getTree()) {
//            for (Domain d : tree) {
//                tvars.addAll(d.getArguments().getTVariables(mind, true));
//            }
//        }
//        for (TVariable t : tvars) {
//            t.setCurrent(s == null ? null : s.getValue(t));
////            mind.getTValues().set(t, s == null ? null : s.getValue(t));
//        }
//        return tvars;
//    }

    @Override
    public UnitType getUnitType() {
        return UnitType.RULE;
    }

    @Override
    public long getMindId() {
        return mindId;
    }

    @Override
    public boolean isAntc() throws Exception {
        if (isStored()) {
            return getDomain().isAntc();
        } else {
            throw new RuntimeErrorException("Using statement reference for non-statement rule");
        }
    }

    @Override
    public IPredicate getPredicate(IMind mind) throws Exception {
        if (isStored()) {
            return getDomain().getPredicate();
        } else {
            throw new RuntimeErrorException("Using statement reference for non-statement rule");
        }
    }

    @Override
    public long getPredicateId() throws Exception {
        if (isStored()) {
            return getDomain().getPredicateId();
        } else {
            throw new RuntimeErrorException("Using statement reference for non-statement rule");
        }
    }

    @Override
    public ArgumentsList getArguments() throws Exception {
        if (isStored()) {
            return getDomain().getArguments();
        } else {
            throw new RuntimeErrorException("Using statement reference for non-statement rule");
        }
    }

    @Override
    public void setMindId(long mindId) {
        this.mindId = mindId;
    }

//    public Right commit(Mind m) throws Exception {
//        m.compile(orig.toString());
//        if (m.getRights().find(this) == null) {
//            setOrig(orig.commit(m));
//            predicates.clear();
//            for (List<Domain> list : tree) {
//                for (Domain d : list) {
//                    d.commit(m);
//                    predicates.add(d.getPredicateId());
//                }
//            }
//            for (TVariable t : mind.getTVars()) {
//                if (t.getRight().getId() == id) {
//                    t.commit(m);
//                }
//            }
//            for (Cause c : causes) {
//                c.commit(mind, m);
//            }
//            for (TValue t : solves) {
//                t.commit(m);
//            }
//            m.getRights().register(this);
//            m.getRights().add(this);
//            this.setMind(m);
//        } else {
//            mind.getRights().delete(this);
//        }
//        return this;
//    }

    //    public Right commit(Mind m) throws Exception {
//        if (m.getRights().find(this) == null) {
//            setOrig(orig.commit(m));
//            predicates.clear();
//            for (List<Domain> list : tree) {
//                for (Domain d : list) {
//                    d.commit(m);
//                    predicates.add(d.getPredicateId());
//                }
//            }
//            for (TVariable t : mind.getTVars()) {
//                if (t.getRight().getId() == id) {
//                    t.commit(m);
//                }
//            }
//            for (Cause c : causes) {
//                c.commit(mind, m);
//            }
//            for (TValue t : solves) {
//                t.commit(m);
//            }
//            m.getRights().register(this);
//            m.getRights().add(this);
//            this.setMind(m);
//        } else {
//            mind.getRights().delete(this);
//        }
//        return this;
//    }


    @Override
    public boolean isLoaded() {
        return origin != null && originId == origin.getId();
    }

    public List<TVariable> getTVariables() {
        List<TVariable> list = new ArrayList<>();
        for (TVariable t : mind.getTVars()) {
            if (t.getRuleId() == id) {
                list.add(t);
            }
        }
        return list;
    }

    public void primitivize() {
        for (List<Domain> a : tree) {
            for (Domain d : a) {
                ArgumentsList list = d.getArguments().convertBase(mind);
                d.getArguments().clear();
                d.getArguments().addAll(list);
            }
        }
    }

    public void packCauses(IMind mind) throws Exception {
        List<ICause> toDelete = new ArrayList<>();
        for (ICause c : causes) {
            IRule r = c.getDonor(mind);
            if (r == null || r.isDeleted(mind) || c.getRule(mind).isDeleted(mind)) {
                toDelete.add(c);
            }
        }
        for (ICause c : toDelete) {
            causes.remove(c);
        }
    }

    public boolean containsTerm(long id, IMind mind) throws Exception {
        terms.add(originId);
        for (List<Domain> row : tree) {
            for (Domain d : row) {
                terms.addAll(d.getTerms(mind, true));
            }
        }
        return terms.contains(id);
    }

    public boolean containsPredicate(long id) {
        return predicates.contains(id);
    }


//    public void washCauses() throws Exception {
//        if (isStored()) {
//            SortedMap<Integer, Set<Cause>> map = new TreeMap<>();
//            for (Cause c : causes) {
//                int weight = 0;
//                for (Argument a : getDomain().getArguments()) {
//                    for (Argument b : c.getDonor().getArguments()) {
//                        if (!a.isEmpty(mind) && !b.isEmpty(mind) && a.getValue(mind).getId() == b.getValue(mind).getId()) {
//                            ++weight;
//                            break;
//                        }
//                    }
//                }
//
////                if(weight == getDomain().getRange() && getDomain().getPredicateId() == c.getDonor().getPredicateId()) {
////                    weight = 0;
////                }
//
//                if (!map.containsKey(weight)) {
//                    map.put(weight, new HashSet<>());
//                }
//                map.get(weight).add(c);
//            }
//            if (map.size() > 1) {
//                int weight = map.firstKey();
//                for (Cause c : map.get(weight)) {
//                    causes.remove(c);
//                }
//                map.remove(weight);
//            }
//        }
//    }
}
