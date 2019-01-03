package kanger.units;

import kanger.User;
import kanger.compiler.Operation;
import kanger.compiler.Parser;
import kanger.enums.Enums;
import kanger.primitives.ArgList;
import kanger.primitives.Argument;
import kanger.primitives.Cause;
import kanger.interfaces.Identifiable;

import java.io.*;
import java.util.*;

/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 * <p>
 * Описатель варианта решения предиката
 */
public class Domain implements Externalizable, Identifiable {

    private long id = -1;                                       // id домена
    private boolean antc = true;                                // ! или ?
    private Predicate predicate = null;                         // Ссылка на описатель предиката
    private ArgList arguments = new ArgList();       // Массив подстановочных переменных
    private Right right;                                        // Ссылка на правило
    private Domain next = null;                                 // Следующий элемент

    private Stack<List<TValue>> tStack = new Stack<>();
    private Map<ArgList, SortedSet<Cause>> causes = new HashMap<>();

    private User user = null;

    //TODO Нужен конструктор по умолчанию
    public Domain(User user) {
        this.user = user;
    }

    @Override
    public void readExternal(ObjectInput dis) throws IOException, ClassNotFoundException {
        id = dis.readLong();
        antc = dis.readBoolean();
        predicate = (Predicate) dis.readObject();
        arguments = (ArgList) dis.readObject();
        right = (Right) dis.readObject();
    }

    @Override
    public void writeExternal(ObjectOutput dos) throws IOException {
        dos.writeLong(id);
        dos.writeBoolean(antc);
        dos.writeObject(predicate);
        dos.writeObject(arguments);
        dos.writeObject(right);
    }

    public Predicate getPredicate() {
        return predicate;
    }

    public void setPredicate(Predicate predicate) {
        this.predicate = predicate;
    }

    public Right getRight() {
        return right;
    }

    public void setRight(Right right) {
        this.right = right;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
        this.id = id;
    }

    public Domain getNext() {
        return next;
    }

    public void setNext(Domain next) {
        this.next = next;
    }

    public Argument get(int i) {
        return arguments.get(i);
    }

