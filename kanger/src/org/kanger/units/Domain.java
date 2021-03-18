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

    private long id = -1;                                       // id домена
    private long mindId = -1;                                   // id транзакции
    protected transient long ruleId = -1;
    private boolean substitutable = false;                  // Правило содержит t-переменные
    private boolean abstractive = false;                    // Правило содержит c-переменные
//    private int range = 0;
//    private Domain next = null;                                 // Следующий элемент

//    private Stack<List<TValue>> tStack = new Stack<>();
//    private Map<ArgList, SortedSet<Cause>> causes = new HashMap<>();

//    private transient long predicateId = -1;
//    private transient long rightId = -1;

    private transient Mind mind = null;
    //    private boolean antc = true;                                // ! или ?
//    private Predicate predicate = null;                         // Ссылка на описатель предиката
//    private ArgList arguments = new ArgList();       // Массив подстановочных переменных
    private IRule rule = null;                                        // Ссылка на правило

//    private transient boolean deleted = false;

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

//    public Right getRight() throws Exception {
//        return super.getRight(mind);
//    }

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


//    public boolean isQueued() {
//        return mind.getQueuedDomains().contains(id);
//    }

//    public boolean isAcceptor() {
//        return mind.getAcceptorDomains().contains(id);
//    }
//
//    public void setAcceptor(boolean on) {
//        if (on) {
//            mind.getAcceptorDomains().createTVar(id);
//        } else {
//            mind.getAcceptorDomains().remove(id);
//        }
//    }
//

//    public void setQueued() {
//        mind.getQueuedDomains().createTVar(id);
//    }

//    public Set<Domain> getCauses() {
//        Set<Domain> list = new HashSet<>();
//        for (TValue t : arguments.getTValues(true)) {
//            if (!t.isEmpty()) {
//                for (Cause s : t.getCauses()) {
//                    if (s.getDst().getId() != id) {
//                        list.add(s.getSrc());
//                    }
//                }
//            }
//        }
//        return list;
//    }

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

//                if(weight == getDomain().getRange() && getDomain().getPredicateId() == c.getDonor().getPredicateId()) {
//                    weight = 0;
//                }

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


