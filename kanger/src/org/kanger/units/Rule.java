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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
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

import java.util.*;

/**
 * Created by Dmitry G. Quznetsov on 20.05.15.
 * <p>
 * Список правил
 */
public class Rule implements IUnit<IRule>, IRule {

    private static final long serialVersionUID = 196402070007L;

    private long id = -1;                                   // ID Правила
    private long mindId = -1;                               // id транзакции
    private ITerm origin = null;                            // Оригинальная строка
    private boolean query = false;                          // Вновь введенное правило
    private boolean generated = false;                      // Правило добавлено в процессе выводс
    private boolean stored = false;                         // Правило добавлено в процессе выводс
    private boolean substitutable = false;                  // Правило содержит t-переменные
    private boolean abstractive = false;                    // Правило содержит c-переменные
    private boolean second = false;                         // Такое правило уже содержится в программе
    private int varIndex = 0;                               // Граниченое значение счетчика переменных
    private List<List<Domain>> tree = new ArrayList<>();    // Ссылка на дерево правила
    private Set<ICause> causes = new HashSet<>();           // Список причин появления правила

    private List<TValue> solves = new ArrayList();
    private Set<Long> predicates = new HashSet<>();         // Список используемых предикатов
    private Set<Long> terms = new HashSet<>();              // Список используемых термов

    private long originId = -1;
    private List<List<Long>> treeIds = new ArrayList<>();
    private Mind mind = null;

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

    private boolean canMutateDirectly() {
        return mind == null || mind.getNext() == null || mindId == mind.getId();
    }

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
        if (canMutateDirectly()) {
            this.generated = b;
        }
    }

    @Override
    public boolean isStored() {
        return stored;
    }

    public void setStored(Mind mind) throws Exception {
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

    public long getOriginId() {
        return originId;
    }

    @Override
    public String getOrigin() throws Exception {
        if (origin == null && originId != -1) {
            origin = mind.getTerms().get(originId);
        }
        return origin == null ? null : (String) origin.getValue();
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
        if (canMutateDirectly()) {
            this.query = current;
        }
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
    public String toString(IMind context) {
        try {
            Mind activeMind = context == null ? mind : (Mind) context;
            String text = isStored()
                    ? getDomain().toString(activeMind)
                    : getOrigin();
            if (text == null) {
                text = "";
            }
            return text
                    + ((activeMind.getDebugLevel() & Enums.DEBUG_OPTION_STATUS) != 0
                    ? " " + mindId + " " +
                    (isGenerated() ? "G" : "") +
                    (isStored() ? "B" : "") +
                    (isStored() && getDomain().isUsed(activeMind) ? "U" : "") +
                    (isQuery() ? "Q" : "")
                    : "");
        } catch (Exception e) {
            System.err.println(new Date());
            e.printStackTrace(System.err);
            return "";
        }
    }

    @Override
    public String toString() {
        return toString(mind);
    }

    @Override
    public String getComment() throws Exception {
        if (mind.getComments().get(id) != null) {
            return mind.getComments().get(id).getComment();
        } else {
            return "";
        }
    }

    @Override
    public void setComment(String comment) throws Exception {
        mind.getComments().add(id, comment);
    }

    @Override
    public int getHash() throws Exception {
        if (stored || (tree.size() == 1 && tree.get(0).size() == 1 && !getDomain().isSubstitutable())) {
            return getDomain().getHashBase(mind);
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
    public Mind getMind() {
        return mind;
    }

    @Override
    public Rule setMind(Mind mind) throws Exception {
        this.mind = mind;
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
                && ((ArgumentsList) x.getArguments()).equalsBase(mind, getDomain().getArguments())) {
            return true;
        } else {
            return false;
        }
    }

    public boolean equalsTo(Solve x) throws Exception {
        Domain domain = getDomain();
        if (x.isAntc() == domain.isAntc()
                && x.getPredicateId() == domain.getPredicateId()
                && x.getRange() == domain.getRange()) {
            int i = 0;
            for (; i < domain.getRange(); ++i) {
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
    public IPredicate getPredicate() throws Exception {
        if (isStored()) {
            return getDomain().getPredicate();
        } else {
            throw new RuntimeErrorException("Using statement reference for non-statement rule");
        }
    }

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

    @Override
    public boolean isLoaded() {
        return origin != null && originId == origin.getId();
    }

    @Override
    public Map<String, Object> createMap(IMind mind) throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("mind_id", mindId);
        map.put("deleted", isDeleted(mind));
        map.put("origin_id", originId);
        map.put("query", query);
        map.put("generated", generated);
        map.put("stored", stored);
        map.put("substitutable", substitutable);
        map.put("abstractive", abstractive);
        map.put("second", second);

        map.put("origin", getOrigin());
        map.put("text", toString(mind));

        return map;
    }

    @Override
    public IRule applyMap(Map<String, Object> map) throws Exception {
        id = Long.parseLong(map.get("id") + "");
        mindId = Long.parseLong(map.get("mind_id") + "");
        boolean deleted = Boolean.parseBoolean(map.get("deleted") + "");
        if (deleted) {
            setDeleted(true, mind);
        }
        originId = Long.parseLong(map.get("origin_id") + "");
        origin = null;
        return null;
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
        if (!canMutateDirectly()) {
            return;
        }
        synchronized (this) {
            for (List<Domain> a : tree) {
                for (Domain d : a) {
                    ArgumentsList list = d.getArguments().convertBase(mind);
                    d.getArguments().clear();
                    d.getArguments().addAll(list);
                }
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

    public boolean containsTerm(long id, Mind mind) throws Exception {
        terms.add(originId);
        for (List<Domain> row : getTree()) {
            for (Domain d : row) {
                terms.addAll(d.getTerms(mind, true));
            }
        }
        return terms.contains(id);
    }

    public boolean containsPredicate(long id) {
        return predicates.contains(id);
    }

    public boolean isSecond() {
        return second;
    }

    public void setSecond(boolean second) {
        this.second = second;
    }
}
