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
import org.kanger.enums.ArgumentType;
import org.kanger.enums.Enums;
import org.kanger.enums.UnitType;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.ParametersIncompleteException;
import org.kanger.interfaces.*;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.primitives.Argument;
import org.kanger.primitives.ArgumentsList;
import org.kanger.primitives.Cause;
import org.kanger.primitives.Solve;
import org.kanger.storage.ByteBuffer;

import java.util.*;

/**
 * Created by Dmitry G. Quznetsov on 20.05.15.
 * <p>
 * Описатель варианта решения предиката
 */
public class Domain extends Solve implements IUnit<Domain>, Comparable<Domain> {

    private static final long serialVersionUID = 196402070001L;

    private long id = -1;                   // id домена
    private long mindId = -1;               // id транзакции
    private IRule rule = null;              // Ссылка на правило
    private boolean substitutable = false;  // Содержит t-переменные
    private boolean abstractive = false;    // Содержит c-переменные

    private long ruleId = -1;
    private Mind mind = null;

    public Domain() {
    }

    public Domain(Predicate pred, boolean antc, ArgumentsList args, IRule r) {
        this(pred, antc, args);
        setRule(r);
    }

    public Domain(Predicate pred, boolean antc, ArgumentsList args) {
        super(pred, antc, args);
    }

