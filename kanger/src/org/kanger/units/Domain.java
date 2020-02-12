package org.kanger.units;

import org.kanger.Mind;
import org.kanger.compiler.Operation;
import org.kanger.compiler.Parser;
import org.kanger.enums.Enums;
import org.kanger.enums.UnitType;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.ParametersIncompleteException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IUnit;
import org.kanger.primitives.ArgList;
import org.kanger.primitives.Argument;
import org.kanger.primitives.Cause;
import org.kanger.storage.ByteBuffer;

import java.io.IOException;
import java.util.*;

/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 * <p>
 * Описатель варианта решения предиката
 */
public class Domain implements IUnit<Domain> {

    private static final long serialVersionUID = 196402070001L;

    private long id = -1;                                       // id домена
    private long mindId = -1;                                   // id транзакции
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
    private transient Mind mind = null;

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

    public Domain(Mind mind) {
        this.mind = mind;

    }

    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putLong(id)
                .putLong(mindId)
                .putByte(deleted ? 1 : 0)
                .putByte(antc ? 1 : 0)
                .putInt(range)
                .putLong(predicateId)
                .putLong(rightId)
                .append(arguments.pack());
        return packet.createMarked();
    }

    public Domain apply(ByteBuffer packet) throws OutOfBufferException {
//        arguments.setUser(user);

        id = packet.getLong();
        mindId = packet.getLong();
        deleted = packet.getByte() != 0;
        antc = packet.getByte() != 0;
        range = packet.getInt();
        predicateId = packet.getLong();
        rightId = packet.getLong();
        try {
            packet.mark();
            arguments = new ArgList().apply(packet);
//            arguments.setUser(user);
        } finally {
            packet.release();
        }
        return this;
    }

    @Override
    public UnitType getUnitType() {
        return UnitType.DOMAIN;
    }

    public Predicate getPredicate() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        if (predicate == null) {
            predicate = mind.getPredicates().load(predicateId);
        }
        return predicate;
    }

    public void setPredicate(Predicate predicate) {
        this.predicateId = predicate.getId();
        this.predicate = predicate;
        this.range = predicate.getRange();
    }

    public Right getRight() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        if (right == null) {
            right = mind.getRights().load(rightId);
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


    public Set<Cause> getCauses() throws ClassNotFoundException, RuntimeErrorException, OutOfBufferException, IOException {
//        Set<Cause> set =  new HashSet<>();
//        for(TVariable t : arguments.getTVariables(true)) {
//            if(!t.isEmpty()) {
//                for(Cause c : t.getCurrent().getCauses()) {
//                    if(c.getSrc().getRight().isStored() && c.getDst().getRightId() == getRightId()) {
//                        set.add(c);
//                    }
//                }
//            }
//        }
//        return set;

        ArgList args = arguments.convertBase(mind);
        if (mind.getDomainCauses().containsKey(this) && mind.getDomainCauses().get(this).containsKey(args)) {
            return mind.getDomainCauses().get(this).get(args);
        } else {
            return null;
        }
    }

    public boolean setCauses(Collection<Cause> causes) throws Exception {
        boolean result = false;
        if (causes != null) {
            ArgList current = arguments.convertBase(mind);
            if (!mind.getDomainCauses().containsKey(this)) {
                mind.getDomainCauses().put(this, new HashMap<>());
            }
            if (!mind.getDomainCauses().get(this).containsKey(current)) {
                mind.getDomainCauses().get(this).put(current, new HashSet<>());
            } else {
                mind.getDomainCauses().get(this).get(current).clear();
            }
            for (Cause c : causes) {
                if (c.getSrc(mind).isComplete()
                        && c.getArguments().equalsBase(mind, current)
                        && c.getArguments().equalsBase(mind, c.getSrc(mind).getArguments())
                        && sourceExists(c) == null
                        && getOverlaps(c.getArguments()) > 0
                ) {
                    mind.getDomainCauses().get(this).get(current).add(c);
                    result = true;
                }
            }
        }
        return result;
    }

    public Set<TValue> getSolves() {
        return getSolves(arguments);
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

    public void setSolves(Collection<TValue> solves) {
        if (solves != null) {
            ArgList current = arguments.convertBase(mind);
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

    public Set<TValue> getSolves(ArgList arguments) {
        ArgList args = arguments.convertBase(mind);
        if (mind.getDomainSolves().containsKey(this) && mind.getDomainSolves().get(this).containsKey(args)) {
            return mind.getDomainSolves().get(this).get(args);
        } else {
            return null;
        }
    }

    private Cause sourceExists(Cause c) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        Set<Cause> causes = getCauses();
        if (causes != null) {
            for (Cause x : causes) {
                if (x.getSrc(mind).getPredicateId() == c.getSrc(mind).getPredicateId()
                        && x.getSrc(mind).getArguments().equalsBase(mind, c.getSrc(mind).getArguments())) {
                    return x;
                }
//                if(x.getSrc().sourceExists(c)) {
//                    return true;
//                }
            }
        }
        return null;
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
//        t.setUser(user);
//        if(!t.isEmpty(mind)) {
//            t.getValue(mind).setMind(mind);
//        }

        if (t.isFSet()) {
            s += t.getF(mind).toString();
        } else if (t.isTSet()) {
            s += t.getT(mind).toString();
        } else if (t.isVSet()) {
            s += t.getV(mind).toString();
        } else if (t.isRSet()) {
            s += t.getR(mind).toString();
        } else if (!t.isEmpty(mind)) {
            s += t.getValue(mind).toString();
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
            for (Term t : arguments.getCVariables(mind, true)) {
                //TODO: Костыль
//                t.setMind(mind);
                if (!cVars.contains(t)) {
                    cVars.add(t);
                }
            }
            for (Term t : cVars) {
                s += "$" + t.getName() + " ";
            }

            Operation op = Parser.getOp(getPredicate().getName().toString(), getRange());

            if (op == null) {
                op = Parser.getOp(getPredicate().getName().toString(), 0);
            }

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
            if ((mind.getDebugLevel() & 0x00FF) == Enums.DEBUG_LEVEL_DEBUG) {
                suffix += " " + id + " " + mindId + " " + mind.getId();
            }
            if ((mind.getDebugLevel() & Enums.DEBUG_OPTION_STATUS) != 0) {
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


    public boolean equalsBase(Domain o) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        if (predicateId != o.getPredicateId()) {
            return false;
        }
        if (arguments.size() != o.getArguments().size()) {
            return false;
        }
        for (int i = 0; i < arguments.size(); ++i) {
            if (arguments.get(i).isEmpty(mind) || o.getArguments().get(i).isEmpty(mind)) {
                return false;
            } else if (id != -1 && o.getId() != -1
                    && arguments.get(i).getValue(mind).getId() != o.getArguments().get(i).getValue(mind).getId()) {
                return false;
            } else if (!arguments.get(i).getValue(mind).equalsTo(o.getArguments().get(i).getValue(mind))) {
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
                if (!a.isEmpty(mind) && !b.isEmpty(mind) && a.getValue(mind).getId() == b.getValue(mind).getId()) {
                    ids.add(a.getValue(mind).getId());
                }
            }
        }
        return ids.size();
    }

    public boolean contains(TVariable t) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        for (TVariable x : arguments.getTVariables(mind, true)) {
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
        if (mind.getUsedDomains().containsKey(this)) {
            for (ArgList list : mind.getUsedDomains().get(this)) {
                if (arguments.equalsBase(mind, list)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setUsed() {
//        arguments.setUser(user);
        if (!mind.getUsedDomains().containsKey(this)) {
            mind.getUsedDomains().put(this, new HashSet<>());
        }
        if (!isUsed()) {
            mind.getUsedDomains().get(this).add(arguments.convertBase(mind));
        }
    }

    public boolean isExcluded(ArgList args) {
        if (mind.getExcludedDomains().containsKey(this)) {
            for (ArgList list : mind.getExcludedDomains().get(this)) {
                if (args.equalsBase(mind, list)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isExcluded() {
        return isExcluded(arguments);
//        if (mind.getExcludedDomains().containsKey(this)) {
//            for (ArgList list : mind.getExcludedDomains().get(this)) {
//                if (arguments.equalsBase(list)) {
//                    return true;
//                }
//            }
//        }
//        return false;
    }

    public void setExcluded(ArgList args) {
        if (!mind.getExcludedDomains().containsKey(this)) {
            mind.getExcludedDomains().put(this, new HashSet<>());
        }
        if (!isExcluded(args)) {
            mind.getExcludedDomains().get(this).add(args.convertBase(mind));
        }
    }

    public void setExcluded() {
        setExcluded(arguments);
//        if (!mind.getExcludedDomains().containsKey(id)) {
//            mind.getExcludedDomains().put(id, new HashSet<>());
//        }
//        if (!isExcluded()) {
////            try {
//            mind.getExcludedDomains().get(id).add(arguments.convertBase());
////            } catch (ParametersIncompleteException e) {
//////                e.printStackTrace();
////            }
//        }
    }

    public boolean isProduced() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        if (mind.getProducedDomains().containsKey(this)) {
            for (List<Term> list : mind.getProducedDomains().get(this)) {
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

    public void setTag(long tag) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        for (TVariable t : arguments.getTVariables(mind, true)) {
            t.getCurrent().setTag(tag);
        }
    }

//    public Set<Long> getTag() {
//        ArgList args = arguments;
//        if (mind.getDomainTags().containsKey(this)
//                && mind.getDomainTags().get(this).containsKey(args)) {
//            return mind.getDomainTags().get(this).get(args);
//        } else {
//            return new HashSet<>();
//        }
//    }

    public void setProduced() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        if (!mind.getProducedDomains().containsKey(this)) {
            mind.getProducedDomains().put(this, new ArrayList<>());
        }
        if (!isProduced()) {
            try {
                mind.getProducedDomains().get(this).add(arguments.getStamp(mind));
            } catch (ParametersIncompleteException | OutOfBufferException e) {
            }
        }
    }

    public boolean isCalculated() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        return isCalculated(arguments);
    }

    public boolean isCalculated(ArgList arguments) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        if (mind.getCalculatedDomains().containsKey(this)) {
            for (List<Term> list : mind.getCalculatedDomains().get(this)) {
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

    public void unCalculated() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        if (isCalculated()) {
            for (List<Term> list : mind.getCalculatedDomains().get(this)) {
                if (arguments.equalsStamp(mind, list)) {
                    mind.getCalculatedDomains().remove(list);
                    break;
                }
            }
        }
    }

    public void setCalculated() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        if (!mind.getCalculatedDomains().containsKey(this)) {
            mind.getCalculatedDomains().put(this, new ArrayList<>());
        }
        if (!isCalculated()) {
            try {
                mind.getCalculatedDomains().get(this).add(arguments.getStamp(mind));
            } catch (ParametersIncompleteException | OutOfBufferException e) {
            }
        }
    }

    public boolean isStored() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        return mind.getRights().find(this) != null;
    }

    public boolean isStored(ArgList args) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        Domain d = new Domain(getPredicate(), antc, args);
        return mind.getRights().find(d) != null;
    }

    public Right setStored() throws Exception {
        Right r = mind.getRights().store(this);
        return r;
    }

    public Right createStored() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        Right r = mind.getRights().add(this);
        return r;
    }

    public boolean isSystem() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        return Parser.getOp(getPredicate().getName().toString(), getRange()) != null;
    }

    public int execSystem() throws Exception {
        if (isSystem()) {
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

    public boolean isQuery() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        return isQuery(arguments);
    }

    public boolean isQuery(ArgList arguments) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        if (rightId == -1) {
            return false;
        }
        if (getRight().isQuery()) {
            return true;
        } else {
            for (TVariable t : arguments.getTVariables(mind, true)) {
                if (t.isQuery()) {
                    return true;
                }
            }
            for (Argument a : arguments) {
                if (a.isVSet()
                        && mind.getQueryValues().containsKey(a.getV(mind).getTVar())
                        && mind.getQueryValues().get(a.getV(mind).getTVar()).contains(a.getV(mind))) {
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

    public int getVarOrder(int pos) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        List<Integer> list = new ArrayList<>();
        SortedMap<Integer, Integer> sort = new TreeMap<>();
        int plains = 0;
        for (int i = 0; i < arguments.size(); ++i) {
            int ix = 0;
            if (arguments.get(i).isTSet()) {
                ix = arguments.get(i).getT(mind).getIndex();
            } else if (arguments.get(i).isCVar()) {
                ix = arguments.get(i).getValue(mind).getIndex();
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
        //TODO: ---
        hash = 47 * hash + arguments.getHash(mind);
//        hash = 47 * hash + arguments.hashCode(); //.getHash(mind);
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
                        if ((to.get(i).isTSet() && arguments.get(i).isTSet() && to.get(i).getT(mind).getId() == arguments.get(i).getT(mind).getId())
                                || (to.get(i).isFSet() && arguments.get(i).isFSet() && to.get(i).getF(mind).getId() == arguments.get(i).getF(mind).getId())
                                || (!to.get(i).isTSet() && !arguments.get(i).isTSet()
                                && !to.get(i).isFSet() && !arguments.get(i).isFSet()
                                && !to.get(i).isEmpty(mind) && !arguments.get(i).isEmpty(mind)
                                && to.get(i).getValue(mind).getId() == arguments.get(i).getValue(mind).getId())) {
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
        for (Argument a : arguments) {
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
    public Domain setMind(Mind mind) throws ClassNotFoundException, RuntimeErrorException, OutOfBufferException, IOException {
        this.mind = mind;
        for (TVariable t : arguments.getTVariables(mind, true)) {
            t.setMind(mind);
        }
//        arguments.setUser(user);
        return this;
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

    public int getHashStruct() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
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
                    long id = arguments.get(i).getValue(mind).getId();
                    hash = 47 * hash + (i + 1) * (int) (id ^ (id >>> 32));
                    break;
                case FUNCTION:
                    hash = 47 * hash + (i + 1) * arguments.get(i).getF(mind).getHashStruct(getRight());
                    break;
            }
        }
        return hash;
    }

    public boolean equalsToStruct(Domain to) throws ClassNotFoundException, RuntimeErrorException, OutOfBufferException, IOException {
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
                        case TVALUE:
                        case TERM:
                            if (arguments.get(i).getValue(mind).getId() != to.getArguments().get(i).getValue(mind).getId()) {
                                return false;
                            }
                            break;
                        case FUNCTION:
                            if (!arguments.get(i).getF(mind).equalsToStruct(to.getArguments().get(i).getF(mind), getRight(), to.getRight())) {
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
    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public void setDeleted() {
        this.deleted = true;
    }

    @Override
    public long getMindId() {
        return mindId;
    }

    @Override
    public void setMindId(long mindId) {
        this.mindId = mindId;
    }

    public Domain commit(Mind m) throws Exception {
        setPredicate(predicate.commit(m));
        for (Argument a : arguments) {
            a.setO((IUnit) a.getO(mind).commit(m));
        }
        setMind(m);
        return this;
    }
}

