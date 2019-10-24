package kanger.units;

import kanger.User;
import kanger.compiler.Operation;
import kanger.compiler.Parser;
import kanger.enums.Enums;
import kanger.exception.ParametersIncompleteException;
import kanger.interfaces.IUnit;
import kanger.primitives.ArgList;
import kanger.primitives.Argument;
import kanger.primitives.Cause;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.*;

/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 * <p>
 * Описатель варианта решения предиката
 */
public class Domain implements Externalizable, IUnit<Domain> {

    private static final long serialVersionUID = 196402070001L;

    private long id = -1;                                       // id домена
    private boolean antc = true;                                // ! или ?
    private Predicate predicate = null;                         // Ссылка на описатель предиката
    private ArgList arguments = new ArgList();       // Массив подстановочных переменных
    private Right right = null;                                        // Ссылка на правило
    private int range = 0;
//    private Domain next = null;                                 // Следующий элемент

//    private Stack<List<TValue>> tStack = new Stack<>();
//    private Map<ArgList, SortedSet<Cause>> causes = new HashMap<>();

    private transient long predicateId = -1;
    private transient long rightId = -1;
    private transient User user = null;

    private transient boolean deleted = false;

    public Domain() {
    }

    public Domain(Predicate pred, boolean antc, ArgList args, Right r) {
        this(pred, antc, args);
        setRight(r);
    }

    public Domain(Predicate pred, boolean antc, ArgList args) {
        setPredicate(pred);
        setAntc(antc);
        getArguments().addAll(args);
    }

    public Domain(User user) {
        this.user = user;
    }

    @Override
    public void readExternal(ObjectInput dis) throws IOException, ClassNotFoundException {
        id = dis.readLong();
        deleted = dis.readBoolean();
        antc = dis.readBoolean();
        range = dis.readInt();
        predicateId = dis.readLong();
        rightId = dis.readLong();
        arguments = (ArgList) dis.readObject();
        arguments.setUser(user);
    }

    @Override
    public void writeExternal(ObjectOutput dos) throws IOException {
        dos.writeLong(id);
        dos.writeBoolean(deleted);
        dos.writeBoolean(antc);
        dos.writeInt(range);
        dos.writeLong(predicateId);
        dos.writeLong(rightId);
        dos.writeObject(arguments);
    }

//    @Override
//    public void linkExternal(User user) throws IOException, ClassNotFoundException {
//        this.user = user;
//        predicate = user.getMind().getPredicates().load(predicateId);
//        right = user.getMind().getRights().load(rightId);
////        arguments.linkExternal(user);
//    }

    public Predicate getPredicate() throws IOException, ClassNotFoundException {
        if (predicate == null) {
            predicate = user.getMind().getPredicates().load(predicateId);
        }
        return predicate;
    }

    public void setPredicate(Predicate predicate) {
        this.predicateId = predicate.getId();
        this.predicate = predicate;
        this.range = predicate.getRange();
    }

    public Right getRight() throws IOException, ClassNotFoundException {
        if (right == null) {
            right = user.getMind().getRights().load(rightId);
        }
        return right;
    }

