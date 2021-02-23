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

package org.kanger.primitives;

import org.kanger.Mind;
import org.kanger.compiler.Operation;
import org.kanger.compiler.Parser;
import org.kanger.enums.ArgumentType;
import org.kanger.enums.Enums;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IPredicate;
import org.kanger.interfaces.ISolve;
import org.kanger.storage.ByteBuffer;
import org.kanger.units.Predicate;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 * <p>
 * Описатель варианта решения предиката
 */
public class Solve implements ISolve {

    private static final long serialVersionUID = 196402070001L;

    protected boolean antc = true;                                // ! или ?
    protected int range = 0;
    protected IPredicate predicate = null;                         // Ссылка на описатель предиката
    protected ArgumentsList arguments = new ArgumentsList();       // Массив подстановочных переменных

    protected transient long predicateId = -1;
//    protected transient long rightId = -1;

    public Solve() {
    }

    public Solve(IPredicate pred, boolean antc, ArgumentsList args) {
        setPredicate(pred);
        setAntc(antc);
        getArguments().addAll(args);
    }

    @Override
    public IPredicate getPredicate(IMind mind) throws Exception {
        if (predicate == null) {
            predicate = mind.getPredicates().get(predicateId);
        }
        return predicate;
    }

    public void setPredicate(IPredicate predicate) {
        this.predicateId = predicate.getId();
        this.predicate = predicate;
        this.range = predicate.getRange();
    }

//    public Right getRight(Mind mind) throws Exception {
//        if (right == null) {
//            right = mind.getRights().load(rightId);
//        }
//        return right;
//    }
//
//    public void setRight(Right r) {
//        this.rightId = r.getId();
//        right = r;
//    }

    @Override
    public ArgumentsList getArguments() {
        return arguments;
    }

    @Override
    public boolean isAntc() {
        return antc;
    }

    public void setAntc(boolean antc) {
        this.antc = antc;
    }

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

    protected String formatParam(IMind mind, IArgument t) throws Exception {
        String s = "";
        //TODO: Костыль
//        t.setUser(user);
//        if(!t.isEmpty(mind)) {
//            t.getValue(mind).setMind(mind);
//        }

        if (t.getType() == ArgumentType.FUNCTION) {
            s += t.getObject(mind).toString();
        } else if (t.getType() == ArgumentType.TVARIABLE) {
            s += t.getObject(mind).toString();
        } else if (t.getType() == ArgumentType.TVALUE) {
            s += t.getObject(mind).toString();
        } else if (t.getType() == ArgumentType.FVALUE) {
            s += t.getObject(mind).toString();
        } else if (!t.isEmpty((Mind) mind)) {
            s += t.getValue(mind).toString();
        } else {
            s += "_";
        }
        return s;
    }

//    @Override
//    public String toString() {
//        return toString(arguments);
//    }


    //TODO ?$x parent(x,Jack); выводит рещшение Result: TRUE...
    //Solves (1):
    //	Solution 001: !parent(y,Jack); GB
    //Values (1):
    //	Row 001: x=%2

    @Override
    public String toString(IMind mind) {
        return toString(mind, arguments);
    }

    public String toString(IMind mind, ArgumentsList arguments) {
        try {
            String s = String.format("%c", antc ? Enums.ANT : Enums.SUC);

//            List<Term> cVars = new ArrayList<>();
//            for (Term t : arguments.getCVariables(mind)) {
//                //TODO: Костыль
////                t.setMind(mind);
//                if (!cVars.contains(t)) {
//                    cVars.add(t);
//                }
//            }
//            for (Term t : cVars) {
//                s += "$" + t.getName() + " ";
//            }

            Operation op = Parser.getOp(getPredicate(mind).getName().toString(), getRange());

            if (op == null) {
                op = Parser.getOp(getPredicate(mind).getName().toString(), 0);
            }

            if (op == null) {
                s += getPredicate(mind).getName() + "(";
                int i = 0;
                for (IArgument t : arguments) {
                    s += formatParam(mind, t);
                    if (i + 1 != getRange()) {
                        s += (char) Enums.COMMA;
                    }
                    ++i;
                }
                s += ")";
            } else if (op.getRange() == 1) {
                if (op.isPost()) {
                    s += formatParam(mind, arguments.get(0)) + op.getName();
                } else {
                    s += op.getName() + formatParam(mind, arguments.get(0));
                }
            } else {
                for (int i = 0; i < op.getRange(); ++i) {
                    s += formatParam(mind, arguments.get(i));
                    if (i + 1 < op.getRange()) {
                        if (i == 0) {
                            s += " " + op.getName() + " ";
                        } else {
                            s += (char) Enums.COMMA;
                        }
                    }
                }
            }

            return s + ";";
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return "";
        }
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 47 * hash + (antc ? 1 : 0);
        hash = 47 * hash + (int) (predicateId ^ (predicateId >>> 32));
        for (IArgument a : arguments) {
            hash = 47 * hash + (int) (a.getId() ^ (a.getId() >>> 32));
        }
        return hash;
    }

//    public int getHashBase(Mind mind) {
//        int hash = 3;
//        hash = 47 * hash + (antc ? 1 : 0);
//        hash = 47 * hash + (int) (predicateId ^ (predicateId >>> 32));
//        //TODO: ---
//        hash = 47 * hash + arguments.getHash(mind);
////        hash = 47 * hash + arguments.hashCode(); //.getHash(mind);
//        return hash;
//    }