    public Domain(Mind mind) {
        this.mind = mind;
    }

    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putLong(id)
                .putLong(mindId)
                .putByte(isDeleted(mind) ? 1 : 0)
                .putLong(ruleId)
                .putByte(substitutable ? 1 : 0)
                .putByte(abstractive ? 1 : 0)
                .append(super.pack());
        return packet.createMarked();
    }

    public Domain apply(ByteBuffer packet) throws Exception {
        id = packet.getLong();
        mindId = packet.getLong();
        if (packet.getByte() != 0) {
            setDeleted(true, mind);
        }
        ruleId = packet.getLong();
        substitutable = packet.getByte() != 0;
        abstractive = packet.getByte() != 0;
        try {
            packet.mark();
            super.apply(packet);
        } finally {
            packet.release();
        }
        return this;
    }

    @Override
    public UnitType getUnitType() {
        return UnitType.DOMAIN;
    }

    @Override
    public boolean isLoaded() {
        return predicate != null && predicateId == predicate.getId();
    }

    @Override
    public Map<String, Object> createMap(IMind mind) throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("mind_id", mindId);
        map.put("deleted", isDeleted(mind));
        map.put("rule_id", ruleId);
        map.put("solve", super.createMap(mind));
        return map;
    }

    @Override
    public Domain applyMap(Map<String, Object> map) throws Exception {
        id = Long.parseLong(map.get("id") + "");
        mindId = Long.parseLong(map.get("mind_id") + "");
        boolean deleted = Boolean.parseBoolean(map.get("deleted") + "");
        if (deleted) {
            setDeleted(true, mind);
        }
        super.applyMap((Map<String, Object>) map.get("solve"));
        return this;
    }

    public Predicate getPredicate() throws Exception {
        return super.getPredicate(mind);
    }

    public IRule getRule() throws Exception {
        if (rule == null) {
            rule = mind.getRules().get(ruleId);
        }
        return rule;
    }

    public void setRule(IRule r) {
        this.ruleId = r.getId();
        rule = r;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
        this.id = id;
    }

    public IArgument get(int i) {
        return arguments.get(i);
    }

    public void add(IArgument t) {
        arguments.add(t);
    }

    public Set<ICause> getCauses(Mind mind) throws Exception {
        Set<ICause> causes = new HashSet<>();

        ArgumentsList args = arguments.convertBase(mind);
        if (mind.getDomainCauses().containsKey(this) && mind.getDomainCauses().get(this).containsKey(args)) {
            Set<ICause> tmp = mind.getDomainCauses().get(this).get(args.convertBase(mind));
            if (tmp != null) {
                causes.addAll(tmp);
                SortedMap<Integer, Set<ICause>> map = new TreeMap<>();
                for (ICause c : causes) {
                    int weight = 0;
                    for (IArgument a : getArguments()) {
                        for (IArgument b : ((Cause) c).getDonor().getArguments()) {
                            if (!a.isEmpty(mind) && !b.isEmpty(mind) && a.getValue(mind).getId() == b.getValue(mind).getId()) {
                                ++weight;
                                break;
                            }
                        }
                    }
                    if (!map.containsKey(weight)) {
                        map.put(weight, new HashSet<>());
                    }
                    map.get(weight).add(c);
                }
                if (map.size() > 1) {
                    int weight = map.firstKey();
                    for (ICause c : map.get(weight)) {
                        causes.remove(c);
                    }
                    map.remove(weight);
                }
            }
        }
        return causes;
    }

    public boolean setCauses(Collection<Cause> causes, Mind mind) throws Exception {
        boolean result = false;
        if (causes != null) {
            ArgumentsList current = arguments.convertBase(mind);
            if (!mind.getDomainCauses().containsKey(this)) {
                mind.getDomainCauses().put(this, new HashMap<>());
            }
            if (!mind.getDomainCauses().get(this).containsKey(current)) {
                mind.getDomainCauses().get(this).put(current, new HashSet<>());
            } else {
                mind.getDomainCauses().get(this).get(current).clear();
            }
            for (Cause c : causes) {
                if (getOverlaps(c.getDonor().getArguments()) > 0) {
                    mind.getDomainCauses().get(this).get(current).add(c);
                    result = true;
                }
            }
        }
        return result;
    }

    public Set<TValue> getSolves(Mind mind) {
        return getSolves(arguments, mind);
    }

    public void setSolves(Collection<TValue> solves, Mind mind) {
        if (solves != null) {
            ArgumentsList current = arguments.convertBase(mind);
            if (!mind.getDomainSolves().containsKey(this)) {
                mind.getDomainSolves().put(this, new HashMap<>());
            }
            if (!mind.getDomainSolves().get(this).containsKey(current)) {
                mind.getDomainSolves().get(this).put(current, new TreeSet<>());
            } else {
                mind.getDomainSolves().get(this).get(current).clear();
            }
            mind.getDomainSolves().get(this).get(current).addAll(solves);
        }
    }

    public Set<TValue> getSolves(ArgumentsList arguments, Mind mind) {
        ArgumentsList args = arguments.convertBase(mind);
        if (mind.getDomainSolves().containsKey(this) && mind.getDomainSolves().get(this).containsKey(args)) {
            return mind.getDomainSolves().get(this).get(args);
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        return toString(arguments);
    }

    public String toString(ArgumentsList arguments) {
        try {
            String s = super.toString(mind, arguments, false);

            String suffix = "";
            if ((mind.getDebugLevel() & Enums.DEBUG_OPTION_STATUS) != 0) {
                try {
                    suffix += " " + id + " " +
                            (isQuery(arguments, mind) ? "Q" : "") +
                            (isUsed(arguments, mind) ? "U" : "") +
                            (isExcluded(arguments, mind) ? "X" : "") +
                            (isStored(arguments, mind) ? "B" : "") +
                            (isCalculated(arguments, mind) ? "S" : "") +
                            "";
                } catch (Exception e) {
                    System.err.println(new Date());
                    e.printStackTrace(System.err);
                }
            }
            return s + suffix;
        } catch (Exception e) {
            System.err.println(new Date());
            e.printStackTrace(System.err);
            return "";
        }
    }

    public boolean equalsBase(Domain o) throws Exception {
        if (predicateId != o.getPredicateId()) {
            return false;
        }
        if (arguments.size() != o.getArguments().size()) {
            return false;
        }
        for (int i = 0; i < arguments.size(); ++i) {
            if (arguments.get(i).isEmpty(mind) || o.getArguments().get(i).isEmpty(mind)) {
                return false;
            } else if (arguments.get(i).getValue(mind).isCVariable() && o.getArguments().get(i).getValue(mind).isCVariable()
                    && arguments.get(i).getValue(mind).getId() != o.getArguments().get(i).getId()
                    && ((Term) arguments.get(i).getValue(mind)).getParentId(mind) != o.getArguments().get(i).getValue(mind).getId()
                    && arguments.get(i).getValue(mind).getId() != ((Term) o.getArguments().get(i).getValue(mind)).getParentId(mind)) {
                return false;
            } else if (arguments.get(i).getValue(mind).getId() != o.getArguments().get(i).getValue(mind).getId()) {
                return false;
            } else if (!((Term) arguments.get(i).getValue(mind)).equalsTo((Term) o.getArguments().get(i).getValue(mind))) {
                return false;
            }
        }
        return true;
    }

    public int getOverlaps(IList arg) throws Exception {
        Set<Long> ids = new HashSet<>();
        for (IArgument a : arguments) {
            for (IArgument b : arg) {
                if (!a.isEmpty(mind) && !b.isEmpty(mind) && a.getValue(mind).getId() == b.getValue(mind).getId()) {
                    ids.add(a.getValue(mind).getId());
                }
            }
        }
        return ids.size();
    }

    public boolean contains(TVariable t) throws Exception {
        for (TVariable x : arguments.getTVariables(mind)) {
            if (x.getId() == t.getId()) {
                return true;
            }
        }
        return false;
    }

    public boolean isUsed(Mind mind) {
        return isUsed(arguments, mind);
    }

    public void setUsed(Mind mind) {
        setUsed(arguments, mind);
    }

    public boolean isUsed(ArgumentsList arguments, Mind mind) {
        if (mind.getUsedDomains().containsKey(this)) {
            for (ArgumentsList list : mind.getUsedDomains().get(this)) {
                if (arguments.equalsBase(mind, list)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setUsed(ArgumentsList arguments, Mind mind) {
        if (!mind.getUsedDomains().containsKey(this)) {
            mind.getUsedDomains().put(this, new HashSet<>());
        }
        if (!isUsed(mind)) {
            mind.getUsedDomains().get(this).add(arguments.convertBase(mind));
        }
    }

    public boolean isExcluded(ArgumentsList args, Mind mind) {
        if (mind.getExcludedDomains().containsKey(this)) {
            for (ArgumentsList list : mind.getExcludedDomains().get(this)) {
                if (args.equalsBase(mind, list)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isExcluded(Mind mind) {
        return isExcluded(arguments, mind);
    }

    public void setExcluded(ArgumentsList args, Mind mind) {
        if (!mind.getExcludedDomains().containsKey(this)) {
            mind.getExcludedDomains().put(this, new HashSet<>());
        }
        if (!isExcluded(args, mind)) {
            mind.getExcludedDomains().get(this).add(args.convertBase(mind));
        }
    }

    public boolean isProduced(Mind mind) throws Exception {
        if (mind.getProducedDomains().containsKey(this)) {
            for (List<ITerm> list : mind.getProducedDomains().get(this)) {
                if (arguments.equalsStamp(mind, list)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setProduced(Mind mind) throws Exception {
        if (!mind.getProducedDomains().containsKey(this)) {
            mind.getProducedDomains().put(this, new ArrayList<>());
        }
        if (!isProduced(mind)) {
            try {
                mind.getProducedDomains().get(this).add(arguments.getStamp(mind));
            } catch (ParametersIncompleteException | OutOfBufferException e) {
            }
        }
    }

    public boolean isCalculated(Mind mind) throws Exception {
        return isCalculated(arguments, mind);
    }

    public boolean isCalculated(ArgumentsList arguments, Mind mind) throws Exception {
        if (mind.getCalculatedDomains().containsKey(this)) {
            for (List<ITerm> list : mind.getCalculatedDomains().get(this)) {
                if (arguments.equalsStamp(mind, list)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void unCalculated(Mind mind) throws Exception {
        if (isCalculated(mind)) {
            for (List<ITerm> list : mind.getCalculatedDomains().get(this)) {
                if (arguments.equalsStamp(mind, list)) {
                    mind.getCalculatedDomains().remove(list);
                    break;
                }
            }
        }
    }

    public void setCalculated(Mind mind) throws Exception {
        if (!mind.getCalculatedDomains().containsKey(this)) {
            mind.getCalculatedDomains().put(this, new ArrayList<>());
        }
        if (!isCalculated(mind)) {
            try {
                mind.getCalculatedDomains().get(this).add(arguments.getStamp(mind));
            } catch (ParametersIncompleteException | OutOfBufferException e) {
            }
        }
    }

    public boolean isStored(Mind mind) throws Exception {
        IRule r = mind.getRules().find(this);
        return r != null && !r.isDeleted(mind);
    }

    public boolean isStored(ArgumentsList args, Mind mind) throws Exception {
        Domain d = new Domain(getPredicate(), antc, args);
        IRule r = mind.getRules().find(d);
        return r != null && !r.isDeleted(mind);
    }

    public IRule setStored(Mind mind) throws Exception {
        IRule r = mind.getRules().store(this);
        return r;
    }

    public IRule createStored(Mind mind) throws Exception {
        IRule r = mind.getRules().add(this);
        return r;
    }

    public boolean isSystem(Mind mind) throws Exception {
        return getPredicate().isSystem(mind);
    }

    public int execSystem(Mind mind) throws Exception {
        if (isSystem(mind)) {
            return mind.executeSystem(this);
        }
        return -1;
    }

    public boolean isQuery(Mind mind) throws Exception {
        return isQuery(arguments, mind);
    }

    public boolean isQuery(ArgumentsList arguments, Mind mind) throws Exception {
        if (ruleId == -1) {
            return false;
        }
        if (getRule().isQuery()) {
            return true;
        } else {
            for (TVariable t : arguments.getTVariables(mind)) {
                if (t.isQuery(mind)) {
                    return true;
                }
            }
            for (IArgument a : arguments) {
                if (a.getType() == ArgumentType.TVALUE
                        && mind.getQueryValues().containsKey(((TValue) a.getObject(mind)).getTVar(mind))
                        && mind.getQueryValues().get(((TValue) a.getObject(mind)).getTVar(mind)).contains(a.getObject(mind))) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public int getHash() {
        return 47 * getHashBase(mind) + (int) (ruleId ^ (ruleId >>> 32));
    }

    public int getHashBase(Mind mind) {
        int hash = 3;
        hash = 47 * hash + (antc ? 1 : 0);
        hash = 47 * hash + (int) (predicateId ^ (predicateId >>> 32));
        hash = 47 * hash + arguments.getHash(mind);
        return hash;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 47 * hash + (int) (id ^ (id >>> 32));
        return hash;
    }


    @Override
    public boolean equals(Object d) {
        return d != null && d instanceof Domain && ((Domain) d).id == id;
    }

    public boolean isComplete() {
        boolean complete = true;
        for (IArgument a : arguments) {
            if (a.isEmpty(mind)) {
                complete = false;
                break;
            }
        }
        return complete;
    }

    @Override
    public Mind getMind() {
        return mind;
    }

    @Override
    public Domain setMind(Mind mind) throws Exception {
        this.mind = mind;
        for (TVariable t : arguments.getTVariables(mind)) {
            t.setMind(mind);
        }
        return this;
    }

    public int getHashStruct() throws Exception {
        int hash = 3;
        hash = 47 * hash + (antc ? 1 : 0);
        hash = 47 * hash + (int) (predicateId ^ (predicateId >>> 32));
        hash = 47 * hash + range;
        for (int i = 0; i < range; ++i) {
            hash = 47 * hash + (i + 1) * arguments.get(i).getType().ordinal();
            switch (arguments.get(i).getType()) {
                case TVARIABLE:
                    hash = 47 * hash + (i + 1) * getVarOrder(mind, i);
                    break;
                case TERM:
                    ITerm a = arguments.get(i).getValue(mind);
                    if (a.isCVariable()) {
                        hash = 47 * hash + (i + 1) * getVarOrder(mind, i);
                    } else {
                        long id = arguments.get(i).getValue(mind).getId();
                        hash = 47 * hash + (i + 1) * (int) (id ^ (id >>> 32));
                    }
                    break;
                case FUNCTION:
                    hash = 47 * hash + (i + 1) * ((Function) arguments.get(i).getObject(mind)).getHashStruct(getRule());
                    break;
            }
        }
        return hash;
    }

    public boolean equalsToStruct(Domain to) throws Exception {
        if (to.isAntc() == antc
                && to.getRange() == range
                && to.getPredicateId() == predicateId) {
            int i = 0;
            for (; i < range; ++i) {
                if (arguments.get(i).getType() == to.getArguments().get(i).getType()) {
                    switch (arguments.get(i).getType()) {
                        case TVARIABLE:
                            if (getVarOrder(mind, i) != to.getVarOrder(mind, i)) {
                                return false;
                            }
                            break;
                        case TVALUE:
                        case TERM:
                            ITerm a = arguments.get(i).getValue(mind);
                            ITerm b = to.getArguments().get(i).getValue(mind);
                            if (a.isCVariable() && b.isCVariable()) {
                                if (getVarOrder(mind, i) != to.getVarOrder(mind, i)) {
                                    return false;
                                }
                            } else if (a.getId() != b.getId()) {
                                return false;
                            }
                            break;
                        case FUNCTION:
                            if (!((Function) arguments.get(i).getObject(mind))
                                    .equalsToStruct((Function) to.getArguments().get(i).getObject(mind), getRule(), to.getRule())) {
                                return false;
                            }
                            break;
                    }
                } else {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean isDeleted(IMind mind) {
        return ((Mind) mind).isUnitDeleted(this);
    }

    @Override
    public void setDeleted(boolean on, Mind mind) throws Exception {
        mind.setUnitDeleted(this, on);
        for (TVariable t : getArguments().getTVariables(mind)) {
            t.setDeleted(on, mind);
        }
        for (Function f : getArguments().getFunctions(mind)) {
            f.setDeleted(on, mind);
        }
        for (TValue v : getArguments().getTValues(mind, true)) {
            if (v.getMindId() == mind.getId()) {
                v.setDeleted(on, mind);
            }
        }
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
    public int compareTo(Domain domain) {
        return (int) (ruleId == domain.ruleId ? id - domain.id : ruleId - domain.ruleId);
    }

    private void calcVarOrders(Mind mind) throws Exception {
        List<Integer> list = new ArrayList<>();
        SortedMap<Integer, Integer> sort = new TreeMap<>();
        int plains = 0;
        for (int i = 0; i < arguments.size(); ++i) {
            int ix = 0;
            if (arguments.get(i).getType() == ArgumentType.TVARIABLE) {
                ix = ((TVariable) arguments.get(i).getObject(mind)).getIndex();
            } else if (arguments.get(i).getType() == ArgumentType.TERM
                    && !arguments.get(i).isEmpty(mind)
                    && arguments.get(i).getValue(mind).isCVariable()
                    && ((Term) arguments.get(i).getValue(mind)).getRuleId() == ruleId) {
                ix = ((Term) arguments.get(i).getValue(mind)).getIndex();
            } else {
                ++plains;
            }
            list.add(ix);
            sort.put(ix, ix);
        }
        int i = sort.firstKey() == 0 ? 0 : 1;
        for (Integer e : sort.keySet()) {
            sort.put(e, i++);
        }

        for (int pos = 0; pos < getRange(); ++pos) {
            ((Argument) arguments.get(pos)).setVarOrder(plains != arguments.size() ? sort.get(list.get(pos)) + plains : 0);
        }
    }

    private int getVarOrder(Mind mind, int pos) throws Exception {
        if (((Argument) arguments.get(pos)).getVarOrder() == -1) {
            calcVarOrders(mind);
        }
        return ((Argument) arguments.get(pos)).getVarOrder();
    }

    public boolean equalsTo(Domain to) {
        try {
            if (to.isAntc() == antc
                    && to.getPredicateId() == predicateId
                    && (ruleId == -1 || to.getRuleId() == ruleId)) {
                int i = 0;
                for (; i < getRange(); ++i) {
                    try {
                        if ((to.getArguments().get(i).getType() == ArgumentType.TVARIABLE && arguments.get(i).getType() == ArgumentType.TVARIABLE && to.getArguments().get(i).getId() == arguments.get(i).getId())
                                || (to.getArguments().get(i).getType() == ArgumentType.FUNCTION && arguments.get(i).getType() == ArgumentType.FUNCTION && to.getArguments().get(i).getId() == arguments.get(i).getId())
                                || (to.getArguments().get(i).getType() != ArgumentType.TVARIABLE && arguments.get(i).getType() != ArgumentType.TVARIABLE
                                && to.getArguments().get(i).getType() != ArgumentType.FUNCTION && arguments.get(i).getType() != ArgumentType.FUNCTION
                                && !to.getArguments().get(i).isEmpty(mind) && !arguments.get(i).isEmpty(mind)
                                && to.getArguments().get(i).getValue(mind).getId() == arguments.get(i).getValue(mind).getId())) {
                        } else {
                            break;
                        }
                    } catch (Exception e) {
                        System.err.println(new Date());
                        e.printStackTrace(System.err);
                    }
                }
                return i == getRange();
            } else {
                return false;
            }
        } catch (Exception e) {
            System.err.println(new Date());
            e.printStackTrace(System.err);
            return false;
        }
    }

    public long getRuleId() {
        return ruleId;
    }

    public boolean isSubstitutable() {
        return substitutable;
    }

    public void setSubstitutable() {
        this.substitutable = true;
    }

    public boolean isAbstractive() {
        return abstractive;
    }

    public void setAbstractive() {
        this.abstractive = true;
    }
}