//    private Set<Cause> getCauses(Mind mind) throws Exception {
//        ArgList args = arguments.convertBase(mind);
//        return getCauses(args, mind);
//    }
//
//    private Set<Cause> getCauses(ArgList args, Mind mind) {
////        Set<Cause> set =  new HashSet<>();
////        for(TVariable t : arguments.getTVariables(true)) {
////            if(!t.isEmpty()) {
////                for(Cause c : t.getCurrent().getCauses()) {
////                    if(c.getSrc().getRight().isStored() && c.getDst().getRightId() == getRightId()) {
////                        set.add(c);
////                    }
////                }
////            }
////        }
////        return set;
//
//        if (mind.getDomainCauses().containsKey(this) && mind.getDomainCauses().get(this).containsKey(args)) {
//            return mind.getDomainCauses().get(this).get(args.convertBase(mind));
//        } else {
//            return null;
//        }
//    }

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
                if (
//                        c.getDonor().getArguments().equalsBase(mind, current)
//                        && sourceExists(c) == null
//                        &&
                        getOverlaps(c.getDonor().getArguments()) > 0
                ) {
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

//    public void dropSolves(ArgList arguments) {
//        ArgList args = arguments.convertBase();
//        if (mind.getDomainSolves().containsKey(this) && mind.getDomainSolves().get(this).containsKey(args)) {
//            mind.getDomainSolves().get(this).remove(args);
//        }
//    }

//    public SortedSet<Cause> getCauses(ArgList arguments) {
//        return causes.get(arguments);
//    }

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

//    private Cause sourceExists(Cause c) throws Exception {
//        Set<Cause> causes = getCauses();
//        if (causes != null) {
//            for (Cause x : causes) {
//                if (x.getDonor().getPredicateId() == c.getDonor().getPredicateId()
//                        && x.getDonor().getArguments().equalsBase(mind, c.getDonor().getArguments())) {
//                    return x;
//                }
////                if(x.getSrc().sourceExists(c)) {
////                    return true;
////                }
//            }
//        }
//        return null;
//    }

//    public Set<TVariable> getRelatedTVariables(boolean full) {
//        Set<TVariable> set = new HashSet<>();
//        for (Domain d : predicate.getRelates()) {
//            set.addAll(d.getArguments().getTVariables(full));
//        }
//        return set;
//    }

//    public Set<Domain> getParents() {
//        return parents;
//    }


    //    String s = String.format("%c%s(", d.isAntc() ? Enums.ANT : Enums.SUC, d.getPredicate().getName());
//    int i = 0;
//    for (TList t : d.getArguments()) {
//        String name = "_";
//        if (t.isCSet()) name = t.getC().toString();
//        else if (t.isFunction() && t.getFunction().getResult() != null)
//            name = t.getFunction().toString(); // + "=" + t.getFunction().getResult().toString();
//        else if (t.isTVariable() && t.getTVariable().getOwner() != 0) name = t.getTVariable().getValue().getTerm().getName();
//        s += name;
//        if (i + 1 != d.getPredicate().getRange()) {
//            s += String.format("%c", Enums.COMMA);
//        }
//        ++i;
//    }
//    s += ");";
//    return s;
    private String formatParam(Argument t) throws Exception {
        return super.formatParam(mind, t);
//        String s = "";
//        //TODO: Костыль
////        t.setUser(user);
////        if(!t.isEmpty(mind)) {
////            t.getValue(mind).setMind(mind);
////        }
//
//        if (t.isFSet()) {
//            s += t.getF(mind).toString();
//        } else if (t.getType() == ArgumentType.TVARIABLE) {
//            s += t.getT(mind).toString();
//        } else if (t.getType() == ArgumentType.TVALUE) {
//            s += t.getV(mind).toString();
//        } else if (t.getType() == ArgumentType.FVALUE) {
//            s += t.getR(mind).toString();
//        } else if (!t.isEmpty(mind)) {
//            s += t.getValue(mind).toString();
//        } else {
//            s += "_";
//        }
//        return s;
    }

    @Override
    public String toString() {
        return toString(arguments);
    }


    //TODO ?$x parent(x,Jack); выводит рещшение Result: TRUE...
    //Solves (1):
    //	Solution 001: !parent(y,Jack); GB
    //Values (1):
    //	Row 001: x=%2

    public String toString(ArgumentsList arguments) {
        try {
            String s = super.toString(mind, arguments);

            String suffix = "";
//            if ((mind.getDebugLevel() & 0x00FF) == Enums.DEBUG_LEVEL_DEBUG) {
//                suffix += " " + id; // + " " + mindId + " " + mind.getId();
//            }
            if ((mind.getDebugLevel() & Enums.DEBUG_OPTION_STATUS) != 0) {
                try {
                    suffix += " " + id + " " +
                            //(isDest() ? "A" : "") +
                            (isQuery(arguments, mind) ? "Q" : "") +
                            (isUsed(arguments, mind) ? "U" : "") +
                            (isExcluded(arguments, mind) ? "X" : "") +
//                            (isProduced() ? "P" : "") +
                            (isStored(arguments, mind) ? "B" : "") +
                            (isCalculated(arguments, mind) ? "S" : "") +
                            "";
                } catch (Exception e) {
                    e.printStackTrace(System.err);
                }
            }
            return s + suffix;
        } catch (Exception e) {
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
//        boolean found = false;
        int cnt = 0;
        for (int i = 0; i < arguments.size(); ++i) {
            if (arguments.get(i).isEmpty(mind) || o.getArguments().get(i).isEmpty(mind)) {
                return false;
//            } else if (id != -1 && o.getId() != -1
//                    && arguments.get(i).isCVar(mind) && o.getArguments().get(i).isCVar(mind)
//                    && arguments.get(i).getValue(mind).getId() != o.getArguments().get(i).getValue(mind).getId()
//                    && !arguments.get(i).getValue(mind).getSlaves().isEmpty() && !o.getArguments().get(i).getValue(mind).getSlaves().isEmpty()                    ) {
//                return false;
            } else if (arguments.get(i).getValue(mind).isCVariable() && o.getArguments().get(i).getValue(mind).isCVariable()
                    && arguments.get(i).getValue(mind).getId() != o.getArguments().get(i).getId()
                    && ((Term) arguments.get(i).getValue(mind)).getParentId() != o.getArguments().get(i).getValue(mind).getId()
                    && arguments.get(i).getValue(mind).getId() != ((Term) o.getArguments().get(i).getValue(mind)).getParentId()) {
                return false;
            } else if (//id != -1 && o.getId() != -1
                //&&
                    arguments.get(i).getValue(mind).getId() != o.getArguments().get(i).getValue(mind).getId()
//                            && ((!o.getArguments().get(i).isCVar(mind)
//                            && !arguments.get(i).isCVar(mind)) || arguments.get(i).getValue(mind).getRightId() == o.getArguments().get(i).getValue(mind).getRightId())
            ) {
                return false;
            } else if (!((Term) arguments.get(i).getValue(mind)).equalsTo((Term) o.getArguments().get(i).getValue(mind))
////                    && !o.getArguments().get(i).isCVar(mind)
////                    && !arguments.get(i).isCVar(mind)
            ) {
                return false;
//            } else if (arguments.get(i).isCVar(mind) && o.getArguments().get(i).isCVar(mind)
//                    && !arguments.get(i).getValue(mind).getRight().isQuery()
//                    && !o.getArguments().get(i).getValue(mind).getRight().isQuery()
//                    && arguments.get(i).getValue(mind).getRightId() != o.getArguments().get(i).getValue(mind).getRightId()
//            ) {
//                ++cnt;
            }

//             Если аргументы только С - результат неопределен
//            if (!arguments.get(i).isCVar(mind)
//                    || !o.getArguments().get(i).isCVar(mind)
//                    || arguments.get(i).getValue(mind).getRight().isQuery()
//                    || o.getArguments().get(i).getValue(mind).getRight().isQuery()
//            ) {
//                ++cnt;
//            }
        }

//        if(!found) {
//            for (int i = 0; i < arguments.size(); ++i) {
//                if(arguments.get(i).getValue(mind).getRightId() == o.getArguments().get(i).getValue(mind).getRightId()) {
//                    found = true;
//                }
//            }
//        }
        return true; //cnt != arguments.size();
    }

//    public boolean equalsSolve(Domain slave) {
//        boolean success = false;
//        if (slave.getPredicate().getId() == predicate.getId() && slave.isAntc() != antc) {
//            success = true;
//            for (int i = 0; i < slave.getPredicate().getRange(); ++i) {
//                if (slave.get(i).isEmpty() || arguments.get(i).isEmpty()
//                        || slave.get(i).getValue().getId() != arguments.get(i).getValue().getId()
////                        || this.isDestFor(i, slave)
////                        || slave.isDestFor(i, this)
////                        || (slave.get(i).getType() == ArgumentType.TVARIABLE && arguments.get(i).getType() == ArgumentType.TVARIABLE
////                        && slave.get(i).getT().getId() == arguments.get(i).getT().getId())
//                ) {
//                    success = false;
//                    break;
//                }
//
////                    if (isDestFor(i, slave) || slave.isDestFor(i, this)) {
////                        success = false;
////                        break;
////                    } else {
//
////                if (get(i).getType() == ArgumentType.TVALUE && slave.get(i).getType() == ArgumentType.TVALUE && get(i).getV().isRelativeFor(slave.get(i).getV())) {
////                    success = false;
////                    break;
////                }
//
////TODO: Разобраться с isDestFor для предикатов. Использовать для операций с БД добавленные правила вместо
////                    }
//            }
//        }
//        return success;
//    }

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

//    public boolean isDestFor(Domain d) {
//        return mind.getSources().containsKey(this) && mind.getSources().createCVar(this).contains(d);
//    }
//
//    public void setDestFor(Domain d) {
//        if (!mind.getDestinations().containsKey(d)) {
//            mind.getDestinations().put(d, new HashSet<>());
//        }
//        if (!mind.getSources().containsKey(this)) {
//            mind.getSources().put(this, new HashSet<>());
//        }
//        mind.getDestinations().createCVar(d).createTVar(this);
//        mind.getSources().createCVar(this).createTVar(d);
//    }

//    public boolean isDestFor(int index, Domain d) {
//        if (index < arguments.size() && ((arguments.get(index).getType() == ArgumentType.TVARIABLE && !arguments.get(index).getT().isEmpty()) || arguments.get(index).getType() == ArgumentType.TVALUE)) {
//            TValue v = arguments.get(index).getType() == ArgumentType.TVALUE ? arguments.get(index).getV() : arguments.get(index).getT().getCurrent();
//            for (Cause s : v.getCauses()) {
//                if (s.getIndex() == index
//                        && s.getDstId() == id
//                        && s.getSrcId() == d.getId()) {
//                    return true;
//                }
//            }
//        }
//        return false;
//
//    }
//
//    public boolean isDest() {
//        for (TVariable t : getTVariables(false)) {
//            if (!t.isEmpty() && t.getDstSolves() != null && /*contains(t)) {*/ t.getDstSolves().contains(this)) {
//                return true;
//            }
//        }
//        return false;
//        //
//        // return mind.getSources().containsKey(this) && !mind.getSources().createCVar(this).isEmpty();
//    }

    //    public List<TVariable> getTVariables(boolean full) {
//        return Tools.getTVariables(arguments, full);
//    }
//
//    public List<TValue> getTValues(boolean full) {
//        return Tools.getTValues(arguments, full);
//    }
//
//    public List<Function> getFunctions() {
//        List<Function> list = new ArrayList<>();
//        for (Argument a : arguments) {
//            if (a.isFSet()) {
//                list.add(a.getF());
//            }
//        }
//        return list;
//    }
//

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
//        arguments.setUser(user);
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
//        if (mind.getExcludedDomains().containsKey(this)) {
//            for (ArgList list : mind.getExcludedDomains().get(this)) {
//                if (arguments.equalsBase(list)) {
//                    return true;
//                }
//            }
//        }
//        return false;
    }

    public void setExcluded(ArgumentsList args, Mind mind) {
        if (!mind.getExcludedDomains().containsKey(this)) {
            mind.getExcludedDomains().put(this, new HashSet<>());
        }
        if (!isExcluded(args, mind)) {
            mind.getExcludedDomains().get(this).add(args.convertBase(mind));
        }
    }

//    public void setExcluded(Mind mind) {
//        setExcluded(arguments, mind);
////        if (!mind.getExcludedDomains().containsKey(id)) {
////            mind.getExcludedDomains().put(id, new HashSet<>());
////        }
////        if (!isExcluded()) {
//////            try {
////            mind.getExcludedDomains().get(id).add(arguments.convertBase());
//////            } catch (ParametersIncompleteException e) {
////////                e.printStackTrace();
//////            }
////        }
//    }

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

//    public void setProduced(int tag, ArgList args) {
//        if (!mind.getProducedDomains().containsKey(this)) {
//            mind.getProducedDomains().put(this, new HashSet<>());
//        }
//        if (!isProduced(args)) {
//            try {
//                mind.getProducedDomains().get(this).add(convertArguments(args));
//            } catch (ParametersIncompleteException e) {
////                e.printStackTrace();
//            }
//        }
//    }
//
//    public boolean isProduced(ArgList args) {
//        if (mind.getProducedDomains().containsKey(this)) {
//            for (ArgList list : mind.getProducedDomains().get(this)) {
//                if (isEqualsArguments(args, list)) {
//                    return true;
//                }
//            }
//        }
//        return false;
//    }
//

//    public void setTag(long tag) throws Exception {
//        for (TVariable t : arguments.getTVariables(mind)) {
//            t.getCurrent().setTag(tag);
//        }
//    }

//    public Set<Long> getTag() {
//        ArgList args = arguments;
//        if (mind.getDomainTags().containsKey(this)
//                && mind.getDomainTags().get(this).containsKey(args)) {
//            return mind.getDomainTags().get(this).get(args);
//        } else {
//            return new HashSet<>();
//        }
//    }

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
//
//        List<TVariable> tVars = getTVariables(true);
//        if(!isSystem() || tVars.isEmpty()) {
//            return false;
//        } else {
//            boolean complete = true;
//            for (TVariable t : getTVariables(true)) {
//                if (t.isEmpty() || !t.getCurrent().isClosed()) {
//                    complete = false;
//                    break;
//                }
//            }
//            return complete;
//        }

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
//        r.setDeleted(false, mind);
        return r;
    }

    public IRule createStored(Mind mind) throws Exception {
        IRule r = mind.getRules().add(this);
//        r.setDeleted(false, mind);
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

//    public boolean recalculate(boolean clear) throws Exception {
//        boolean occurrs = false;
//        for (Function f : getArguments().getFunctions()) {
//            if (f.isCalculable() && f.isEmpty()) {
//                if (clear) {
//                    f.clear();
//                }
//                if (mind.getCalculator().calculate(f, mind.isLogging())) {
//                    occurrs = true;
//                }
//            }
//        }
//        return occurrs;
//    }

//    public boolean recalculate() throws RuntimeErrorException {
//        boolean occurrs = false;
//        for (Argument a : arguments) {
//            if (a.isFunction() /*&& a.getFunction().isComplete()*/) {
////                a.getFunction().clearResult();
//                if (mind.getCalculator().calculate(a.getFunction()) > 0) {
//                    occurrs = true;
//                }
//            }
//        }
//        return occurrs;
//    }

//    public boolean isPairedWith(Domain d) {
//        for (TVariable t : getTVariables(false)) {
//            if (d.getTVariables(false).contains(t)) {
//                return true;
//            }
//        }
//        return false;
//    }

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

//    public boolean isSingleInTree() {
//        for (Tree t : getParentTrees()) {
//            if (t.getSequence().size() == 1) {
//                return true;
//            }
//        }
//        return false;
//    }

//    public boolean isComplete() {
//        for (TVariable t : getTVariables(true)) {
//            if (!t.isComplete() || /*!mind.getUsed().contains(t) ||*/ t.isEmpty()) {
//                return false;
//            }
//        }
//        return true;
//    }

//    public boolean isBlocked() {
//        for (TVariable t : getTVariables(true)) {
//            if (t.isBlocked()) {
//                return true;
//            }
//        }
//        return false;
//    }

//    public int getTVarCount() {
//        int cnt = 0;
//        for (int i = 0; i < arguments.size(); ++i) {
//            if (arguments.get(i).isTVariable()) {
//                ++cnt;
//            }
//        }
//        return cnt;
//    }
//
//    public int getCVarCount() {
//        int cnt = 0;
//        for (int i = 0; i < arguments.size(); ++i) {
//            if (!arguments.get(i).isTVariable() && arguments.get(i).isCVariable()) {
//                ++cnt;
//            }
//        }
//        return cnt;
//    }

//    public void calcVarOrders() throws Exception {
//        for (int pos = 0; pos < getRange(); ++pos) {
//            List<Integer> list = new ArrayList<>();
//            SortedMap<Integer, Integer> sort = new TreeMap<>();
//            int plains = 0;
//            for (int i = 0; i < arguments.size(); ++i) {
//                int ix = 0;
//                if (arguments.get(i).getType() == ArgumentType.TVARIABLE) {
//                    ix = arguments.get(i).getT(mind).getIndex();
//                } else if (arguments.get(i).isCVar(mind) && arguments.get(i).getValue(mind).getRightId() == rightId) {
//                    ix = arguments.get(i).getValue(mind).getIndex();
//                } else {
//                    ++plains;
//                }
//                list.add(ix);
//                sort.put(ix, ix);
//            }
//            int i = sort.firstKey() == 0 ? 0 : 1;
//            for (Integer e : sort.keySet()) {
//                sort.put(e, i++);
//            }
//            arguments.get(pos).setVarOrder(plains != arguments.size() ? sort.get(list.get(pos)) + plains : 0);
//        }
//    }
//
//    public int getVarOrder(int pos) throws Exception {
//        if (arguments.get(pos).getVarOrder() == -1) {
//            calcVarOrders();
//        }
//        return arguments.get(pos).getVarOrder();
//    }

    @Override
    public int getHash() {
        return 47 * getHashBase(mind) + (int) (ruleId ^ (ruleId >>> 32));
    }

    public int getHashBase(Mind mind) {
        int hash = 3;
        hash = 47 * hash + (antc ? 1 : 0);
        hash = 47 * hash + (int) (predicateId ^ (predicateId >>> 32));
        //TODO: ---
        hash = 47 * hash + arguments.getHash(mind);
//        hash = 47 * hash + arguments.hashCode(); //.getHash(mind);
        return hash;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 47 * hash + (int) (id ^ (id >>> 32));
        return hash;

//        return ("" + id).hashCode();
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

    //    public Set<Tree> getParentTrees() {
//        Set<Tree> set = new HashSet<>();
//        for (Tree t : mind.getTrees()) {
//            if (t.getSequence().contains(this)) {
//                set.add(t);
//            }
//        }
//        return set;
//    }
//
//    public void pushValues() {
//        List<TValue> list = new ArrayList<>();
//        for (TVariable t : arguments.getTVariables(true)) {
//            list.add(t.getCurrent());
//        }
//        tStack.push(list);
//    }
//
//    public void popValues() {
//        List<TValue> list = tStack.pop();
//        List<TVariable> ts = arguments.getTVariables(true);
//        for (int i = 0; i < ts.size(); ++i) {
//            if (list.get(i) != null) {
//                ts.get(i).setCurrent(list.get(i));
//            }
//        }
//    }

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
//        arguments.setUser(user);
        return this;
    }

//    public boolean isIntersected(Domain d) {
//        List<TValue> tValues = arguments.getTValues(true);
//        if (tValues.isEmpty()) {
//            return false;
//        } else {
//            boolean found = false;
//            for (TValue v : tValues) {
//                for (int i = 0; i < d.getPredicate().getRange(); ++i) {
//                    Argument a = d.get(i);
//                    if (a.isEmpty()) {
//                        return false;
//                    } else {
//                        if (a.getValue().getId() == v.getValue().getId()) {
//                            for (Cause s : v.getCauses()) {
//                                if (s.getSrc().getPredicate().getId() == d.getPredicate().getId() && s.getIndex() == i) {
//                                    found = true;
//                                    return true;
//                                }
//                            }
//                        }
//                    }
////                    if (found) {
////                        break;
////                    }
//                }
//            }
////            if (!found) {
////                return false;
////            }
//            return true;
//        }
//    }
//    public int getValOrder(int i) {
//    }

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

//    public int compareVars(Domain slave, int pos) throws Exception {
//        if(arguments.get(pos).getType() == ArgumentType.TVARIABLE && slave.get(pos).isCVar() && rightId == slave.get(pos).getValue(mind).getRightId()) {
//            return arguments.get(pos).getT(mind).getIndex() - slave.get(pos).getValue(mind).getIndex();
//        } else {
//            return 0;
//        }
//    }

//    public Domain commit(Mind m) throws Exception {
//        setPredicate(predicate.commit(m));
//        for (Argument a : arguments) {
//            a.setO((IUnit) a.getO(mind).commit(m));
//        }
//        setMind(m);
//        return this;
//    }


    private void calcVarOrders(Mind mind) throws Exception {
        for (int pos = 0; pos < getRange(); ++pos) {
            List<Integer> list = new ArrayList<>();
            SortedMap<Integer, Integer> sort = new TreeMap<>();
            int plains = 0;
            for (int i = 0; i < arguments.size(); ++i) {
                int ix = 0;
                if (arguments.get(i).getType() == ArgumentType.TVARIABLE) {
                    ix = ((TVariable) arguments.get(i).getObject(mind)).getIndex();
//                } else if (arguments.get(i).getValue(mind).isXVariable() && arguments.get(i).getValue(mind).getParent().getRightId() == rightId) {
//                    ix = arguments.get(i).getValue(mind).getParent().getIndex();
//                    ++plains;
                } else if (!arguments.get(i).isEmpty(mind)
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
                        e.printStackTrace(System.err);
                    }
                }
                return i == getRange();
            } else {
                return false;
            }
        } catch (Exception e) {
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