    public void add(Argument t) {
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


    public SortedSet<Cause> getCauses() {
        return causes.get(arguments.convertBase());
    }

//    public SortedSet<Cause> getCauses(ArgList arguments) {
//        return causes.get(arguments);
//    }

    private boolean sourceExists(Cause c) {
        for (Cause x : causes.get(arguments)) {
            if (x.getSrc().getPredicate().getId() == c.getSrc().getPredicate().getId() && x.getSrc().getArguments().equalsBase(c.getSrc().getArguments())) {
                return true;
            }
        }
        return false;
    }

    public void addCauses(Collection<Cause> causes) {
        if (causes != null) {
            ArgList current = arguments.convertBase();
            if (!this.causes.containsKey(current)) {
                this.causes.put(current, new TreeSet<>());
            } else {
                this.causes.get(current).clear();
            }
            for (Cause c : causes) {
                if (c.getArguments().equalsBase(c.getSrc().getArguments()) && !sourceExists(c) && getOverlaps(c.getArguments()) > 0) {
                    this.causes.get(arguments).add(c);
                }
            }
        }
    }

    public ArgList getArguments() {
        return arguments;
    }

    public boolean isAntc() {
        return antc;
    }

    public void setAntc(boolean antc) {
        this.antc = antc;
    }

    public Set<TVariable> getRelatedTVariables(boolean full) {
        Set<TVariable> set = new HashSet<>();
        for(Domain d : predicate.getRelates()) {
            set.addAll(d.getArguments().getTVariables(full));
        }
        return set;
    }

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
    private String formatParam(Argument t) {
        String s = "";
        if (t.isFSet()) {
            s += t.getF().toString();
        } else if (t.isTSet()) {
            s += t.getT().toString();
        } else if (t.isVSet()) {
            s += t.getV().toString();
        } else if (t.isRSet()) {
            s += t.getR().toString();
        } else if (!t.isEmpty()) {
            s += t.getValue().toString();
        } else {
            s += "_";
        }
        return s;
    }

    @Override
    public String toString() {
        return toString(arguments);
    }


    public String toString(ArgList arguments) {
        String s = String.format("%c", antc ? Enums.ANT : Enums.SUC);
        Operation op = Parser.getOp(predicate.getName().toString(), predicate.getRange());
        if (op == null) {
            s += predicate.getName() + "(";
            int i = 0;
            for (Argument t : arguments) {
                s += formatParam(t);
                if (i + 1 != predicate.getRange()) {
                    s += (char) Enums.COMMA;
                }
                ++i;
            }
            s += ")";
        } else if (op.getRange() == 1) {
            if (op.isPost()) {
                s += formatParam(arguments.get(0)) + op.getName();
            } else {
                s += op.getName() + formatParam(arguments.get(0));
            }
        } else {
            try {
                for (int i = 0; i < op.getRange(); ++i) {
                    s += formatParam(arguments.get(i));
                    if (i + 1 < op.getRange()) {
                        if (i == 0) {
                            s += " " + op.getName() + " ";
                        } else {
                            s += (char) Enums.COMMA;
                        }
                    }
                }
            } catch (IndexOutOfBoundsException ex) {
                System.out.print(ex);
            }
        }

        String suffix = "";
        if ((user.getMind().getDebugLevel() & 0x00FF) == Enums.DEBUG_LEVEL_DEBUG) {
            suffix += " " + id;
        }
        if ((user.getMind().getDebugLevel() & Enums.DEBUG_OPTION_STATUS) != 0) {
            suffix += /*isDest() ||*/ isQuery(arguments)  || /*isUsed() ||*/ isExcluded(arguments) || /*isProduced() ||*/ isStored(arguments) || isCalculated(arguments)
                    ? " " +
                    //(isDest() ? "A" : "") +
                    (isQuery() ? "Q" : "") +
//                    (isUsed() ? "U" : "") +
                    (isExcluded() ? "X" : "") +
                    //(isProduced() ? "P" : "") +
                    (isStored() ? "B" : "") +
                    (isCalculated() ? "S" : "") +
                    " "
                    : "";
        }
        return s + ";" + suffix;
    }


    public boolean equalsBase(Domain o) {
        if (predicate.getId() != o.getPredicate().getId()) {
            return false;
        }
        if (arguments.size() != o.getArguments().size()) {
            return false;
        }
        for (int i = 0; i < arguments.size(); ++i) {
            if (arguments.get(i).isEmpty() || o.getArguments().get(i).isEmpty()) {
                return false;
            }
            if (arguments.get(i).getValue().getId() != o.getArguments().get(i).getValue().getId()) {
                return false;
            }
        }
        return true;
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
////                        || (slave.get(i).isTSet() && arguments.get(i).isTSet()
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
////                if (get(i).isVSet() && slave.get(i).isVSet() && get(i).getV().isRelativeFor(slave.get(i).getV())) {
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

    public int getOverlaps(ArgList arg) {
        Set<Long> ids = new HashSet<>();
        for (Argument a : arguments) {
            for (Argument b : arg) {
                if (!a.isEmpty() && !b.isEmpty() && a.getValue().getId() == b.getValue().getId()) {
                    ids.add(a.getValue().getId());
                }
            }
        }
        return ids.size();
    }

    public boolean contains(TVariable t) {
        for (TVariable x : arguments.getTVariables(true)) {
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

    public boolean isDestFor(int index, Domain d) {
        if (index < arguments.size() && ((arguments.get(index).isTSet() && !arguments.get(index).getT().isEmpty()) || arguments.get(index).isVSet())) {
            TValue v = arguments.get(index).isVSet() ? arguments.get(index).getV() : arguments.get(index).getT().getCurrent();
            for (Cause s : v.getCauses()) {
                if (s.getIndex() == index
                        && s.getDst().getId() == id
                        && s.getSrc().getId() == d.getId()) {
                    return true;
                }
            }
        }
        return false;

    }

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
    public List<Function> getFunctions() {
        List<Function> list = new ArrayList<>();
        for (Argument a : arguments) {
            if (a.isFSet()) {
                list.add(a.getF());
            }
        }
        return list;
    }


    public boolean isUsed() {
        if (user.getMind().getUsedDomains().containsKey(id)) {
            for (ArgList list : user.getMind().getUsedDomains().get(id)) {
                if (arguments.equalsBase(list)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setUsed() {
        if (!user.getMind().getUsedDomains().containsKey(id)) {
            user.getMind().getUsedDomains().put(id, new HashSet<>());
        }
        if (!isUsed()) {
//            try {
            user.getMind().getUsedDomains().get(id).add(arguments.convertBase());
//            } catch (ParametersIncompleteException e) {
////                e.printStackTrace();
//            }
        }
    }

    public boolean isExcluded(ArgList args) {
        if (user.getMind().getExcludedDomains().containsKey(id)) {
            for (ArgList list : user.getMind().getExcludedDomains().get(id)) {
                if (args.equalsBase(list)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isExcluded() {
        if (user.getMind().getExcludedDomains().containsKey(id)) {
            for (ArgList list : user.getMind().getExcludedDomains().get(id)) {
                if (arguments.equalsBase(list)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setExcluded(ArgList args) {
        if (!user.getMind().getExcludedDomains().containsKey(id)) {
            user.getMind().getExcludedDomains().put(id, new HashSet<>());
        }
        if (!isExcluded(args)) {
//            try {
            user.getMind().getExcludedDomains().get(id).add(args.convertBase());
//            } catch (ParametersIncompleteException e) {
////                e.printStackTrace();
//            }
        }
    }

    public void setExcluded() {
        if (!user.getMind().getExcludedDomains().containsKey(id)) {
            user.getMind().getExcludedDomains().put(id, new HashSet<>());
        }
        if (!isExcluded()) {
//            try {
            user.getMind().getExcludedDomains().get(id).add(arguments.convertBase());
//            } catch (ParametersIncompleteException e) {
////                e.printStackTrace();
//            }
        }
    }

    public boolean isProduced(int tag) {
        if (user.getMind().getProducedDomains().containsKey(id)) {
            if (user.getMind().getProducedDomains().get(id).containsKey(tag)) {
                for (ArgList list : user.getMind().getProducedDomains().get(id).get(tag)) {
                    if (arguments.equalsBase(list)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

//    public void setProduced(int tag, ArgList args) {
//        if (!user.getMind().getProducedDomains().containsKey(this)) {
//            user.getMind().getProducedDomains().put(this, new HashSet<>());
//        }
//        if (!isProduced(args)) {
//            try {
//                user.getMind().getProducedDomains().get(this).add(convertArguments(args));
//            } catch (ParametersIncompleteException e) {
////                e.printStackTrace();
//            }
//        }
//    }
//
//    public boolean isProduced(ArgList args) {
//        if (user.getMind().getProducedDomains().containsKey(this)) {
//            for (ArgList list : user.getMind().getProducedDomains().get(this)) {
//                if (isEqualsArguments(args, list)) {
//                    return true;
//                }
//            }
//        }
//        return false;
//    }
//

    public void setProduced(int tag) {
        if (!user.getMind().getProducedDomains().containsKey(id)) {
            user.getMind().getProducedDomains().put(id, new HashMap<>());
        }
        if (!user.getMind().getProducedDomains().get(id).containsKey(tag)) {
            user.getMind().getProducedDomains().get(id).put(tag, new HashSet<>());
        }
        if (!isProduced(tag)) {
//            try {
            user.getMind().getProducedDomains().get(id).get(tag).add(arguments.convertBase());
//            } catch (ParametersIncompleteException e) {
////                e.printStackTrace();
//            }
        }
    }

    public boolean isCalculated() {
        return isCalculated(arguments);
    }

    public boolean isCalculated(ArgList arguments) {
        if (user.getMind().getCalculatedDomains().containsKey(id)) {
            for (ArgList list : user.getMind().getCalculatedDomains().get(id)) {
                if (arguments.equalsBase(list)) {
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

    public void setCalculated() {
        if (!user.getMind().getCalculatedDomains().containsKey(id)) {
            user.getMind().getCalculatedDomains().put(id, new HashSet<>());
        }
        if (!isCalculated()) {
//            try {
            user.getMind().getCalculatedDomains().get(id).add(arguments.convertBase());
//            } catch (ParametersIncompleteException e) {
////                e.printStackTrace();
//            }
        }
    }

    public boolean isStored() {
        return user.getMind().getDatabase().find(this) != null;
    }

    public boolean isStored(ArgList args) {
        return user.getMind().getDatabase().find(predicate, antc, args) != null;
    }

    public Record setStored() {
        return user.getMind().getDatabase().add(this);
    }

    public Record createStored() {
        return user.getMind().getDatabase().add(predicate, antc, isQuery(), arguments);
    }

    public boolean isSystem() {
        return Parser.getOp(predicate.getName().toString(), predicate.getRange()) != null;
    }

    public int execSystem() {
        if (isSystem()) {
            return user.getMind().executeSystem(this);
        }
        return -1;
    }

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

    public boolean isQuery() {
        return isQuery(arguments);
    }

    public boolean isQuery(ArgList arguments) {
        if (right == null) {
            return false;
        }
        if (right.isQuery()) {
            return true;
        } else {
            for (TVariable t : arguments.getTVariables(true)) {
                if (t.isQuery()) {
                    return true;
                }
            }
            for (Argument a : arguments) {
                if (a.isVSet()
                        && user.getMind().getQueryValues().containsKey(a.getV().getTVar().getId())
                        && user.getMind().getQueryValues().get(a.getV().getTVar().getId()).contains(a.getV().getId())) {
                    return true;
                }
            }
            return false;
        }
    }

    public boolean isSingleInTree() {
        for (Tree t : getParentTrees()) {
            if (t.getSequence().size() == 1) {
                return true;
            }
        }
        return false;
    }

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

    public int getVarOrder(int pos) {
        List<Integer> list = new ArrayList<>();
        SortedMap<Integer, Integer> sort = new TreeMap<>();
        int plains = 0;
        for (int i = 0; i < arguments.size(); ++i) {
            int ix = 0;
            if (arguments.get(i).isTSet()) {
                ix = arguments.get(i).getT().getIndex();
            } else if (arguments.get(i).isCVar()) {
                ix = arguments.get(i).getValue().getIndex();
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
        return plains != arguments.size() ? sort.get(list.get(pos)) + plains : 0;
    }

    @Override
    public int getHash() {
        StringBuffer buffer = new StringBuffer();
        buffer.append(antc);
        buffer.append(predicate.getId());
        buffer.append(right.getId());
        buffer.append(arguments.hashCode());
        return buffer.toString().hashCode();
    }

    @Override
    public int hashCode() {
        return ("" + id).hashCode();
    }
       

    @Override
    public boolean equals(Object d) {
        return d != null && d instanceof Domain && ((Domain) d).id == id;
    }

    public boolean isComplete() {
        boolean complete = true;
        for (Argument a : arguments) {
            if (a.isEmpty()) {
                complete = false;
                break;
            }
        }
        return complete;
    }

    public Set<Tree> getParentTrees() {
        Set<Tree> set = new HashSet<>();
        for (Tree t = user.getMind().getTrees().getRoot(); t != null; t = t.getNext()) {
            if (t.getSequence().contains(this)) {
                set.add(t);
            }
        }
        return set;
    }

    public void pushValues() {
        List<TValue> list = new ArrayList<>();
        for (TVariable t : arguments.getTVariables(true)) {
            list.add(t.getCurrent());
        }
        tStack.push(list);
    }

    public void popValues() {
        List<TValue> list = tStack.pop();
        List<TVariable> ts = arguments.getTVariables(true);
        for (int i = 0; i < ts.size(); ++i) {
            if (list.get(i) != null) {
                ts.get(i).setCurrent(list.get(i));
            }
        }
    }

    public User getUser() {
        return user;
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
}

//    public void setQuery() {
//        if (!right.isQuery()) {
//            for (TVariable t : getTVariables(true)) {
//                if (!mind.getQueryValues().containsKey(t.getId())) {
//                    mind.getQueryValues().put(t.getId(), new HashSet<>());
//                }
//                mind.getQueryValues().createCVar(t.getId()).createTVar(t.getValue().getId());
//            }
//        }
//    }