    public void setRight(Right r) {
        this.rightId = r.getId();
        right = r;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
        this.id = id;
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


    public Set<Cause> getCauses() {
        ArgList args = arguments.convertBase();
        if (user.getMind().getDomainCauses().containsKey(this) && user.getMind().getDomainCauses().get(this).containsKey(args)) {
            return user.getMind().getDomainCauses().get(this).get(args);
        } else {
            return null;
        }
    }

    public Set<TValue> getSolves() {
        return getSolves(arguments);
    }

    public Set<TValue> getSolves(ArgList arguments) {
        ArgList args = arguments.convertBase();
        if (user.getMind().getDomainSolves().containsKey(this) && user.getMind().getDomainSolves().get(this).containsKey(args)) {
            return user.getMind().getDomainSolves().get(this).get(args);
        } else {
            return null;
        }
    }

//    public void dropSolves(ArgList arguments) {
//        ArgList args = arguments.convertBase();
//        if (user.getMind().getDomainSolves().containsKey(this) && user.getMind().getDomainSolves().get(this).containsKey(args)) {
//            user.getMind().getDomainSolves().get(this).remove(args);
//        }
//    }

//    public SortedSet<Cause> getCauses(ArgList arguments) {
//        return causes.get(arguments);
//    }

    private boolean sourceExists(Cause c) throws IOException, ClassNotFoundException {
        Set<Cause> causes = getCauses();
        if (causes != null) {
            for (Cause x : getCauses()) {
                if (x.getSrc().getPredicateId() == c.getSrc().getPredicateId() && x.getSrc().getArguments().equalsBase(c.getSrc().getArguments())) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setCauses(Collection<Cause> causes) throws Exception {
        if (causes != null) {
            ArgList current = arguments.convertBase();
            if (!user.getMind().getDomainCauses().containsKey(this)) {
                user.getMind().getDomainCauses().put(this, new HashMap<>());
            }
            if (!user.getMind().getDomainCauses().get(this).containsKey(current)) {
                user.getMind().getDomainCauses().get(this).put(current, new HashSet<>());
            } else {
                user.getMind().getDomainCauses().get(this).get(current).clear();
            }
            for (Cause c : causes) {
                if (c.getArguments().equalsBase(c.getSrc().getArguments()) && !sourceExists(c) && getOverlaps(c.getArguments()) > 0) {
                    user.getMind().getDomainCauses().get(this).get(current).add(c);
                }
            }
        }
    }

    public void setSolves(Collection<TValue> solves) {
        if (solves != null) {
            ArgList current = arguments.convertBase();
            if (!user.getMind().getDomainSolves().containsKey(this)) {
                user.getMind().getDomainSolves().put(this, new HashMap<>());
            }
            if (!user.getMind().getDomainSolves().get(this).containsKey(current)) {
                user.getMind().getDomainSolves().get(this).put(current, new TreeSet<>());
            } else {
                user.getMind().getDomainSolves().get(this).get(current).clear();
            }
            user.getMind().getDomainSolves().get(this).get(current).addAll(solves);
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
        String s = "";
        //TODO: Костыль
        t.setUser(user);
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


    //TODO ?$x parent(x,Jack); выводит рещшение Result: TRUE...
    //Solves (1):
    //	Solution 001: !parent(y,Jack); GB
    //Values (1):
    //	Row 001: x=%2

    public String toString(ArgList arguments) {
        try {
            String s = String.format("%c", antc ? Enums.ANT : Enums.SUC);

            List<Term> cVars = new ArrayList<>();
            for (Term t : arguments.getCVariables(true)) {
                if (!cVars.contains(t)) {
                    cVars.add(t);
                }
            }
            for (Term t : cVars) {
                s += "$" + t.getName() + " ";
            }

            Operation op = Parser.getOp(getPredicate().getName().toString(), getRange());
            if (op == null) {
                s += getPredicate().getName() + "(";
                int i = 0;
                for (Argument t : arguments) {
                    s += formatParam(t);
                    if (i + 1 != getRange()) {
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
            }

            String suffix = "";
            if ((user.getMind().getDebugLevel() & 0x00FF) == Enums.DEBUG_LEVEL_DEBUG) {
                suffix += " " + id;
            }
            if ((user.getMind().getDebugLevel() & Enums.DEBUG_OPTION_STATUS) != 0) {
                try {
                    suffix += /*isDest() ||*/ isQuery(arguments) || isUsed() || isExcluded(arguments) || /*isProduced() ||*/ isStored(arguments) || isCalculated(arguments)
                            ? " " +
                            //(isDest() ? "A" : "") +
                            (isQuery() ? "Q" : "") +
                            (isUsed() ? "U" : "") +
                            (isExcluded() ? "X" : "") +
                            //(isProduced() ? "P" : "") +
                            (isStored() ? "B" : "") +
                            (isCalculated() ? "S" : "") +
                            " "
                            : "";
                } catch (Exception e) {
                    e.printStackTrace(System.err);
                }
            }
            return s + ";" + suffix;
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return "";
        }
    }


    public boolean equalsBase(Domain o) throws IOException, ClassNotFoundException {
        if (predicateId != o.getPredicateId()) {
            return false;
        }
        if (arguments.size() != o.getArguments().size()) {
            return false;
        }
        for (int i = 0; i < arguments.size(); ++i) {
            if (arguments.get(i).isEmpty() || o.getArguments().get(i).isEmpty()) {
                return false;
            } else if (id != -1 && o.getId() != -1
                    && arguments.get(i).getValue().getId() != o.getArguments().get(i).getValue().getId()) {
                return false;
            } else if (!arguments.get(i).getValue().equalsTo(o.getArguments().get(i).getValue())) {
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

    public int getOverlaps(ArgList arg) throws Exception {
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

    public boolean contains(TVariable t) throws IOException, ClassNotFoundException {
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

//    public boolean isDestFor(int index, Domain d) {
//        if (index < arguments.size() && ((arguments.get(index).isTSet() && !arguments.get(index).getT().isEmpty()) || arguments.get(index).isVSet())) {
//            TValue v = arguments.get(index).isVSet() ? arguments.get(index).getV() : arguments.get(index).getT().getCurrent();
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

    public boolean isUsed() {
        if (user.getMind().getUsedDomains().containsKey(this)) {
            for (ArgList list : user.getMind().getUsedDomains().get(this)) {
                if (arguments.equalsBase(list)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setUsed() {
        if (!user.getMind().getUsedDomains().containsKey(this)) {
            user.getMind().getUsedDomains().put(this, new HashSet<>());
        }
        if (!isUsed()) {
            user.getMind().getUsedDomains().get(this).add(arguments.convertBase());
        }
    }

    public boolean isExcluded(ArgList args) {
        if (user.getMind().getExcludedDomains().containsKey(this)) {
            for (ArgList list : user.getMind().getExcludedDomains().get(this)) {
                if (args.equalsBase(list)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isExcluded() {
        return isExcluded(arguments);
//        if (user.getMind().getExcludedDomains().containsKey(this)) {
//            for (ArgList list : user.getMind().getExcludedDomains().get(this)) {
//                if (arguments.equalsBase(list)) {
//                    return true;
//                }
//            }
//        }
//        return false;
    }

    public void setExcluded(ArgList args) {
        if (!user.getMind().getExcludedDomains().containsKey(this)) {
            user.getMind().getExcludedDomains().put(this, new HashSet<>());
        }
        if (!isExcluded(args)) {
            user.getMind().getExcludedDomains().get(this).add(args.convertBase());
        }
    }

    public void setExcluded() {
        setExcluded(arguments);
//        if (!user.getMind().getExcludedDomains().containsKey(id)) {
//            user.getMind().getExcludedDomains().put(id, new HashSet<>());
//        }
//        if (!isExcluded()) {
////            try {
//            user.getMind().getExcludedDomains().get(id).add(arguments.convertBase());
////            } catch (ParametersIncompleteException e) {
//////                e.printStackTrace();
////            }
//        }
    }

    public boolean isProduced() throws IOException, ClassNotFoundException {
        if (user.getMind().getProducedDomains().containsKey(this)) {
            for (List<Term> list : user.getMind().getProducedDomains().get(this)) {
                if (arguments.equalsStamp(list)) {
                    return true;
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

    public void setTag(long tag) throws IOException, ClassNotFoundException {
        for (TVariable t : arguments.getTVariables(true)) {
            t.getCurrent().setTag(tag);
        }
    }

//    public Set<Long> getTag() {
//        ArgList args = arguments;
//        if (user.getMind().getDomainTags().containsKey(this)
//                && user.getMind().getDomainTags().get(this).containsKey(args)) {
//            return user.getMind().getDomainTags().get(this).get(args);
//        } else {
//            return new HashSet<>();
//        }
//    }

    public void setProduced() throws IOException, ClassNotFoundException {
        if (!user.getMind().getProducedDomains().containsKey(this)) {
            user.getMind().getProducedDomains().put(this, new ArrayList<>());
        }
        if (!isProduced()) {
            try {
                user.getMind().getProducedDomains().get(this).add(arguments.getStamp());
            } catch (ParametersIncompleteException e) {
            }
        }
    }

    public boolean isCalculated() throws IOException, ClassNotFoundException {
        return isCalculated(arguments);
    }

    public boolean isCalculated(ArgList arguments) throws IOException, ClassNotFoundException {
        if (user.getMind().getCalculatedDomains().containsKey(this)) {
            for (List<Term> list : user.getMind().getCalculatedDomains().get(this)) {
                if (arguments.equalsStamp(list)) {
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

    public void unCalculated() throws IOException, ClassNotFoundException {
        if (isCalculated()) {
            for (List<Term> list : user.getMind().getCalculatedDomains().get(this)) {
                if (arguments.equalsStamp(list)) {
                    user.getMind().getCalculatedDomains().remove(list);
                    break;
                }
            }
        }
    }

    public void setCalculated() throws IOException, ClassNotFoundException {
        if (!user.getMind().getCalculatedDomains().containsKey(this)) {
            user.getMind().getCalculatedDomains().put(this, new ArrayList<>());
        }
        if (!isCalculated()) {
            try {
                user.getMind().getCalculatedDomains().get(this).add(arguments.getStamp());
            } catch (ParametersIncompleteException e) {
            }
        }
    }

    public boolean isStored() throws IOException, ClassNotFoundException {
        return user.getMind().getRights().find(this) != null;
    }

    public boolean isStored(ArgList args) throws IOException, ClassNotFoundException {
        Domain d = new Domain(getPredicate(), antc, args);
        return user.getMind().getRights().find(d) != null;
    }

    public Right setStored() throws Exception {
        Right r = user.getMind().getRights().store(this);
        return r;
    }

    public Right createStored() throws IOException, ClassNotFoundException {
        Right r = user.getMind().getRights().add(this);
        return r;
    }

    public boolean isSystem() throws IOException, ClassNotFoundException {
        return Parser.getOp(getPredicate().getName().toString(), getRange()) != null;
    }

    public int execSystem() throws Exception {
        if (isSystem()) {
            return user.getMind().executeSystem(this);
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
//                if (new Calculator(user).calculate(f, user.getMind().isLogging())) {
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

    public boolean isQuery() throws IOException, ClassNotFoundException {
        return isQuery(arguments);
    }

    public boolean isQuery(ArgList arguments) throws IOException, ClassNotFoundException {
        if (rightId == -1) {
            return false;
        }
        if (getRight().isQuery()) {
            return true;
        } else {
            for (TVariable t : arguments.getTVariables(true)) {
                if (t.isQuery()) {
                    return true;
                }
            }
            for (Argument a : arguments) {
                if (a.isVSet()
                        && user.getMind().getQueryValues().containsKey(a.getV().getTVar())
                        && user.getMind().getQueryValues().get(a.getV().getTVar()).contains(a.getV())) {
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

    public int getVarOrder(int pos) throws IOException, ClassNotFoundException {
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
        return 47 * getHashBase() + (int) (rightId ^ (rightId >>> 32));
    }

    public int getHashBase() {
        int hash = 3;
        hash = 47 * hash + (antc ? 1 : 0);
        hash = 47 * hash + (int) (predicateId ^ (predicateId >>> 32));
        hash = 47 * hash + arguments.hashCode();
        return hash;
    }

    @Override
    public boolean equalsTo(Domain to) {
        try {
            if (to.isAntc() == antc
                    && to.getPredicateId() == predicateId
                    && (rightId == -1 || to.getRightId() == rightId)) {
                int i = 0;
                for (; i < getRange(); ++i) {
                    try {
                        if ((to.get(i).isTSet() && arguments.get(i).isTSet() && to.get(i).getT().getId() == arguments.get(i).getT().getId())
                                || (to.get(i).isFSet() && arguments.get(i).isFSet() && to.get(i).getF().getId() == arguments.get(i).getF().getId())
                                || (!to.get(i).isTSet() && !arguments.get(i).isTSet()
                                && !to.get(i).isFSet() && !arguments.get(i).isFSet()
                                && !to.get(i).isEmpty() && !arguments.get(i).isEmpty()
                                && to.get(i).getValue().getId() == arguments.get(i).getValue().getId())) {
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

    //    public Set<Tree> getParentTrees() {
//        Set<Tree> set = new HashSet<>();
//        for (Tree t : user.getMind().getTrees()) {
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

    public User getUser() {
        return user;
    }

    @Override
    public void setUser(User user) {
        this.user = user;
        arguments.setUser(user);
    }

    public long getPredicateId() {
        return predicateId;
    }

    public long getRightId() {
        return rightId;
    }

    public int getRange() {
        return range;
    }

    public void setRange(int range) {
        this.range = range;
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

    public int getHashStruct() throws IOException, ClassNotFoundException {
        int hash = 3;
        hash = 47 * hash + (antc ? 1 : 0);
        hash = 47 * hash + (int) (predicateId ^ (predicateId >>> 32));
        hash = 47 * hash + range;
        for (int i = 0; i < range; ++i) {
            hash = 47 * hash + (i + 1) * arguments.get(i).getType().ordinal();
            switch (arguments.get(i).getType()) {
                case CVARIABLE:
                case TVARIABLE:
                    hash = 47 * hash + (i + 1) * getVarOrder(i);
                    break;
                case TERM:
                    long id = arguments.get(i).getValue().getId();
                    hash = 47 * hash + (i + 1) * (int) (id ^ (id >>> 32));
                    break;
                case FUNCTION:
                    hash = 47 * hash + (i + 1) * arguments.get(i).getF().getHashStruct(getRight());
                    break;
            }
        }
        return hash;
    }

    public boolean equalsToStruct(Domain to) {
        try {
            if (to.isAntc() == antc
                    && to.getRange() == range
                    && to.getPredicateId() == predicateId) {
                int i = 0;
                for (; i < range; ++i) {
                    if (arguments.get(i).getType() == to.getArguments().get(i).getType()) {
                        switch (arguments.get(i).getType()) {
                            case TVARIABLE:
                            case CVARIABLE:
                                if (getVarOrder(i) != to.getVarOrder(i)) {
                                    return false;
                                }
                                break;
                            case TERM:
                                if (arguments.get(i).getValue().getId() != to.getArguments().get(i).getValue().getId()) {
                                    return false;
                                }
                                break;
                            case FUNCTION:
                                if (!arguments.get(i).getF().equalsToStruct(to.getArguments().get(i).getF(), getRight(), to.getRight())) {
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
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace(System.err);
            return false;
        }
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted() {
        this.deleted = true;
    }
}