    @Override
    public boolean equals(Object d) {
        if (d != null) {
            if (((Solve) d).getPredicateId() == predicateId || ((Solve) d).getRange() == range) {
                for (int i = 0; i < arguments.size(); ++i) {
                    if (arguments.get(i).getId() != ((Solve) d).getArguments().get(i).getId()) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

//    public boolean isComplete() {
//        boolean complete = true;
//        for (Argument a : arguments) {
//            if (a.isEmpty(mind)) {
//                complete = false;
//                break;
//            }
//        }
//        return complete;
//    }

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

    public long getPredicateId() {
        return predicateId;
    }

    @Override
    public int getRange() {
        return range;
    }

    public void setRange(int range) {
        this.range = range;
    }

    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putLong(predicateId)
                .putInt(range)
                .putByte(antc ? 1 : 0)
                .append(arguments.pack());
        return packet.createMarked();
    }

    public Solve apply(ByteBuffer packet) throws Exception {
        predicateId = packet.getLong();
        range = packet.getInt();
        antc = packet.getByte() != 0;
        try {
            packet.mark();
            arguments = new ArgumentsList().apply(packet);
        } finally {
            packet.release();
        }
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

//    public int getHashStruct(Mind mind) throws Exception {
//        int hash = 3;
//        hash = 47 * hash + (antc ? 1 : 0);
//        hash = 47 * hash + (int) (predicateId ^ (predicateId >>> 32));
//        hash = 47 * hash + range;
//        for (int i = 0; i < range; ++i) {
//            hash = 47 * hash + (i + 1) * arguments.get(i).getType().ordinal();
//            switch (arguments.get(i).getType()) {
//                case TVARIABLE:
//                    hash = 47 * hash + (i + 1) * getVarOrder(mind, i);
//                    break;
//                case TERM:
//                    long id = arguments.get(i).getValue(mind).getId();
//                    hash = 47 * hash + (i + 1) * (int) (id ^ (id >>> 32));
//                    break;
//                case FUNCTION:
//                    hash = 47 * hash + (i + 1) * arguments.get(i).getF(mind).getHashStruct(getRight(mind));
//                    break;
//            }
//        }
//        return hash;
//    }
//
//    public boolean equalsToStruct(Mind mind, Solve to) throws Exception {
//        if (to.isAntc() == antc
//                && to.getRange() == range
//                && to.getPredicateId() == predicateId) {
//            int i = 0;
//            for (; i < range; ++i) {
//                if (arguments.get(i).getType() == to.getArguments().get(i).getType()) {
//                    switch (arguments.get(i).getType()) {
//                        case TVARIABLE:
//                            if (getVarOrder(mind, i) != to.getVarOrder(mind, i)) {
//                                return false;
//                            }
//                            break;
//                        case TVALUE:
//                        case TERM:
//                            if (arguments.get(i).getValue(mind).getId() != to.getArguments().get(i).getValue(mind).getId()) {
//                                return false;
//                            }
//                            break;
//                        case FUNCTION:
//                            if (!arguments.get(i).getF(mind).equalsToStruct(to.getArguments().get(i).getF(mind), getRight(mind), to.getRight(mind))) {
//                                return false;
//                            }
//                            break;
//                    }
//                } else {
//                    return false;
//                }
//            }
//            return true;
//        } else {
//            return false;
//        }
//    }

//    @Override
//    public boolean isDeleted() {
//        return deleted;
//    }
//
//    @Override
//    public void setDeleted() {
//        this.deleted = true;
//    }
//
//    @Override
//    public long getMindId() {
//        return mindId;
//    }
//
//    @Override
//    public void setMindId(long mindId) {
//        this.mindId = mindId;
//    }
//
//    @Override
//    public int compareTo(Solve domain) {
//        return (int) (rightId == domain.rightId ? id - domain.id : rightId - domain.rightId);
//    }

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

    public int getHash(IMind mind) {
        int hash = 3;
        hash = 47 * hash + (antc ? 1 : 0);
        hash = 47 * hash + (int) (predicateId ^ (predicateId >>> 32));
        //TODO: ---
        hash = 47 * hash + arguments.getHash((Mind) mind);
//        hash = 47 * hash + arguments.hashCode(); //.getHash(mind);
        return hash;
    }

    public Collection<Long> getTerms(IMind mind, boolean total) throws Exception {
        Set<Long> terms = new HashSet<>();
        if (total) {
            terms.add(((Predicate) getPredicate(mind)).getNameId());
        }
        terms.addAll(arguments.getTerms(mind, total));
        return terms;
    }
}

